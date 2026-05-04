package app.qomo.emailsender.infrastructure.adapter.in.kafka;

import static app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase.EmailCommandProcessingOutcome.COMPLETED;
import static app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase.EmailCommandProcessingOutcome.RECOVERABLE_STATE_PERSISTED;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.emailsender.application.exception.InvalidEmailCommandException;
import app.qomo.emailsender.application.model.EmailCommandMessage;
import app.qomo.emailsender.application.observability.HashUtil;
import app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class EmailCommandsConsumerTest {

  @Mock private HashUtil hashUtil;
  @Mock private ObjectMapper objectMapper;
  @Mock private ProcessEmailCommandUseCase processEmailCommandUseCase;
  @Mock private Acknowledgment ack;

  private EmailCommandsConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new EmailCommandsConsumer(hashUtil, objectMapper, processEmailCommandUseCase);
    when(hashUtil.sha256Hex(any())).thenReturn("payload-hash");
  }

  @Test
  void consume_validCommandAndCompletedOutcome_acknowledgesOffset() throws Exception {
    String payload = "{\"eventId\":\"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b\"}";
    EmailCommandMessage message = validMessage();

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(message);
    when(processEmailCommandUseCase.process(message, payload)).thenReturn(COMPLETED);

    consumer.consume(payload, ack);

    verify(processEmailCommandUseCase).process(message, payload);
    verify(ack).acknowledge();
  }

  @Test
  void consume_recoverableStatePersistedOutcome_acknowledgesOffset() throws Exception {
    String payload = "{\"eventId\":\"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b\"}";
    EmailCommandMessage message = validMessage();

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(message);
    when(processEmailCommandUseCase.process(message, payload))
        .thenReturn(RECOVERABLE_STATE_PERSISTED);

    consumer.consume(payload, ack);

    verify(processEmailCommandUseCase).process(message, payload);
    verify(ack).acknowledge();
  }

  @Test
  void consume_jsonParseError_acknowledgesAndSkipsUseCase() throws Exception {
    String payload = "{invalid-json";
    when(objectMapper.readValue(payload, EmailCommandMessage.class))
        .thenThrow(new JsonProcessingException("broken-json") {});

    consumer.consume(payload, ack);

    verify(processEmailCommandUseCase, never()).process(any(), any());
    verify(ack).acknowledge();
  }

  @Test
  void consume_messageWithValidationError_acknowledgesAndSkipsUseCase() throws Exception {
    String payload = "{\"eventId\":\"\"}";
    EmailCommandMessage invalidMessage =
        new EmailCommandMessage(
            "",
            "2026-01-01T10:00:00Z",
            "corr-1",
            "user-1",
            "test@example.com",
            "123456",
            "EMAIL_VERIFICATION",
            "EMAIL_VERIFICATION_REQUESTED");

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(invalidMessage);

    consumer.consume(payload, ack);

    verify(processEmailCommandUseCase, never()).process(any(), any());
    verify(ack).acknowledge();
  }

  @Test
  void consume_semanticallyInvalidEmail_acknowledgesAndSkipsUseCase() throws Exception {
    String payload = "{\"eventId\":\"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b\"}";
    EmailCommandMessage invalidMessage =
        new EmailCommandMessage(
            "31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b",
            "2026-01-01T10:00:00Z",
            "corr-1",
            "user-1",
            "not-an-email",
            "123456",
            "EMAIL_VERIFICATION",
            "EMAIL_VERIFICATION_REQUESTED");

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(invalidMessage);

    consumer.consume(payload, ack);

    verify(processEmailCommandUseCase, never()).process(any(), any());
    verify(ack).acknowledge();
  }

  @Test
  void consume_semanticallyInvalidVerificationCode_acknowledgesAndSkipsUseCase() throws Exception {
    String payload = "{\"eventId\":\"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b\"}";
    EmailCommandMessage invalidMessage =
        new EmailCommandMessage(
            "31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b",
            "2026-01-01T10:00:00Z",
            "corr-1",
            "user-1",
            "test@example.com",
            "ABC123",
            "EMAIL_VERIFICATION",
            "EMAIL_VERIFICATION_REQUESTED");

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(invalidMessage);

    consumer.consume(payload, ack);

    verify(processEmailCommandUseCase, never()).process(any(), any());
    verify(ack).acknowledge();
  }

  @Test
  void consume_invalidUuid_acknowledgesAndSkipsUseCase() throws Exception {
    String payload = "{\"eventId\":\"invalid-uuid\"}";
    EmailCommandMessage invalidMessage =
        new EmailCommandMessage(
            "invalid-uuid",
            "2026-01-01T10:00:00Z",
            "corr-1",
            "user-1",
            "test@example.com",
            "123456",
            "EMAIL_VERIFICATION",
            "EMAIL_VERIFICATION_REQUESTED");

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(invalidMessage);

    consumer.consume(payload, ack);

    verify(processEmailCommandUseCase, never()).process(any(), any());
    verify(ack).acknowledge();
  }

  @Test
  void consume_useCaseSignalsInvalidCommand_acknowledgesOffset() throws Exception {
    String payload = "{\"eventId\":\"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b\"}";
    EmailCommandMessage message = validMessage();

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(message);
    when(processEmailCommandUseCase.process(message, payload))
        .thenThrow(new InvalidEmailCommandException("eventId_invalid"));

    consumer.consume(payload, ack);

    verify(ack).acknowledge();
  }

  @Test
  void consume_useCaseRuntimeFailure_doesNotAcknowledgeOffset() throws Exception {
    String payload = "{\"eventId\":\"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b\"}";
    EmailCommandMessage message = validMessage();

    when(objectMapper.readValue(payload, EmailCommandMessage.class)).thenReturn(message);
    when(processEmailCommandUseCase.process(message, payload))
        .thenThrow(new IllegalStateException("db-not-reachable"));

    consumer.consume(payload, ack);

    verify(ack, never()).acknowledge();
  }

  @Test
  void consume_unexpectedMapperRuntimeException_propagatesAndDoesNotAcknowledge() throws Exception {
    String payload = "{\"eventId\":\"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b\"}";
    when(objectMapper.readValue(eq(payload), eq(EmailCommandMessage.class)))
        .thenThrow(new IllegalStateException("mapper-runtime-broke"));

    assertThrows(IllegalStateException.class, () -> consumer.consume(payload, ack));

    verify(processEmailCommandUseCase, never()).process(any(), any());
    verify(ack, never()).acknowledge();
  }

  private EmailCommandMessage validMessage() {
    return new EmailCommandMessage(
        "31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b",
        "2026-01-01T10:00:00Z",
        "corr-1",
        "user-1",
        "test@example.com",
        "123456",
        "EMAIL_VERIFICATION",
        "EMAIL_VERIFICATION_REQUESTED");
  }
}
