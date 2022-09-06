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

    @Value("${app.event.throttling.maxCount:1}")
    private Long eventThrottlingMaxCount;
    @Value("${app.event.throttling.periodMillis:1000}")
    private Long eventThrottlingPeriodMillis;

    @Override
    public void configure() {
        errorHandler(deadLetterChannel("micrometer:counter:unexpectedFailures?increment=1"));

        from("kafka:{{app.event.topic.name}}").routeId("event-kafka-inbound")
                .errorHandler(deadLetterChannel("micrometer:counter:recordsUnmarshallFailure?increment=1"))
                .to("micrometer:counter:recordsIn?increment=1")
            .unmarshal(new AvroDataFormat(Event.SCHEMA$))
                .to("micrometer:counter:recordsUnmarshalled?increment=1")
            .to("direct:processing-stage").id("processing-stage");

        from("direct:processing-stage").routeId("processing-stage-route")
                .errorHandler(deadLetterChannel("micrometer:counter:recordsProcessingFailure?increment=1"))
            .filter(MainRouter::matches)
                .to("micrometer:counter:recordsFiltered?increment=1")
            .process(MainRouter::handle)
                .to("micrometer:counter:recordsProcessed?increment=1")
            .to("direct:delivery-stage").id("delivery-stage");

        from("direct:delivery-stage").routeId("delivery-stage-route")
            .throttle(eventThrottlingMaxCount).timePeriodMillis(eventThrottlingPeriodMillis)
            .process(exchange -> exchange.setProperty(Exchange.CHARSET_NAME, StandardCharsets.UTF_8))
            .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
            .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
            .to("{{app.external.service.url}}").id("event-http-outbound")
                .to("micrometer:counter:recordsOut?increment=1")
        ;
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
