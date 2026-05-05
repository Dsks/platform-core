package app.qomo.emailsender.application.port.out;

import app.qomo.emailsender.domain.model.EmailJobRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable boundary for email jobs created by application use cases.
 *
 * <p>Implementations normally belong to the infrastructure layer and translate this contract to
 * persistent storage while hiding schema, locking, transaction management, and serialization
 * details from the application core. Application services provide prepared job metadata, encrypted
 * payload bytes, retry criteria, and timestamps; the port exposes only creation, state transitions,
 * and retry claiming outcomes needed by the use case.
 */
public interface EmailJobRepositoryPort {

  /**
   * Attempts to persist a new pending job for an event.
   *
   * <p>The {@code eventId} is the deduplication key expressed by this contract: callers can use the
   * boolean result to decide whether the event produced a newly stored job or was already
   * represented in storage. Implementations should make the create-or-deduplicate decision
   * atomically so concurrent handling of the same event cannot create more than one pending job.
   *
   * @param eventId unique event identifier used to deduplicate job creation
   * @param correlationId correlation value propagated with the event, if supplied by the caller
   * @param type event or notification type associated with the job
   * @param template template identifier selected by the application layer
   * @param toEmailFp fingerprint of the recipient address, suitable for persistence without
   *     exposing the raw address
   * @param payloadEnc encrypted job payload prepared before crossing the persistence boundary
   * @param payloadNonce nonce required by the application crypto boundary to decrypt the payload
   * @param now application timestamp to use for persisted audit or scheduling fields
   * @return {@code true} when a pending job was created; {@code false} when storage already
   *     contained a job for the same event
   */
  boolean tryCreatePending(
      UUID eventId,
      String correlationId,
      String type,
      String template,
      String toEmailFp,
      byte[] payloadEnc,
      byte[] payloadNonce,
      Instant now);

  /**
   * Marks the job for the given event as successfully delivered.
   *
   * <p>The state change is persisted by the implementation and should leave provider-specific or
   * database-specific update details outside the application core.
   *
   * @param eventId event identifier of the job whose lifecycle should move to the sent state
   * @param now application timestamp to persist with the state transition
   */
  void markSent(UUID eventId, Instant now);

  /**
   * Records a failed delivery attempt for the job associated with an event.
   *
   * <p>The error value is application-visible diagnostic context supplied by the caller; the
   * implementation is responsible for storing it without exposing persistence details to the use
   * case.
   *
   * @param eventId event identifier of the job that failed during processing
   * @param error failure description selected by the application layer
   * @param now application timestamp to persist with the failed attempt
   */
  void markFailed(UUID eventId, String error, Instant now);

  /**
   * Claims jobs that are eligible for retry processing.
   *
   * <p>The returned jobs represent work assigned to the caller for this retry pass. Implementations
   * are expected to apply the attempt threshold, age threshold, and limit as a single claim
   * operation so competing workers do not process the same candidate concurrently. An empty list
   * means no jobs currently satisfy the retry criteria.
   *
   * @param maxAttempts attempt threshold used to decide whether a job remains retryable
   * @param olderThan threshold used to select jobs old enough to retry
   * @param limit maximum number of jobs to claim for this pass
   * @param now application timestamp to persist as part of the claim operation
   * @return claimed retry candidates, possibly empty, in the record shape consumed by the
   *     application
   */
  List<EmailJobRecord> claimRetryCandidates(
      int maxAttempts, Instant olderThan, int limit, Instant now);

  /**
   * Moves the job for an event to a terminal failed state.
   *
   * <p>This transition is used when the application decides the job should no longer be retried.
   *
   * @param eventId event identifier of the job to stop retrying
   * @param error terminal failure description selected by the application layer
   * @param now application timestamp to persist with the terminal transition
   */
  void markDead(UUID eventId, String error, Instant now);
}
