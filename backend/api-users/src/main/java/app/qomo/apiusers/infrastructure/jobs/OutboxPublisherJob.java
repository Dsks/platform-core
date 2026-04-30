package app.qomo.apiusers.infrastructure.jobs;

import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.OutboxEventPublisherPort;
import app.qomo.apiusers.domain.port.out.OutboxRepositoryPort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class OutboxPublisherJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

  private final OutboxRepositoryPort outboxRepository;
  private final OutboxEventPublisherPort outboxPublisher;
  private final ClockPort clock;
  private final int batchSize;
  private final int maxAttempts;
  private final Duration minAge;

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

  @Scheduled(
      fixedDelayString = "${qomo.outbox.publisher.fixed-delay-ms:2000}",
      initialDelayString = "${qomo.outbox.publisher.fixed-delay-ms:2000}")
  public void publishPendingEvents() {
    var now = clock.now();
    var olderThan = now.minus(minAge);

    var events = outboxRepository.claimPublishable(batchSize, olderThan, now);
    for (var event : events) {
      if (event.attempts() >= maxAttempts) {
        outboxRepository.markDead(event.id(), "max attempts exceeded", now);
        continue;
      }
      try {
        outboxPublisher.publish(event);
        outboxRepository.markSent(event.id(), now);
      } catch (RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (event.attempts() + 1 >= maxAttempts) {
          outboxRepository.markDead(event.id(), message, now);
          log.warn("outbox_event_dead outboxId={} aggregateId={}", event.id(), event.aggregateId());
        } else {
          outboxRepository.markFailed(event.id(), message, now);
          log.warn("outbox_event_failed outboxId={} aggregateId={}", event.id(),
              event.aggregateId());
        }
      }
    }
  }
}