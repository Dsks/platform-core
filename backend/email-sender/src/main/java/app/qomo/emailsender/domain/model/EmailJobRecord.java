package app.qomo.emailsender.domain.model;

import java.util.UUID;

/**
 * Immutable domain snapshot of an email delivery job created from an upstream domain event.
 *
 * <p>The {@code eventId} identifies the source event and is the domain key that callers can use for
 * idempotency and duplicate suppression. The encrypted payload and nonce are kept together because
 * they form the complete input needed to render and send the message without exposing sensitive
 * template data in the domain command.
 *
 * @param eventId stable identifier of the source event represented by this job
 * @param correlationId trace identifier propagated across the email workflow
 * @param type business event type that caused the email to be scheduled
 * @param template template identifier to render for this delivery
 * @param toEmailFp fingerprint of the recipient email address, not the address itself
 * @param payloadEnc encrypted template payload associated with the job
 * @param payloadNonce cryptographic nonce required to decrypt {@code payloadEnc}
 * @param attempts number of delivery attempts already consumed by this job
 */
public record EmailJobRecord(
    UUID eventId,
    String correlationId,
    String type,
    String template,
    String toEmailFp,
    byte[] payloadEnc,
    byte[] payloadNonce,
    int attempts) {}
