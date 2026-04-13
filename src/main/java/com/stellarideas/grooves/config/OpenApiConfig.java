package com.stellarideas.grooves.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stellarGroovesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stellar Grooves API")
                        .description("Music library management API. Supports scanning local directories for audio files, "
                                + "organizing tracks by genre, managing playlists, streaming audio, and admin operations.")
                        .version("1.0.0")
                        .contact(new Contact().name("Stellar Ideas")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .schemaRequirement("bearer-jwt", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token obtained from /api/v1/auth/signin"));
    }
}
