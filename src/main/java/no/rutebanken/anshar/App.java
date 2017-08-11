package no.rutebanken.anshar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
public class App {

    // must have a main method spring-boot can run
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

}