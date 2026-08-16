package sh.forq.forqsdk.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import sh.forq.forqsdk.api.ErrorResponse;
import sh.forq.forqsdk.api.ErrorResponseException;
import sh.forq.forqsdk.api.NewMessageRequest;

import java.io.IOException;
import java.util.Objects;

public class ForqProducer {
    private static final String PRODUCE_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages";

    private static final String API_KEY_HEADER = "X-API-Key";

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String forqServerUrl;
    private final String authSecret;

    public ForqProducer(CloseableHttpClient httpClient,
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

    public void sendMessage(NewMessageRequest newMessage, String queueName) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + PRODUCE_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName);

        var request = new HttpPost(url);
        request.addHeader(API_KEY_HEADER, authSecret);
        request.addHeader("Accept", "application/json");
        request.setEntity(new ByteArrayEntity(objectMapper.writeValueAsBytes(newMessage), ContentType.APPLICATION_JSON));

        var result = httpClient.execute(request, response -> {
            var entity = response.getEntity();
            var body = entity != null ? EntityUtils.toByteArray(entity) : new byte[0];
            return new RawResponse(response.getCode(), body);
        });

        if (result.code() != 204) {
            var errorResponse = objectMapper.readValue(result.body(), ErrorResponse.class);
            throw new ErrorResponseException(result.code(), errorResponse);
        }
    }

    private record RawResponse(int code, byte[] body) {
    }
}
