package my.threehumpedcamel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import my.threehumpedcamel.model.Event;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
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
        });
    }

    @Test
    public void when_validCjeEventReceived_then_messagePropagatedToProcessingStage() throws Exception {
        AdviceWith.adviceWith(camelContext, "event-kafka-inbound", in -> {
            in.weaveById("processing-stage").replace().to(outboundEndpointMock);
        });
        camelContext.start();

        final Event event = createEvent();
        outboundEndpointMock.expectedMessageCount(1);
        outboundEndpointMock.message(0).body().isEqualTo(event);

        template.sendBody("direct:start", Utils.toBytes(event));

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsIn").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsUnmarshalled").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsUnmarshallFailure").counter(), nullValue());
    }

    @Test
    public void when_malformedCjeEventReceived_then_eventSkipped() throws Exception {
        AdviceWith.adviceWith(camelContext, "event-kafka-inbound", in -> {
            in.weaveById("processing-stage").replace().to(outboundEndpointMock);
        });
        camelContext.start();

        outboundEndpointMock.expectedMessageCount(0);

        template.sendBody("direct:start", toBytes(new byte[] {0, 1, 2}));

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsIn").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsUnmarshalled").counter(), nullValue());
        assertThat(meterRegistry.find("recordsUnmarshallFailure").counter().count(), equalTo(1.0D));
    }

    @Test
    public void when_eventProcessingSucceeds_then_messagePropagatedToDeliveryStage() throws Exception {
        AdviceWith.adviceWith(camelContext, "processing-stage-route", in ->
            in.weaveById("delivery-stage").replace().to(outboundEndpointMock));
        camelContext.start();

        outboundEndpointMock.expectedMessageCount(1);

        template.sendBody("direct:processing-stage", createEvent());

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsFiltered").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsProcessed").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsProcessingFailure").counter(), nullValue());
    }

    @Test
    public void when_eventProcessorFails_then_eventSkipped() throws Exception {
        AdviceWith.adviceWith(camelContext, "processing-stage-route", in ->
            in.weaveById("delivery-stage").replace().to(outboundEndpointMock));
        camelContext.start();

        outboundEndpointMock.expectedMessageCount(0);

        template.sendBody("direct:processing-stage", createWrongTypeOfCje());

        outboundEndpointMock.assertIsSatisfied();
        assertThat(meterRegistry.find("recordsFiltered").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsProcessingFailure").counter().count(), equalTo(1.0D));
        assertThat(meterRegistry.find("recordsProcessed").counter(), nullValue());
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
