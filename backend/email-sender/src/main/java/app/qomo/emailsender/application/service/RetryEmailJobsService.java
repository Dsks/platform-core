package app.qomo.emailsender.application.service;

import app.qomo.emailsender.application.command.EmailCommandMessage;
import app.qomo.emailsender.application.port.in.RetryEmailJobsUseCase;
import app.qomo.emailsender.application.port.out.ClockPort;
import app.qomo.emailsender.application.port.out.EmailJobRepositoryPort;
import app.qomo.emailsender.application.port.out.EmailSenderPort;
import app.qomo.emailsender.application.port.out.EmailTemplateRendererPort;
import app.qomo.emailsender.application.port.out.PayloadCryptoPort;
import app.qomo.emailsender.application.util.ErrorSanitizer;
import app.qomo.emailsender.domain.model.EmailJobRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryEmailJobsService implements RetryEmailJobsUseCase {

  private static final Logger log = LoggerFactory.getLogger(RetryEmailJobsService.class);

  private final EmailJobRepositoryPort emailJobRepository;
  private final PayloadCryptoPort payloadCrypto;
  private final EmailTemplateRendererPort templateRenderer;
  private final EmailSenderPort emailSender;
  private final ObjectMapper objectMapper;
  private final ClockPort clock;
  private final int batchSize;
  private final int maxAttempts;
  private final int minAgeSeconds;
  private final String appName;
  private final String verificationSubject;

  public RetryEmailJobsService(
      EmailJobRepositoryPort emailJobRepository,
      PayloadCryptoPort payloadCrypto,
      EmailTemplateRendererPort templateRenderer,
      EmailSenderPort emailSender,
      ObjectMapper objectMapper,
      ClockPort clock,
      int batchSize,
      int maxAttempts,
      int minAgeSeconds,
      String appName,
      String verificationSubject) {
    this.emailJobRepository = emailJobRepository;
    this.payloadCrypto = payloadCrypto;
    this.templateRenderer = templateRenderer;
    this.emailSender = emailSender;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.minAgeSeconds = minAgeSeconds;
    this.appName = appName;
    this.verificationSubject = verificationSubject;
  }

  @Override
  public void retryFailedJobs() {
    Instant now = clock.now();
    Instant olderThan = now.minusSeconds(minAgeSeconds);

    List<EmailJobRecord> claimedJobs =
        emailJobRepository.claimRetryCandidates(maxAttempts, olderThan, batchSize, now);

    log.info(
        "retry_batch_size claimed={} maxAttempts={} olderThan={} batchSize={} minAgeSeconds={}",
        claimedJobs.size(),
        maxAttempts,
        olderThan,
        batchSize,
        minAgeSeconds);

    claimedJobs.forEach(candidate -> retryJob(candidate, now));
  }

  private void retryJob(EmailJobRecord job, Instant now) {
    int currentAttempt = job.attempts() + 1;
    log.info(
        "retry_attempt eventId={} attempt={} type={} template={} to_fp={}",
        job.eventId(),
        currentAttempt,
        job.type(),
        job.template(),
        job.toEmailFp());

    try {
      byte[] decrypted =
          payloadCrypto.decrypt(
              new PayloadCryptoPort.EncryptedPayload(job.payloadEnc(), job.payloadNonce()));
      EmailCommandMessage message =
          objectMapper.readValue(
              new String(decrypted, StandardCharsets.UTF_8), EmailCommandMessage.class);

      String html =
          templateRenderer.render(
              message.template(),
              Map.of("verificationCode", message.verificationCode(), "appName", appName));
      emailSender.sendHtml(message.toEmail(), verificationSubject, html);
      emailJobRepository.markSent(job.eventId(), now);
    } catch (Exception exception) {
      String sanitizedError = ErrorSanitizer.sanitize(toRuntimeException(exception), null);
      if (currentAttempt >= maxAttempts) {
        emailJobRepository.markDead(job.eventId(), "max_attempts_exceeded", now);
      } else {
        emailJobRepository.markFailed(job.eventId(), sanitizedError, now);
      }
      log.warn(
          "retry_failed eventId={} attempt={} type={} template={} to_fp={}",
          job.eventId(),
          currentAttempt,
          job.type(),
          job.template(),
          job.toEmailFp());
    }
  }

  private RuntimeException toRuntimeException(Exception exception) {
    if (exception instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new RuntimeException(exception);
  }
}
