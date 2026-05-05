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

/**
 * Application service for retrying email jobs that were previously left in a failed recoverable
 * state.
 *
 * <p>The service coordinates a retry sweep by asking the repository to claim eligible jobs, then
 * decrypting the stored command payload, rebuilding the command, rendering the verification
 * template, attempting email delivery, and recording the resulting state. Eligibility is expressed
 * to the repository through the configured maximum attempts, minimum job age, and batch size; SQL
 * selection and locking details remain outside this service.
 *
 * <p>Observable side effects include retry claiming, email delivery attempts, sent/failed/dead
 * state transitions, and structured logs. Scheduling, repository implementation details, payload
 * crypto mechanics, template storage, and mail transport details are delegated to the corresponding
 * adapters or outbound ports.
 */
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

  /**
   * Runs one synchronous retry sweep for jobs that satisfy the configured retry window.
   *
   * <p>The method computes the age cutoff from the current clock time, claims at most the
   * configured batch size, and attempts each claimed job. Individual job processing may deliver an
   * email, mark the job sent, keep it failed for a later sweep, or mark it dead when the attempt
   * limit has been reached.
   */
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

  /**
   * Attempts delivery for one claimed job and persists the next state derived from the outcome.
   *
   * <p>The attempt number is derived from the persisted count. Failures while decrypting,
   * deserializing, rendering, sending, or marking the job sent are sanitized before persistence.
   * Once the configured attempt limit is reached, the job is moved to the dead state instead of
   * being left eligible for future retries.
   */
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
        // The max-attempts boundary is terminal for retry eligibility.
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

  /**
   * Adapts checked failures to the sanitizer contract while preserving the original exception as
   * the cause.
   */
  private RuntimeException toRuntimeException(Exception exception) {
    if (exception instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new RuntimeException(exception);
  }
}
