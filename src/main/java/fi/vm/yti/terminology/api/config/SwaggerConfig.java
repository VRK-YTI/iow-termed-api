package fi.vm.yti.terminology.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {

    @Bean
    public Docket frontendApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("frontend")
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.ant("/api/v1/frontend/**"))
                .build();
    }

    @Bean
    public Docket integrationApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("integration")
                .select()
                .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.ant("/api/v1/integration/**"))
                .build();
    }

    @Bean
    public Docket importApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("import")
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.ant("/api/v1/import/**"))
                .build();
    }

    @Bean
    public Docket exportApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("export")
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.ant("/api/v1/export/**"))
                .build();
    }

    @Bean
    public Docket publicApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("publicapi")
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.ant("/api/v1/public/**"))
                .build();
    }

    @Bean
    public Docket systemApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("system")
                .select()
                .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.ant("/api/v1/system/**"))
                .build();
    }

    @Bean
    public Docket adminApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("admin")
                .select()
                .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.ant("/api/v1/admin/**"))
            .build();
    }

    @Bean
    public Docket privateApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("private")
            .select()
            .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.ant("/private/v1/**"))
                .build();
    }
}
