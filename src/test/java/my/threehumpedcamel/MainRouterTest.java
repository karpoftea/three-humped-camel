package my.threehumpedcamel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import my.threehumpedcamel.model.Event;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class MainRouterTest {

    @Autowired
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint outboundEndpointMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CamelContext camelContext;

    @BeforeEach
    public void setUp() throws Exception {
        AdviceWith.adviceWith(camelContext, "event-kafka-inbound", in -> {
            in.replaceFromWith("direct:start");
            in.weaveById("event-http-outbound").replace().to(outboundEndpointMock);
        });
    }

    @Test
    public void when_everythingGoesFine_then_eventSentViaHttp() throws Exception {
        camelContext.start();

        final Event event = createEvent();
        outboundEndpointMock.expectedMessageCount(1);
        outboundEndpointMock.expectedBodiesReceived("{\"value\":\"test-value\"}");

        template.sendBody("direct:start", Utils.toBytes(event));

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsIn").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsFiltered").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsFailed").counter(), nullValue());
        assertThat(meterRegistry.find("recordsOut").counter().count(), equalTo(1.0D));
    }

    @Test
    public void when_unmarshallFailed_then_eventSkipped() throws Exception {
        camelContext.start();

        outboundEndpointMock.expectedMessageCount(0);

        template.sendBody("direct:start", new byte[] {42});

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsIn").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsFiltered").counter(), nullValue());
        assertThat(meterRegistry.find("recordsFailed").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsOut").counter(), nullValue());
    }

    @Test
    public void when_processingFailed_then_eventSkipped() throws Exception {
        camelContext.start();

        final Event event = createWrongTypeOfCje();
        outboundEndpointMock.expectedMessageCount(0);

        template.sendBody("direct:start", Utils.toBytes(event));

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsIn").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsFiltered").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsFailed").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsOut").counter(), nullValue());
    }

    @Test
    public void when_sentFailed_then_eventSkipped() throws Exception {
        camelContext.start();

        final Event event = createEvent();
        outboundEndpointMock.expectedMessageCount(1);
        outboundEndpointMock.whenAnyExchangeReceived(exchange -> {
            throw new HttpOperationFailedException("uri", 401, "location", "n/a", Collections.emptyMap(), "none");
        });

        template.sendBody("direct:start", Utils.toBytes(event));

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsIn").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsFiltered").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsFailed").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsOut").counter(), nullValue());
    }

    private Event createEvent() {
        final Map<String, String> properties = new HashMap<>();
        properties.put("value", "test-value");
        return Event.newBuilder()
            .setId(42L)
            .setType("test")
            .setProperties(properties)
            .build();
    }

    private Event createWrongTypeOfCje() {
        Map<String, String> properties = new HashMap<>();
        properties.put("failure", "true");
        return Event.newBuilder(createEvent())
            .setProperties(properties)
            .build();
    }

    private ByteArrayOutputStream toBytes(byte[] array) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(array.length);
        outputStream.write(array);
        return outputStream;
    }
}
