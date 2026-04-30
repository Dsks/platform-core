package app.qomo.apiusers.infrastructure.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.domain.model.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherAdapterTest {

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  private KafkaOutboxPublisherAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new KafkaOutboxPublisherAdapter(kafkaTemplate, new ObjectMapper());
  }

  @Test
  void publish_shouldSendEventAndComplete_whenKafkaAndPayloadParsingSucceed() {
    var event = validEventWithPayload(
        "{\"eventId\":\"evt-1\",\"correlationId\":\"cor-1\",\"fingerprint\":\"fp-1\"}");
    var sendFuture = CompletableFuture.<SendResult<String, String>>completedFuture(null);
    when(kafkaTemplate.send(event.topic(), event.key(), event.payloadJson())).thenReturn(
        sendFuture);

    assertThatCode(() -> adapter.publish(event)).doesNotThrowAnyException();

    verify(kafkaTemplate).send(event.topic(), event.key(), event.payloadJson());
  }

  @Test
  void publish_shouldWrapExecutionExceptionAsIllegalStateException() throws Exception {
    var event = validEventWithPayload("{\"eventId\":\"evt-2\"}");
    CompletableFuture<SendResult<String, String>> sendFuture = mock(CompletableFuture.class);

    when(kafkaTemplate.send(event.topic(), event.key(), event.payloadJson())).thenReturn(
        sendFuture);
    when(sendFuture.get()).thenThrow(
        new ExecutionException(new RuntimeException("kafka unavailable")));

    assertThatThrownBy(() -> adapter.publish(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to publish outbox event")
        .hasCauseInstanceOf(ExecutionException.class);
  }

  @Test
  void publish_shouldRestoreInterruptFlagAndWrapInterruptedException() throws Exception {
    var event = validEventWithPayload("{\"eventId\":\"evt-3\"}");
    CompletableFuture<SendResult<String, String>> sendFuture = mock(CompletableFuture.class);

    when(kafkaTemplate.send(event.topic(), event.key(), event.payloadJson())).thenReturn(
        sendFuture);
    when(sendFuture.get()).thenThrow(new InterruptedException("interrupted"));

    try {
      assertThatThrownBy(() -> adapter.publish(event))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Interrupted while publishing outbox event")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void publish_shouldWrapPayloadParsingErrorAfterSuccessfulSend() {
    var event = validEventWithPayload("not-json");
    var sendFuture = CompletableFuture.<SendResult<String, String>>completedFuture(null);
    when(kafkaTemplate.send(event.topic(), event.key(), event.payloadJson())).thenReturn(
        sendFuture);

    assertThatThrownBy(() -> adapter.publish(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to publish outbox event")
        .hasCauseInstanceOf(java.io.IOException.class);
  }

  private static OutboxEvent validEventWithPayload(String payloadJson) {
    return new OutboxEvent(
        UUID.fromString("44444444-4444-4444-8444-444444444444"),
        "User",
        UUID.fromString("55555555-5555-4555-8555-555555555555"),
        "UserRegistered",
        "users.events",
        "user-5555",
        payloadJson,
        0,
        Instant.parse("2026-04-09T10:00:00Z"),
        Instant.parse("2026-04-09T10:00:00Z"));
  }
}