package com.example.guardian.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the Host Guardian REST API.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Host Guardian API",
                version = "1.0.0",
                description = """
                        REST API for configuring Linux hosts, grouping monitored services,
                        checking service health, and triggering controlled restarts.
                        """
        ),
        servers = {
                @Server(url = "/", description = "Current application host")
        },
        tags = {
                @Tag(name = "Dashboard", description = "Aggregated service status and dashboard filter data"),
                @Tag(name = "Hosts", description = "Host configurations used for local or SSH command execution"),
                @Tag(name = "Service groups", description = "Logical groups used to filter monitored services"),
                @Tag(name = "Monitored services", description = "Service definitions, monitoring flags, and manual actions")
        }
)
public class OpenApiConfig {
}
