package my.threehumpedcamel;

import java.io.EOFException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import my.threehumpedcamel.model.Event;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.dataformat.avro.AvroDataFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MainRouter extends RouteBuilder {

    private final String ERROR_LOGGER_NAME = this.getClass().getPackage().getName() + ".error";

    @Value("${app.event.throttling.maxCount:1}")
    private Long eventThrottlingMaxCount;
    @Value("${app.event.throttling.periodMillis:1000}")
    private Long eventThrottlingPeriodMillis;

    @Override
    public void configure() {
        // @formatter:off
        errorHandler(deadLetterChannel("direct:error"));

        from("direct:error")
            .to(String.format("log:%s?level=DEBUG", ERROR_LOGGER_NAME))
            .to("micrometer:counter:recordsFailed");

        from("kafka:{{app.event.topic.name}}").routeId("event-kafka-inbound")
                .to("micrometer:counter:recordsIn")
            .unmarshal(new AvroDataFormat(Event.SCHEMA$))
            .filter(MainRouter::matches)
                .to("micrometer:counter:recordsFiltered")
            .process(MainRouter::handle)
            .throttle(eventThrottlingMaxCount).timePeriodMillis(eventThrottlingPeriodMillis)
            .process(exchange -> exchange.setProperty(Exchange.CHARSET_NAME, StandardCharsets.UTF_8))
            .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
            .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
            .to("{{app.external.service.url}}").id("event-http-outbound")
                .to("micrometer:counter:recordsOut")
        ;
        // @formatter:on
    }

    private static boolean matches(Exchange exchange) {
        final Event event = exchange.getMessage().getBody(Event.class);
        return "test".equals(event.getType());
    }

    private static void handle(Exchange exchange) {
        try {
            final Event event = exchange.getMessage().getBody(Event.class);
            if (event.getProperties().containsKey("failure")) {
                throw new EOFException("failure event!");
            }

            final ObjectMapper mapper = new ObjectMapper();
            final String json = mapper.writeValueAsString(event.getProperties());
            exchange.getIn().setBody(json);
        } catch (Exception e) {
            exchange.setException(e);
        }
    }
}
