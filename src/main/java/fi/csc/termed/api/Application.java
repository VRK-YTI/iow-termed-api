package fi.csc.termed.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan({"fi.csc.termed.api.search"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}