package no.rutebanken.anshar.integration;

import no.rutebanken.anshar.App;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;

@RunWith(CamelSpringBootRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public abstract class SpringBootBaseTest {
}
