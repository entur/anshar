package no.rutebanken.anshar.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With allowStAX=false, Camel wraps the StAXSource it creates for the body in a StAX2SAXSource. Saxon then tries to set
 * accessExternalDTD on it, and writes "Selected XML parser ... does not recognize the property" to stderr for every
 * transformation. With the default (allowStAX=true), the StAXSource is passed to Saxon directly.
 */
public class XsltAllowStAXTest {

    private static final String WARNING = "does not recognize the property http://javax.xml.XMLConstants/property/accessExternalDTD";

    private CamelContext context;
    private ProducerTemplate template;
    private String siri20;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
        siri20 = Files.readString(Path.of("src/test/resources/siri-et-from-bnr.xml"), StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void allowStAXFalseCausesWarning() {
        assertTrue(transform("xslt-saxon:xsl/split.xsl?allowStAX=false", siri20).stderr.contains(WARNING));
        assertFalse(transform("xslt-saxon:xsl/split.xsl", siri20).stderr.contains(WARNING));
    }

    @Test
    void outputIsTheSameWithAndWithoutStAX() {
        String siri14 = transform("xslt-saxon:xsl/siri_20_14.xsl", siri20).output;
        String soap = soapEnvelope(siri20);

        assertTrue(siri14.contains("EstimatedVehicleJourney"), "Expected SIRI 1.4 sample");
        assertTrue(transform("xslt-saxon:xsl/siri_soap_raw.xsl", soap).output.contains("EstimatedVehicleJourney"),
            "Expected SOAP sample to be converted to SIRI");

        record Case(String xsl, String input) {}
        List<Case> cases = List.of(
            new Case("indent.xsl", siri20),
            new Case("split.xsl", siri20),
            new Case("siri_14_20.xsl", siri14),
            new Case("siri_soap_raw.xsl", soap),
            new Case("siri_soap_raw.xsl", siri20)
        );

        List<Function<String, Object>> bodyTypes = List.of(
            s -> s,
            s -> s.getBytes(StandardCharsets.UTF_8),
            s -> new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))
        );

        for (Case c : cases) {
            for (Function<String, Object> bodyType : bodyTypes) {
                Result withoutStAX = transform("xslt-saxon:xsl/" + c.xsl + "?allowStAX=false", bodyType.apply(c.input));
                Result withStAX = transform("xslt-saxon:xsl/" + c.xsl, bodyType.apply(c.input));

                String description = c.xsl + " with " + bodyType.apply("").getClass().getSimpleName();
                assertFalse(withStAX.output.isEmpty(), "Empty output for " + description);
                assertEquals(withoutStAX.output, withStAX.output, "Output differs for " + description);
                assertFalse(withStAX.stderr.contains(WARNING), "Warning for " + description);
            }
        }
    }

    /**
     * Wraps the EstimatedTimetableDelivery in a SOAP GetEstimatedTimetableResponse, as received from SOAP providers
     */
    private static String soapEnvelope(String siri) {
        int start = siri.indexOf("<EstimatedTimetableDelivery");
        int end = siri.indexOf("</EstimatedTimetableDelivery>") + "</EstimatedTimetableDelivery>".length();
        String delivery = siri.substring(start, end)
            .replaceFirst("<EstimatedTimetableDelivery", "<EstimatedTimetableDelivery xmlns=\"http://www.siri.org.uk/siri\"");
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>" +
            "<siri:GetEstimatedTimetableResponse xmlns:siri=\"http://www.siri.org.uk/siri\">" +
            "<ServiceDeliveryInfo><siri:ResponseTimestamp>2018-08-28T09:08:35.867+02:00</siri:ResponseTimestamp>" +
            "<siri:ProducerRef>ENT</siri:ProducerRef></ServiceDeliveryInfo>" +
            "<Answer>" + delivery + "</Answer>" +
            "</siri:GetEstimatedTimetableResponse></soapenv:Body></soapenv:Envelope>";
    }

    private Result transform(String endpoint, Object body) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            String output = template.requestBody(endpoint, body, String.class);
            return new Result(output, err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(originalErr);
        }
    }

    private record Result(String output, String stderr) {}
}
