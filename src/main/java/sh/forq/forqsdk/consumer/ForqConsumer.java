package sh.forq.forqsdk.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import sh.forq.forqsdk.api.ErrorResponse;
import sh.forq.forqsdk.api.ErrorResponseException;
import sh.forq.forqsdk.api.MessageResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class ForqConsumer {
    private static final long LONG_POLLING_MAX_DURATION_MS = 30000;
    private static final long LONG_POLLING_BUFFER_MS = 10000;

    private static final String CONSUME_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages";
    private static final String ACK_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages/%s/ack";
    private static final String NACK_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages/%s/nack";

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String RECEIPT_HEADER = "X-Forq-Receipt";

    // Forq holds an empty consume request open for up to 30 seconds (long
    // polling), so the response timeout for that call must exceed it. Set
    // per-request, as the client-level configuration is not introspectable.
    private static final RequestConfig CONSUME_REQUEST_CONFIG = RequestConfig.custom()
        .setResponseTimeout(Timeout.ofMilliseconds(LONG_POLLING_MAX_DURATION_MS + LONG_POLLING_BUFFER_MS))
        .build();

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String forqServerUrl;
    private final String authSecret;

    public ForqConsumer(CloseableHttpClient httpClient,
                        String forqServerUrl,
                        String authSecret) {
        Objects.requireNonNull(httpClient, "httpClient must not be null");
        Objects.requireNonNull(forqServerUrl, "forqServerUrl must not be null");
        Objects.requireNonNull(authSecret, "authSecret must not be null");

        if (forqServerUrl.isBlank()) {
            throw new IllegalArgumentException("forqServerUrl must not be blank");
        }
        if (authSecret.isBlank()) {
            throw new IllegalArgumentException("authSecret must not be blank");
        }

        if (forqServerUrl.endsWith("/")) {
            forqServerUrl = forqServerUrl.substring(0, forqServerUrl.length() - 1);
        }

        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.forqServerUrl = forqServerUrl;
        this.authSecret = authSecret;
    }

    public Optional<MessageResponse> consumeOne(String queueName) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + CONSUME_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName);

        var request = new HttpGet(url);
        request.setConfig(CONSUME_REQUEST_CONFIG);
        request.addHeader(API_KEY_HEADER, authSecret);
        request.addHeader("Accept", "application/json");

        var response = execute(request);
        return switch (response.code()) {
            case 200 -> Optional.of(objectMapper.readValue(response.body(), MessageResponse.class));
            case 204 -> Optional.empty(); // no message available
            default -> throw toErrorResponseException(response);
        };
    }

    /**
     * Acknowledges the given message as successfully processed. The message's
     * receipt fences the ack to this exact delivery: if the message exceeded
     * the visibility timeout and was redelivered to another consumer, the ack
     * fails with a {@code not_found.message} error instead of affecting the
     * other delivery.
     */
    public void ack(String queueName, MessageResponse message) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + ACK_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName, message.id());
        sendAckNackRequest(url, message.receipt());
    }

    /**
     * Reports the given message as failed to process, scheduling a retry (or
     * a DLQ move once attempts are exhausted). Like ack, it is fenced to this
     * exact delivery via the message's receipt.
     */
    public void nack(String queueName, MessageResponse message) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + NACK_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName, message.id());
        sendAckNackRequest(url, message.receipt());
    }

    private void sendAckNackRequest(String url, String receipt) throws IOException, ErrorResponseException {
        var request = new HttpPost(url);
        request.addHeader(API_KEY_HEADER, authSecret);
        request.addHeader(RECEIPT_HEADER, receipt);
        request.addHeader("Accept", "application/json");

        var response = execute(request);
        if (response.code() != 204) {
            throw toErrorResponseException(response);
        }
    }

    private RawResponse execute(org.apache.hc.core5.http.ClassicHttpRequest request) throws IOException {
        return httpClient.execute(request, response -> {
            var entity = response.getEntity();
            var body = entity != null ? EntityUtils.toByteArray(entity) : new byte[0];
            return new RawResponse(response.getCode(), body);
        });
    }

    private ErrorResponseException toErrorResponseException(RawResponse response) throws IOException {
        var errorResponse = objectMapper.readValue(response.body(), ErrorResponse.class);
        return new ErrorResponseException(response.code(), errorResponse);
    }

    private record RawResponse(int code, byte[] body) {
    }
}
