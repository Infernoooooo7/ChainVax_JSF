package edu.nmamit.chainvax.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ChainVax API",
                version = "0.1.0-demo",
                description = "Cold-chain custody and excursion ledger (academic demo). "
                        + "Get a token from POST /api/auth/login (staff) or POST /api/auth/device-token (logger), "
                        + "click Authorize and paste it. Readings with source SIMULATOR come from the built-in "
                        + "logger simulator. DEMO-FAST policy thresholds are illustrative, not medical guidance."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
