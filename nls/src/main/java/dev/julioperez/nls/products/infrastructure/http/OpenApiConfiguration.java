package dev.julioperez.nls.products.infrastructure.http;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Natural Language Search API",
        version = "0.0.1-SNAPSHOT",
        description = "Semantic product catalog search using structured criteria or natural language."))
public class OpenApiConfiguration {
}
