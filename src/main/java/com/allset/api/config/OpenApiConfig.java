package com.allset.api.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name       = "bearerAuth",
    type       = SecuritySchemeType.HTTP,
    scheme     = "bearer",
    bearerFormat = "JWT",
    description  = "Token JWT obtido via POST /api/auth/login. Formato: Bearer <token>"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("All Set API")
                .description("Marketplace de Serviços Autônomos — API REST")
                .version("v1")
                .contact(new Contact()
                    .name("All Set")
                    .email("contato@allset.com.br")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
