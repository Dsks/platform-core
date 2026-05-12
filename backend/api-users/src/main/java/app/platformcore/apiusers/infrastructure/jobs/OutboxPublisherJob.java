package app.platformcore.apiusers.infrastructure.jobs;

import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.OutboxEventPublisherPort;
import app.platformcore.apiusers.application.port.out.OutboxRepositoryPort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Dispatches durable outbox records to the configured event publisher.
 *
 * <p>This infrastructure job is the bridge between transactional persistence and broker delivery:
 * application use cases can commit events to the outbox table together with their state changes,
 * while this worker later claims publishable records and hands them to {@link
 * OutboxEventPublisherPort}. The durable write and the broker publish are intentionally separated
 * so a database transaction does not depend on external messaging availability.
 *
 * <p>The job delegates claim, retry, and terminal-state semantics to {@link OutboxRepositoryPort}.
 * Claimed {@code PENDING} or {@code FAILED} events are processed as {@code IN_PROGRESS}; successful
 * deliveries become {@code SENT}, recoverable delivery failures become {@code FAILED}, and events
 * that have exhausted their attempt budget become {@code DEAD}. Re-running the job is safe for rows
 * that are already {@code SENT} or {@code DEAD} because publishable selection is handled by the
 * repository contract.
 */
public class OutboxPublisherJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

  private final OutboxRepositoryPort outboxRepository;
  private final OutboxEventPublisherPort outboxPublisher;
  private final ClockPort clock;
  private final int batchSize;
  private final int maxAttempts;
  private final Duration minAge;

  /**
   * Creates an outbox dispatcher with externally supplied scheduling policy values.
   *
   * @param outboxRepository port used to claim outbox rows and persist publication outcomes
   * @param outboxPublisher port used to deliver claimed events to the broker or eventing
   *     infrastructure
   * @param clock application clock used to compute retry eligibility and status timestamps
   * @param batchSize maximum number of events claimed per execution
   * @param maxAttempts maximum number of publication attempts before an event is marked dead
   * @param minAge minimum age since the last update before {@code PENDING} or {@code FAILED} rows
   *     are eligible for claim
   */
  public OutboxPublisherJob(
      OutboxRepositoryPort outboxRepository,
      OutboxEventPublisherPort outboxPublisher,
      ClockPort clock,
      int batchSize,
      int maxAttempts,
      Duration minAge) {
    this.outboxRepository = outboxRepository;
    this.outboxPublisher = outboxPublisher;
    this.clock = clock;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.minAge = minAge;
  }

  /**
   * Claims and publishes a batch of retryable outbox events.
   *
   * <p>Spring schedules this method with the {@code platformcore.outbox.publisher.fixed-delay-ms}
   * property, using the same value for the initial delay and the delay after each completed
   * execution. The method is public and side-effect driven, so tests or E2E support code can invoke
   * it directly to drain the outbox without waiting for the scheduler.
   *
   * <p>Each execution claims at most {@code batchSize} events older than {@code minAge}, publishes
   * them through {@link OutboxEventPublisherPort}, and records the result through {@link
   * OutboxRepositoryPort}. Runtime failures from publishing or outcome persistence are converted
   * into {@code FAILED} or {@code DEAD} status updates when possible; the warning logs identify
   * failed or terminal records without exposing the event payload.
   */
  @Scheduled(
      fixedDelayString = "${platformcore.outbox.publisher.fixed-delay-ms:2000}",
      initialDelayString = "${platformcore.outbox.publisher.fixed-delay-ms:2000}")
  public void publishPendingEvents() {
    var now = clock.now();
    var olderThan = now.minus(minAge);

    var events = outboxRepository.claimPublishable(batchSize, olderThan, now);
    for (var event : events) {
      // A row already at the attempt limit is claimed once more only to move it out of retry flow.
      if (event.attempts() >= maxAttempts) {
        outboxRepository.markDead(event.id(), "max attempts exceeded", now);
        continue;
      }
      try {
        outboxPublisher.publish(event);
        outboxRepository.markSent(event.id(), now);
      } catch (RuntimeException ex) {
        // Persist only the message text; the repository compacts and bounds it before storage.
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (event.attempts() + 1 >= maxAttempts) {
          outboxRepository.markDead(event.id(), message, now);
          log.warn("outbox_event_dead outboxId={} aggregateId={}", event.id(), event.aggregateId());
        } else {
          outboxRepository.markFailed(event.id(), message, now);
          log.warn(
              "outbox_event_failed outboxId={} aggregateId={}", event.id(), event.aggregateId());
        }
      }
    }
  }
}
