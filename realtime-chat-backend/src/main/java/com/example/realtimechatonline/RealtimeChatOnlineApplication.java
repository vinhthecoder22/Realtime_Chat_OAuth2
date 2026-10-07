package com.example.realtimechatonline;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
        info = @Info(
                title = "Realtime Chat Online",
                version = "2.0.0",
                description = "Realtime Chat Website",
                contact = @Contact(name = "Developer", email = "dev@example.com"),
                license = @License(name = "Apache 2.0", url = "http://springdoc.org")
        )
)
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT authentication",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
@SpringBootApplication
public class RealtimeChatOnlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeChatOnlineApplication.class, args);
    }

}
