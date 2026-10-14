/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Providers may respond with gzip-compressed content (e.g. CheckStatus). The response must be decompressed exactly
 * once - HttpClient 5.6 decompresses without removing the Content-Encoding header, and Camel 4.20 then tries to
 * decompress the body again ("Not in GZIP format").
 */
public class HttpGzipResponseTest {

    private static final String SIRI_RESPONSE = "<Siri xmlns=\"http://www.siri.org.uk/siri\"><CheckStatusResponse/></Siri>";

    private HttpServer server;
    private CamelContext camelContext;

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/siri", exchange -> {
            byte[] body = gzip(SIRI_RESPONSE);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=UTF-8");
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        camelContext.stop();
        server.stop(0);
    }

    @Test
    public void testGzipResponseIsDecompressedOnce() {
        ProducerTemplate producer = camelContext.createProducerTemplate();
        String url = "http://localhost:" + server.getAddress().getPort() + "/siri";

        String response = producer.requestBody(url, "<Siri/>", String.class);

        assertEquals(SIRI_RESPONSE, response);
    }

    private static byte[] gzip(String content) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
