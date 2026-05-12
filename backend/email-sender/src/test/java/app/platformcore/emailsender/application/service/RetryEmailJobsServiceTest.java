package app.platformcore.emailsender.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.platformcore.emailsender.application.port.out.ClockPort;
import app.platformcore.emailsender.application.port.out.EmailJobRepositoryPort;
import app.platformcore.emailsender.application.port.out.EmailSenderPort;
import app.platformcore.emailsender.application.port.out.EmailTemplateRendererPort;
import app.platformcore.emailsender.application.port.out.PayloadCryptoPort;
import app.platformcore.emailsender.application.port.out.PayloadCryptoPort.EncryptedPayload;
import app.platformcore.emailsender.domain.model.EmailJobRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetryEmailJobsServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-02-10T12:00:00Z");
  private static final int MAX_ATTEMPTS = 3;
  private static final int BATCH_SIZE = 50;
  private static final int MIN_AGE_SECONDS = 120;

  @Mock private EmailJobRepositoryPort emailJobRepository;
  @Mock private PayloadCryptoPort payloadCrypto;
  @Mock private EmailTemplateRendererPort templateRenderer;
  @Mock private EmailSenderPort emailSender;

  private RetryEmailJobsService service;

  @BeforeEach
  void setUp() {
    ClockPort fixedClock = () -> FIXED_NOW;

    service =
        new RetryEmailJobsService(
            emailJobRepository,
            payloadCrypto,
            templateRenderer,
            emailSender,
            new ObjectMapper(),
            fixedClock,
            BATCH_SIZE,
            MAX_ATTEMPTS,
            MIN_AGE_SECONDS,
            "PlatformCore",
            "Verify your email");
  }

  @Test
  void retryFailedJobs_whenNoCandidates_onlyClaimsAndStops() {
    when(emailJobRepository.claimRetryCandidates(
            eq(MAX_ATTEMPTS),
            eq(FIXED_NOW.minusSeconds(MIN_AGE_SECONDS)),
            eq(BATCH_SIZE),
            eq(FIXED_NOW)))
        .thenReturn(List.of());

    service.retryFailedJobs();

    verify(emailJobRepository)
        .claimRetryCandidates(
            eq(MAX_ATTEMPTS),
            eq(FIXED_NOW.minusSeconds(MIN_AGE_SECONDS)),
            eq(BATCH_SIZE),
            eq(FIXED_NOW));
    verify(payloadCrypto, never()).decrypt(any());
    verify(emailSender, never()).sendHtml(any(), any(), any());
    verify(emailJobRepository, never()).markSent(any(), any());
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
    verify(emailJobRepository, never()).markDead(any(), any(), any());
  }

  @Test
  void retryFailedJobs_withValidRetryableJob_sendsAndMarksSent() {
    EmailJobRecord candidate = job(1);
    when(emailJobRepository.claimRetryCandidates(
            anyInt(), any(Instant.class), anyInt(), any(Instant.class)))
        .thenReturn(List.of(candidate));
    when(payloadCrypto.decrypt(any())).thenReturn(validMessagePayload());
    when(templateRenderer.render(eq("EMAIL_VERIFICATION"), any())).thenReturn("<html>ok</html>");

    service.retryFailedJobs();

    verify(payloadCrypto)
        .decrypt(eq(new EncryptedPayload(candidate.payloadEnc(), candidate.payloadNonce())));
    verify(templateRenderer)
        .render(
            eq("EMAIL_VERIFICATION"),
            eq(
                java.util.Map.of(
                    "verificationCode", "123456",
                    "appName", "PlatformCore")));
    verify(emailSender)
        .sendHtml(eq("test@example.com"), eq("Verify your email"), eq("<html>ok</html>"));
    verify(emailJobRepository).markSent(eq(candidate.eventId()), eq(FIXED_NOW));
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
    verify(emailJobRepository, never()).markDead(any(), any(), any());
  }

  @Test
  void retryFailedJobs_whenDecryptFailsAndStillUnderLimit_marksFailed() {
    EmailJobRecord candidate = job(1);
    when(emailJobRepository.claimRetryCandidates(
            anyInt(), any(Instant.class), anyInt(), any(Instant.class)))
        .thenReturn(List.of(candidate));
    when(payloadCrypto.decrypt(any())).thenThrow(new IllegalArgumentException("decrypt boom"));

    service.retryFailedJobs();

    verify(emailJobRepository)
        .markFailed(
            eq(candidate.eventId()), eq("IllegalArgumentException: decrypt boom"), eq(FIXED_NOW));
    verify(emailJobRepository, never()).markDead(any(), any(), any());
    verify(emailJobRepository, never()).markSent(any(), any());
  }

  @Test
  void retryFailedJobs_whenPayloadCannotBeReconstructedAndStillUnderLimit_marksFailed() {
    EmailJobRecord candidate = job(1);
    when(emailJobRepository.claimRetryCandidates(
            anyInt(), any(Instant.class), anyInt(), any(Instant.class)))
        .thenReturn(List.of(candidate));
    when(payloadCrypto.decrypt(any())).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

    service.retryFailedJobs();

    verify(emailJobRepository).markFailed(eq(candidate.eventId()), any(), eq(FIXED_NOW));
    verify(emailJobRepository, never()).markDead(any(), any(), any());
    verify(templateRenderer, never()).render(any(), any());
    verify(emailSender, never()).sendHtml(any(), any(), any());
  }

  @Test
  void retryFailedJobs_whenRenderFailsAndStillUnderLimit_marksFailed() {
    EmailJobRecord candidate = job(0);
    when(emailJobRepository.claimRetryCandidates(
            anyInt(), any(Instant.class), anyInt(), any(Instant.class)))
        .thenReturn(List.of(candidate));
    when(payloadCrypto.decrypt(any())).thenReturn(validMessagePayload());
    when(templateRenderer.render(any(), any()))
        .thenThrow(new IllegalStateException("render crash"));

    service.retryFailedJobs();

    verify(emailJobRepository)
        .markFailed(
            eq(candidate.eventId()), eq("IllegalStateException: render crash"), eq(FIXED_NOW));
    verify(emailJobRepository, never()).markDead(any(), any(), any());
    verify(emailSender, never()).sendHtml(any(), any(), any());
  }

  @Test
  void retryFailedJobs_whenSendFailsAndStillUnderLimit_marksFailed() {
    EmailJobRecord candidate = job(0);
    when(emailJobRepository.claimRetryCandidates(
            anyInt(), any(Instant.class), anyInt(), any(Instant.class)))
        .thenReturn(List.of(candidate));
    when(payloadCrypto.decrypt(any())).thenReturn(validMessagePayload());
    when(templateRenderer.render(any(), any())).thenReturn("<html>ok</html>");
    doThrow(new RuntimeException("smtp down")).when(emailSender).sendHtml(any(), any(), any());

    service.retryFailedJobs();

    verify(emailJobRepository)
        .markFailed(eq(candidate.eventId()), eq("RuntimeException: smtp down"), eq(FIXED_NOW));
    verify(emailJobRepository, never()).markDead(any(), any(), any());
    verify(emailJobRepository, never()).markSent(any(), any());
  }

  @Test
  void retryFailedJobs_whenFailureHitsMaxAttempts_marksDead() {
    EmailJobRecord candidate = job(MAX_ATTEMPTS - 1);
    when(emailJobRepository.claimRetryCandidates(
            anyInt(), any(Instant.class), anyInt(), any(Instant.class)))
        .thenReturn(List.of(candidate));
    when(payloadCrypto.decrypt(any())).thenThrow(new RuntimeException("decrypt final failure"));

    service.retryFailedJobs();

    verify(emailJobRepository)
        .markDead(eq(candidate.eventId()), eq("max_attempts_exceeded"), eq(FIXED_NOW));
    verify(emailJobRepository, never()).markFailed(any(), any(), any());
    verify(emailJobRepository, never()).markSent(any(), any());
  }

  private EmailJobRecord job(int attempts) {
    return new EmailJobRecord(
        UUID.fromString("31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b"),
        "corr-1",
        "EMAIL_VERIFICATION_REQUESTED",
        "EMAIL_VERIFICATION",
        "email-fp",
        new byte[] {1, 2, 3},
        new byte[] {9, 9, 9},
        attempts);
  }

  private byte[] validMessagePayload() {
    return """
        {"eventId":"31e5fb30-1d89-44fd-8ed0-6417ea3f9f5b","occurredAt":"2026-01-01T10:00:00Z","correlationId":"corr-1","userId":"user-1","toEmail":"test@example.com","verificationCode":"123456","template":"EMAIL_VERIFICATION","type":"EMAIL_VERIFICATION_REQUESTED"}
        """
        .getBytes(StandardCharsets.UTF_8);
  }
}
