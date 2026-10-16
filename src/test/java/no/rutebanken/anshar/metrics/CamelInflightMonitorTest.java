package no.rutebanken.anshar.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.InflightRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelInflightMonitorTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch blocked = new CountDownLatch(1);

    private CamelContext camelContext;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.getInflightRepository().setInflightBrowseEnabled(true);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:outer").routeId("outer")
                        .to("direct:inner");

                from("direct:inner").routeId("inner")
                        .process(p -> {
                            blocked.countDown();
                            release.await(10, TimeUnit.SECONDS);
                        }).id("blocking-node");
            }
        });
        camelContext.start();
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    public void tearDown() {
        release.countDown();
        camelContext.stop();
    }

    @Test
    public void testInflightExchangesArePublishedAsGaugePerRoute() throws Exception {
        sendBlockingExchange();

        CamelInflightMonitor monitor = new CamelInflightMonitor(camelContext, meterRegistry, 0);
        monitor.update();

        Gauge outer = meterRegistry.find("app.anshar.camel.inflight").tag("routeId", "outer").gauge();
        assertNotNull(outer);
        assertEquals(1, outer.value());

        Gauge oldest = meterRegistry.find("app.anshar.camel.inflight.oldest.seconds").gauge();
        assertNotNull(oldest);
        assertTrue(oldest.value() >= 0);

        release.countDown();
        waitForNoInflight();
        monitor.update();
        assertEquals(0, outer.value());
    }

    @Test
    public void testStuckExchangesAreReportedWithRouteAndNode() throws Exception {
        sendBlockingExchange();
        Thread.sleep(20);

        CamelInflightMonitor monitor = new CamelInflightMonitor(camelContext, meterRegistry, 0);
        List<InflightRepository.InflightExchange> stuck = monitor.update();

        assertEquals(1, stuck.size());
        InflightRepository.InflightExchange exchange = stuck.get(0);
        assertEquals("outer", exchange.getFromRouteId());
        assertEquals("inner", exchange.getAtRouteId());
        assertEquals("blocking-node", exchange.getNodeId());
    }

    @Test
    public void testExchangesYoungerThanThresholdAreNotReportedAsStuck() throws Exception {
        sendBlockingExchange();

        CamelInflightMonitor monitor = new CamelInflightMonitor(camelContext, meterRegistry, 300);

        assertTrue(monitor.update().isEmpty());
    }

    private void sendBlockingExchange() throws InterruptedException {
        camelContext.createProducerTemplate().asyncSendBody("direct:outer", "body");
        assertTrue(blocked.await(5, TimeUnit.SECONDS));
    }

    private void waitForNoInflight() throws InterruptedException {
        for (int i = 0; i < 100 && camelContext.getInflightRepository().size() > 0; i++) {
            Thread.sleep(50);
        }
        assertEquals(0, camelContext.getInflightRepository().size());
    }
}
