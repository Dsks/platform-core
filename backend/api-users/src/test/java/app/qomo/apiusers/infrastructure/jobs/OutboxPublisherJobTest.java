package app.qomo.apiusers.infrastructure.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.OutboxEventPublisherPort;
import app.qomo.apiusers.application.port.out.OutboxRepositoryPort;
import app.qomo.apiusers.domain.model.OutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherJobTest {

  private static final Instant NOW = Instant.parse("2026-04-08T10:15:30Z");
  private static final Duration MIN_AGE = Duration.ofSeconds(30);
  private static final int BATCH_SIZE = 50;
  private static final int MAX_ATTEMPTS = 3;

  @Mock private OutboxRepositoryPort outboxRepository;

  @Mock private OutboxEventPublisherPort outboxPublisher;

  private OutboxPublisherJob job;

  @BeforeEach
  void setUp() {
    ClockPort clock = () -> NOW;
    job =
        new OutboxPublisherJob(
            outboxRepository, outboxPublisher, clock, BATCH_SIZE, MAX_ATTEMPTS, MIN_AGE);
  }

  @Test
  void publishPendingEvents_shouldDoNothingElse_whenNoPublishableEventsExist() {
    var olderThan = NOW.minus(MIN_AGE);
    when(outboxRepository.claimPublishable(BATCH_SIZE, olderThan, NOW)).thenReturn(List.of());

    job.publishPendingEvents();

    verify(outboxRepository).claimPublishable(BATCH_SIZE, olderThan, NOW);
    verify(outboxPublisher, never()).publish(any());
    verify(outboxRepository, never()).markSent(any(), any());
    verify(outboxRepository, never()).markFailed(any(), any(), any());
    verify(outboxRepository, never()).markDead(any(), any(), any());
    verifyNoMoreInteractions(outboxRepository, outboxPublisher);
  }

  @Test
  void publishPendingEvents_shouldMarkSent_whenPublishSucceeds() {
    var event = eventWithAttempts(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), 1);
    var olderThan = NOW.minus(MIN_AGE);
    when(outboxRepository.claimPublishable(BATCH_SIZE, olderThan, NOW)).thenReturn(List.of(event));

    job.publishPendingEvents();

    InOrder order = inOrder(outboxRepository, outboxPublisher);
    order.verify(outboxRepository).claimPublishable(BATCH_SIZE, olderThan, NOW);
    order.verify(outboxPublisher).publish(event);
    order.verify(outboxRepository).markSent(event.id(), NOW);
    verify(outboxRepository, never()).markFailed(any(), any(), any());
    verify(outboxRepository, never()).markDead(any(), any(), any());
    verifyNoMoreInteractions(outboxRepository, outboxPublisher);
  }

  @Test
  void publishPendingEvents_shouldMarkFailed_whenPublishThrowsAndRetryIsStillPossible() {
    var event = eventWithAttempts(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), 1);
    var olderThan = NOW.minus(MIN_AGE);
    var error = new RuntimeException("kafka timeout");
    when(outboxRepository.claimPublishable(BATCH_SIZE, olderThan, NOW)).thenReturn(List.of(event));
    doThrow(error).when(outboxPublisher).publish(event);

    job.publishPendingEvents();

    verify(outboxRepository).claimPublishable(BATCH_SIZE, olderThan, NOW);
    verify(outboxPublisher).publish(event);
    verify(outboxRepository).markFailed(event.id(), "kafka timeout", NOW);
    verify(outboxRepository, never()).markSent(any(), any());
    verify(outboxRepository, never()).markDead(any(), eq("kafka timeout"), any());
    verifyNoMoreInteractions(outboxRepository, outboxPublisher);
  }

  @Test
  void publishPendingEvents_shouldMarkDead_whenPublishThrowsAndAttemptReachesLimit() {
    var event = eventWithAttempts(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"), 2);
    var olderThan = NOW.minus(MIN_AGE);
    when(outboxRepository.claimPublishable(BATCH_SIZE, olderThan, NOW)).thenReturn(List.of(event));
    doThrow(new RuntimeException()).when(outboxPublisher).publish(event);

    job.publishPendingEvents();

    verify(outboxRepository).claimPublishable(BATCH_SIZE, olderThan, NOW);
    verify(outboxPublisher).publish(event);
    verify(outboxRepository).markDead(event.id(), "RuntimeException", NOW);
    verify(outboxRepository, never()).markSent(any(), any());
    verify(outboxRepository, never()).markFailed(any(), any(), any());
    verifyNoMoreInteractions(outboxRepository, outboxPublisher);
  }

  @Test
  void publishPendingEvents_shouldShortCircuitToDead_whenEventAlreadyAtMaxAttempts() {
    var event = eventWithAttempts(UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"), 3);
    var olderThan = NOW.minus(MIN_AGE);
    when(outboxRepository.claimPublishable(BATCH_SIZE, olderThan, NOW)).thenReturn(List.of(event));

    job.publishPendingEvents();

    verify(outboxRepository).claimPublishable(BATCH_SIZE, olderThan, NOW);
    verify(outboxRepository).markDead(event.id(), "max attempts exceeded", NOW);
    verify(outboxPublisher, never()).publish(any());
    verify(outboxRepository, never()).markSent(any(), any());
    verify(outboxRepository, never()).markFailed(any(), any(), any());
    verifyNoMoreInteractions(outboxRepository, outboxPublisher);
  }

  @Test
  void publishPendingEvents_shouldProcessMultipleEventsInSingleRun_andContinueAfterFailure() {
    var publishFailsRetryable =
        eventWithAttempts(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"), 1);
    var alreadyExhausted =
        eventWithAttempts(UUID.fromString("11111111-1111-4111-8111-111111111111"), 3);
    var olderThan = NOW.minus(MIN_AGE);

    when(outboxRepository.claimPublishable(BATCH_SIZE, olderThan, NOW))
        .thenReturn(List.of(publishFailsRetryable, alreadyExhausted));
    doThrow(new RuntimeException("transient")).when(outboxPublisher).publish(publishFailsRetryable);

    job.publishPendingEvents();

    InOrder order = inOrder(outboxRepository, outboxPublisher);
    order.verify(outboxRepository).claimPublishable(BATCH_SIZE, olderThan, NOW);
    order.verify(outboxPublisher).publish(publishFailsRetryable);
    order.verify(outboxRepository).markFailed(publishFailsRetryable.id(), "transient", NOW);
    order.verify(outboxRepository).markDead(alreadyExhausted.id(), "max attempts exceeded", NOW);

    verifyNoMoreInteractions(outboxRepository, outboxPublisher);
  }

  private static OutboxEvent eventWithAttempts(UUID id, int attempts) {
    return new OutboxEvent(
        id,
        "User",
        UUID.fromString("22222222-2222-4222-8222-222222222222"),
        "UserRegistered",
        "users.events",
        "user-2222",
        "{\"id\":\"2222\"}",
        attempts,
        Instant.parse("2026-04-08T10:00:00Z"),
        Instant.parse("2026-04-08T10:00:00Z"));
  }
}
