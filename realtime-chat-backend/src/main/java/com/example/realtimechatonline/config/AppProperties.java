package com.example.realtimechatonline.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private OAuth2 oauth2 = new OAuth2();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class OAuth2 {
        private String redirectUri;
        private List<String> allowedRedirectUris = List.of();
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of();
    }
}
