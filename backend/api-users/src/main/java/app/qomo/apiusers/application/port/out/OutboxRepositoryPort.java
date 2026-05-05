package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Abstracts durable storage for events managed through the outbox pattern.
 *
 * <p>The application expects implementations to persist pending events atomically with the use case
 * that produced them and to support safe claiming by dispatchers so retries do not publish the same
 * event concurrently. Event payloads may contain emails, verification codes, correlation data, or
 * other sensitive fields and must not be logged in clear text.
 *
 * <p>This contract manages event state only. It does not define broker topics, payload schemas, or
 * business decisions about when an event should be emitted.
 */
public interface OutboxRepositoryPort {

  /**
   * Records an event as pending publication.
   *
   * @param event event metadata and payload to persist for later dispatch
   */
  void insertPending(OutboxEvent event);

  /**
   * Claims a batch of pending or retryable events for publication.
   *
   * <p>Implementations should make claimed events unavailable to concurrent dispatchers until they
   * are marked sent, failed, or dead.
   *
   * @param batchSize maximum number of events to claim
   * @param olderThan only events last updated before this instant are eligible
   * @param now application time to record as the claim update time
   * @return events claimed for the current publish attempt
   */
  List<OutboxEvent> claimPublishable(int batchSize, Instant olderThan, Instant now);

  /**
   * Marks a claimed event as successfully published.
   *
   * @param id outbox event identifier
   * @param now application time to record as the sent and update timestamp
   */
  void markSent(UUID id, Instant now);

  /**
   * Marks a claimed event as failed while preserving it for a future retry.
   *
   * @param id outbox event identifier
   * @param error failure summary safe for operational storage; payload contents should not be
   *     included
   * @param now application time to record as the failure update timestamp
   */
  void markFailed(UUID id, String error, Instant now);

  /**
   * Marks a claimed event as permanently failed and no longer eligible for retries.
   *
   * @param id outbox event identifier
   * @param error terminal failure summary safe for operational storage; payload contents should not
   *     be included
   * @param now application time to record as the terminal update timestamp
   */
  void markDead(UUID id, String error, Instant now);
}
