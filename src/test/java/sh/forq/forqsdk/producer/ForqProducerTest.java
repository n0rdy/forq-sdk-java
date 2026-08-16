package sh.forq.forqsdk.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.forq.forqsdk.TestHttpServer;
import sh.forq.forqsdk.api.ErrorCode;
import sh.forq.forqsdk.api.ErrorResponseException;
import sh.forq.forqsdk.api.NewMessageRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForqProducerTest {

    private static final String SECRET = "test-secret-that-is-32-chars-long";

    private static CloseableHttpClient httpClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        httpClient = HttpClients.createDefault();
    }

    @AfterAll
    static void tearDown() throws IOException {
        httpClient.close();
    }

    @Test
    void sendMessagePostsJsonBody() throws Exception {
        try (var server = new TestHttpServer(204, "")) {
            var producer = new ForqProducer(httpClient, server.url(), SECRET);

            producer.sendMessage(new NewMessageRequest("hello", 1755366229123L), "orders");

            var req = server.lastRequest();
            assertEquals("POST", req.method());
            assertEquals("/api/v1/queues/orders/messages", req.path());
            assertEquals(SECRET, req.apiKey());
            assertTrue(req.contentType().startsWith("application/json"));

            var sent = objectMapper.readValue(req.body(), NewMessageRequest.class);
            assertEquals("hello", sent.content());
            assertEquals(1755366229123L, sent.processAfter());
        }
    }

    @Test
    void sendMessageThrowsOnErrorResponse() throws Exception {
        try (var server = new TestHttpServer(400, """
            {"code":"bad_request.queue.produce_to_dlq"}""")) {
            var producer = new ForqProducer(httpClient, server.url(), SECRET);

            var ex = assertThrows(ErrorResponseException.class,
                () -> producer.sendMessage(new NewMessageRequest("x", null), "orders-dlq"));
            assertEquals(400, ex.getHttpStatusCode());
            assertEquals(ErrorCode.BAD_REQUEST_PRODUCE_TO_DLQ, ex.getErrorResponse().code());
        }
    }

    @Test
    void constructorValidatesArguments() {
        assertThrows(NullPointerException.class, () -> new ForqProducer(null, "http://x", SECRET));
        assertThrows(IllegalArgumentException.class, () -> new ForqProducer(httpClient, "", SECRET));
        assertThrows(IllegalArgumentException.class, () -> new ForqProducer(httpClient, "http://x", ""));
    }
}
