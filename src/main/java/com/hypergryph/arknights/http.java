package com.hypergryph.arknights;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class http {
    private final int serverPort;
    private final int httpPort;

    public http() {
        this.serverPort = ArknightsApplication.serverConfig.getJSONObject("server").getIntValue("https");
        this.httpPort = ArknightsApplication.serverConfig.getJSONObject("server").getIntValue("http");
    }

    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
            }
        };

        tomcat.addConnectorCustomizers(http::allowBracketChars);

        Connector httpConnector = initiateHttpConnector();
        allowBracketChars(httpConnector);
        tomcat.addAdditionalTomcatConnectors(httpConnector);

        return tomcat;
    }

    private Connector initiateHttpConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setPort(this.httpPort);
        connector.setRedirectPort(this.serverPort);
        connector.setSecure(true);
        return connector;
    }

    private static void allowBracketChars(Connector connector) {
        connector.setProperty("relaxedPathChars", "[,]");
        connector.setProperty("relaxedQueryChars", "[,]");
    }
}