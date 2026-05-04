package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepositoryPort {

  void insertPending(OutboxEvent event);

  List<OutboxEvent> claimPublishable(int batchSize, Instant olderThan, Instant now);

  void markSent(UUID id, Instant now);

  void markFailed(UUID id, String error, Instant now);

  void markDead(UUID id, String error, Instant now);
}
