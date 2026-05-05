package app.qomo.emailsender.application.service;

import static app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase.EmailCommandProcessingOutcome.COMPLETED;
import static app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase.EmailCommandProcessingOutcome.RECOVERABLE_STATE_PERSISTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.emailsender.application.command.EmailCommandMessage;
import app.qomo.emailsender.application.exception.InvalidEmailCommandException;
import app.qomo.emailsender.application.observability.PiiUtil;
import app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase.EmailCommandProcessingOutcome;
import app.qomo.emailsender.application.port.out.ClockPort;
import app.qomo.emailsender.application.port.out.EmailJobRepositoryPort;
import app.qomo.emailsender.application.port.out.EmailSenderPort;
import app.qomo.emailsender.application.port.out.EmailTemplateRendererPort;
import app.qomo.emailsender.application.port.out.PayloadCryptoPort;
import app.qomo.emailsender.application.port.out.PayloadCryptoPort.EncryptedPayload;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessEmailCommandServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:15:30Z");

  @Mock private EmailTemplateRendererPort templateRenderer;
  @Mock private EmailSenderPort emailSender;
  @Mock private EmailJobRepositoryPort emailJobRepository;
  @Mock private PayloadCryptoPort payloadCrypto;

  private ProcessEmailCommandService service;

  @BeforeEach
  void setUp() {
    ClockPort fixedClock = () -> FIXED_NOW;

    service =
        new ProcessEmailCommandService(
            templateRenderer,
            emailSender,
            emailJobRepository,
            payloadCrypto,
            fixedClock,
            "Qomo",
            "Verify your email");
  }

  @Test
  void process_validSupportedCommand_persistsPendingSendsAndMarksSent() {
    EmailCommandMessage message = validMessage();
    String rawPayload = rawPayload();
    UUID eventId = UUID.fromString(message.eventId());

    byte[] enc = new byte[] {1, 2, 3};
    byte[] nonce = new byte[] {9, 9, 9};
    when(payloadCrypto.encrypt(any())).thenReturn(new EncryptedPayload(enc, nonce));
    when(emailJobRepository.tryCreatePending(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(true);
    when(templateRenderer.render(any(), any())).thenReturn("<html>ok</html>");

    EmailCommandProcessingOutcome outcome = service.process(message, rawPayload);

    assertEquals(COMPLETED, outcome);
    verify(payloadCrypto).encrypt(eq(rawPayload.getBytes(StandardCharsets.UTF_8)));
    verify(emailJobRepository)
        .tryCreatePending(
            eq(eventId),
            eq(message.correlationId()),
            eq(message.type()),
            eq(message.template()),
            eq(PiiUtil.emailFingerprint(message.toEmail())),
            eq(enc),
            eq(nonce),
            eq(FIXED_NOW));
    verify(templateRenderer).render(eq(message.template()), any());
    verify(emailSender)
        .sendHtml(eq(message.toEmail()), eq("Verify your email"), eq("<html>ok</html>"));
    verify(emailJobRepository).markSent(eq(eventId), eq(FIXED_NOW));
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
  }

  @Test
  void process_duplicateEventId_returnsCompletedWithoutSending() {
    EmailCommandMessage message = validMessage();
    UUID eventId = UUID.fromString(message.eventId());

    byte[] enc = new byte[] {1, 2, 3};
    byte[] nonce = new byte[] {9, 9, 9};
    when(payloadCrypto.encrypt(any())).thenReturn(new EncryptedPayload(enc, nonce));
    when(emailJobRepository.tryCreatePending(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(false);

    EmailCommandProcessingOutcome outcome = service.process(message, rawPayload());

    assertEquals(COMPLETED, outcome);
    verify(emailSender, never()).sendHtml(any(), any(), any());
    verify(emailJobRepository, never()).markSent(any(), any());
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
    verify(emailJobRepository)
        .tryCreatePending(eq(eventId), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void process_unsupportedType_returnsCompletedWithoutPersistingOrSending() {
    EmailCommandMessage message =
        new EmailCommandMessage(
            "31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b",
            "2026-01-01T10:00:00Z",
            "corr-1",
            "user-1",
            "test@example.com",
            "123456",
            "EMAIL_VERIFICATION",
            "PASSWORD_RESET_REQUESTED");

    when(payloadCrypto.encrypt(any()))
        .thenReturn(new EncryptedPayload(new byte[] {1}, new byte[] {2}));

    EmailCommandProcessingOutcome outcome = service.process(message, rawPayload());

    assertEquals(COMPLETED, outcome);
    verify(emailJobRepository, never())
        .tryCreatePending(any(), any(), any(), any(), any(), any(), any(), any());
    verify(emailSender, never()).sendHtml(any(), any(), any());
    verify(emailJobRepository, never()).markSent(any(), any());
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
  }

  @Test
  void process_unsupportedTemplate_returnsCompletedWithoutPersistingOrSending() {
    EmailCommandMessage message =
        new EmailCommandMessage(
            "31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b",
            "2026-01-01T10:00:00Z",
            "corr-1",
            "user-1",
            "test@example.com",
            "123456",
            "WELCOME_EMAIL",
            "EMAIL_VERIFICATION_REQUESTED");

    when(payloadCrypto.encrypt(any()))
        .thenReturn(new EncryptedPayload(new byte[] {1}, new byte[] {2}));

    EmailCommandProcessingOutcome outcome = service.process(message, rawPayload());

    assertEquals(COMPLETED, outcome);
    verify(emailJobRepository, never())
        .tryCreatePending(any(), any(), any(), any(), any(), any(), any(), any());
    verify(emailSender, never()).sendHtml(any(), any(), any());
    verify(emailJobRepository, never()).markSent(any(), any());
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
  }

  @Test
  void process_renderOrSendFailure_marksFailedAndReturnsRecoverableStatePersisted() {
    EmailCommandMessage message = validMessage();
    UUID eventId = UUID.fromString(message.eventId());

    when(payloadCrypto.encrypt(any()))
        .thenReturn(new EncryptedPayload(new byte[] {1}, new byte[] {2}));
    when(emailJobRepository.tryCreatePending(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(true);
    when(templateRenderer.render(any(), any()))
        .thenThrow(new IllegalStateException("template exploded 123456"));

    EmailCommandProcessingOutcome outcome = service.process(message, rawPayload());

    assertEquals(RECOVERABLE_STATE_PERSISTED, outcome);
    verify(emailJobRepository, never()).markSent(any(), any());

    ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailJobRepository).markFailed(eq(eventId), errorCaptor.capture(), eq(FIXED_NOW));
    assertEquals("IllegalStateException: template exploded [REDACTED]", errorCaptor.getValue());
  }

  @Test
  void process_invalidEventId_throwsInvalidEmailCommandAndSkipsDependencies() {
    EmailCommandMessage message =
        new EmailCommandMessage(
            "invalid-uuid",
            "2026-01-01T10:00:00Z",
            "corr-1",
            "user-1",
            "test@example.com",
            "123456",
            "EMAIL_VERIFICATION",
            "EMAIL_VERIFICATION_REQUESTED");

    assertThrows(InvalidEmailCommandException.class, () -> service.process(message, rawPayload()));

    verify(payloadCrypto, never()).encrypt(any());
    verify(emailJobRepository, never())
        .tryCreatePending(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void process_encryptFailure_propagatesAndSkipsPersistenceAndSending() {
    EmailCommandMessage message = validMessage();
    when(payloadCrypto.encrypt(any())).thenThrow(new IllegalArgumentException("crypto-broken"));

    assertThrows(IllegalArgumentException.class, () -> service.process(message, rawPayload()));

    verify(emailJobRepository, never())
        .tryCreatePending(any(), any(), any(), any(), any(), any(), any(), any());
    verify(emailSender, never()).sendHtml(any(), any(), any());
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
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

  private String rawPayload() {
    return """
        {"eventId":"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b","type":"EMAIL_VERIFICATION_REQUESTED"}
        """;
  }
}
