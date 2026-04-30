package app.qomo.emailsender.domain.model;

import java.util.UUID;

public record EmailJobRecord(
    UUID eventId,
    String correlationId,
    String type,
    String template,
    String toEmailFp,
    byte[] payloadEnc,
    byte[] payloadNonce,
    int attempts) {

}