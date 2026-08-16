package sh.forq.forqsdk.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param processAfter optional Unix timestamp in milliseconds indicating when
 *                     the message should become visible for processing; null
 *                     means immediately
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewMessageRequest(
    String content,
    Long processAfter
) {
}
