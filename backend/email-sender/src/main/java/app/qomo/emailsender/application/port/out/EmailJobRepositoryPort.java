package app.qomo.emailsender.application.port.out;

import app.qomo.emailsender.domain.model.EmailJobRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailJobRepositoryPort {

  boolean tryCreatePending(
      UUID eventId,
      String correlationId,
      String type,
      String template,
      String toEmailFp,
      byte[] payloadEnc,
      byte[] payloadNonce,
      Instant now);

  void markSent(UUID eventId, Instant now);

  void markFailed(UUID eventId, String error, Instant now);

  List<EmailJobRecord> claimRetryCandidates(
      int maxAttempts, Instant olderThan, int limit, Instant now);

  void markDead(UUID eventId, String error, Instant now);
}
