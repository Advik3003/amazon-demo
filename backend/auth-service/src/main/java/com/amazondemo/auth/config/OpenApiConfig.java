package com.amazondemo.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger Configuration
 * Configures the Swagger UI with JWT authentication support.
 * After logging in, you can click "Authorize" in Swagger and enter the JWT
 * to test secured endpoints directly from the browser.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Amazon Demo - Auth Service API",
        version = "1.0.0",
        description = "Authentication and Authorization API for Amazon Demo E-Commerce Platform",
        contact = @Contact(name = "Amazon Demo Team", email = "dev@amazondemo.com")
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
