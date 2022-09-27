package my.threehumpedcamel;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class MainRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("{{app.camel.component.kafka}}")
            .to(String.format("log:%s", this.getClass().getName()));
    }
}
