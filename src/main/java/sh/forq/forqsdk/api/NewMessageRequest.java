package sh.forq.forqsdk.api;

public record NewMessageRequest(
    String content,
    Long ProcessAfter
) {
}
