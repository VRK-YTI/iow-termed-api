package fi.vm.yti.terminology.api;

import java.util.concurrent.Executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestContextListener;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
    info = @Info(
        title = "YTI Terminology Service",
        description = "YTI Terminology Service - Terminology API",
        termsOfService = "https://opensource.org/licenses/EUPL-1.2",
        contact = @Contact(name = "YTI Terminology Service by the Digital and Population Data Services Agency", url = "https://yhteentoimiva.suomi.fi/", email = "yhteentoimivuus@dvv.fi"),
        license = @License(name = "EUPL-1.2", url = "https://opensource.org/licenses/EUPL-1.2")
    ),
    servers = { @Server(url = "/terminology-api", description = "Terminology API Service") },
    tags = {
        @Tag(name = "Integration", description = "YTI Terminology API - Integration API for software integrations"),
        @Tag(name = "Import-Export", description = "YTI Terminology API - Import-Export API for importing and exporting data"),
        @Tag(name = "Frontend", description = "YTI Terminology API - Frontend API  for internal Web UI usage"),
        @Tag(name = "Admin", description = "YTI Terminology API - Admin API for internal administrative usage"),
        @Tag(name = "System", description = "YTI Terminology API - System API for internal usage"),
        @Tag(name = "Resolve", description = "YTI Terminology API - Resolve API for resolving URIs to actual resources"),
        @Tag(name = "Private", description = "YTI Terminology API - Private API for internal YTI cluster usage"),
        @Tag(name = "Private/Integration", description = "YTI Terminology API - Private Integration API for internal software integrations"),
        @Tag(name = "Public", description = "YTI Terminology API - Public API for software integrations (DEPRECATED)")
    }
)
@SpringBootApplication
@ComponentScan(basePackages = {"fi.vm.yti"})
@EnableAsync
@EnableJms
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RequestContextListener requestContextListener() {
        return new RequestContextListener();
    }

    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ImportAPI-");
        executor.initialize();
        return executor;
    }
}
