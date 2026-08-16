package sh.forq.forqsdk.api;

/**
 * @param receipt identifies this particular delivery of the message and is
 *                required by the Forq server on ack/nack. Opaque - do not
 *                parse it.
 */
public record MessageResponse(
    String id,
    String content,
    String receipt
) {
}
