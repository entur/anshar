package no.rutebanken.anshar.integration;

import no.rutebanken.anshar.App;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.springframework.boot.test.context.SpringBootTest;

@CamelSpringBootTest
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.NONE, classes = App.class)
public abstract class SpringBootBaseTest {
}
