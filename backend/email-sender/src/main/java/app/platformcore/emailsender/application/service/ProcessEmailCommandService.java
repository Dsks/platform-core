package app.platformcore.emailsender.application.service;

import app.platformcore.emailsender.application.command.EmailCommandMessage;
import app.platformcore.emailsender.application.exception.InvalidEmailCommandException;
import app.platformcore.emailsender.application.observability.PiiUtil;
import app.platformcore.emailsender.application.port.in.ProcessEmailCommandUseCase;
import app.platformcore.emailsender.application.port.out.ClockPort;
import app.platformcore.emailsender.application.port.out.EmailJobRepositoryPort;
import app.platformcore.emailsender.application.port.out.EmailSenderPort;
import app.platformcore.emailsender.application.port.out.EmailTemplateRendererPort;
import app.platformcore.emailsender.application.port.out.PayloadCryptoPort;
import app.platformcore.emailsender.application.util.ErrorSanitizer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service for processing email command messages at the email-sender boundary.
 *
 * <p>The service coordinates the inbound command use case with outbound ports for encrypted job
 * persistence, template rendering, email delivery, and time. For supported verification commands it
 * stores a recoverable job before attempting delivery, deduplicates through the repository by event
 * id, renders the verification template, sends the email, and records the resulting job state.
 *
 * <p>Observable side effects include encrypted payload persistence, email delivery attempts,
 * sent/failed state transitions, and structured logs. Unsupported command type/template
 * combinations and duplicate event ids are treated as completed work. Transport parsing, broker
 * acknowledgement, template storage, encryption details, and repository implementation details are
 * outside this service.
 */
public class ProcessEmailCommandService implements ProcessEmailCommandUseCase {

  private static final Logger log = LoggerFactory.getLogger(ProcessEmailCommandService.class);

  private static final String EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";
  private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

  private final EmailTemplateRendererPort templateRenderer;
  private final EmailSenderPort emailSender;
  private final EmailJobRepositoryPort emailJobRepository;
  private final PayloadCryptoPort payloadCrypto;
  private final ClockPort clock;
  private final String appName;
  private final String verificationSubject;

  public ProcessEmailCommandService(
      EmailTemplateRendererPort templateRenderer,
      EmailSenderPort emailSender,
      EmailJobRepositoryPort emailJobRepository,
      PayloadCryptoPort payloadCrypto,
      ClockPort clock,
      String appName,
      String verificationSubject) {
    this.templateRenderer = templateRenderer;
    this.emailSender = emailSender;
    this.emailJobRepository = emailJobRepository;
    this.payloadCrypto = payloadCrypto;
    this.clock = clock;
    this.appName = appName;
    this.verificationSubject = verificationSubject;
  }

  /**
   * Processes a parsed command and returns the application outcome that an inbound adapter can use
   * without depending on repository or delivery details.
   *
   * <p>A valid verification command is persisted as an encrypted pending job before delivery is
   * attempted, which lets a send failure be retried later from stored state. If the event id
   * already exists, the command is considered a duplicate and no email is sent. If immediate
   * delivery fails after the job is created, the job is marked failed with a sanitized error and
   * the method reports recoverable state.
   *
   * @param message parsed application command to process
   * @param rawPayload original command payload to encrypt and persist for recovery
   * @return {@link EmailCommandProcessingOutcome#COMPLETED} for ignored, duplicate, or successfully
   *     sent commands; {@link EmailCommandProcessingOutcome#RECOVERABLE_STATE_PERSISTED} when a
   *     send failure has been recorded for retry
   * @throws InvalidEmailCommandException when the command event id is not a UUID
   */
  @Override
  public EmailCommandProcessingOutcome process(EmailCommandMessage message, String rawPayload) {
    UUID eventId = parseEventId(message.eventId());
    Instant now = clock.now();
    String emailFingerprint = PiiUtil.emailFingerprint(message.toEmail());
    var encrypted = payloadCrypto.encrypt(rawPayload.getBytes(StandardCharsets.UTF_8));

    if (!EMAIL_VERIFICATION_REQUESTED.equals(message.type())
        || !EMAIL_VERIFICATION.equals(message.template())) {
      log.info(
          "email_command_ignored eventId={} type={} template={} emailFingerprint={}",
          eventId,
          message.type(),
          message.template(),
          emailFingerprint);
      return EmailCommandProcessingOutcome.COMPLETED;
    }

    // Create durable retry state before delivery so a later broker ack cannot lose the email job.
    boolean created =
        emailJobRepository.tryCreatePending(
            eventId,
            message.correlationId(),
            message.type(),
            message.template(),
            emailFingerprint,
            encrypted.ciphertext(),
            encrypted.nonce(),
            now);

    if (!created) {
      // Repository-level uniqueness is the idempotency guard for re-delivered commands.
      log.info(
          "duplicate_event eventId={} type={} template={} emailFingerprint={}",
          eventId,
          message.type(),
          message.template(),
          emailFingerprint);
      return EmailCommandProcessingOutcome.COMPLETED;
    }

    try {
      sendVerificationEmail(message);
      emailJobRepository.markSent(eventId, now);
      log.info(
          "email_command_sent eventId={} type={} template={} emailFingerprint={}",
          eventId,
          message.type(),
          message.template(),
          emailFingerprint);
      return EmailCommandProcessingOutcome.COMPLETED;
    } catch (RuntimeException exception) {
      // Redact the one-time code before the failure reason is persisted or emitted.
      String sanitizedError = ErrorSanitizer.sanitize(exception, message.verificationCode());
      emailJobRepository.markFailed(eventId, sanitizedError, now);
      log.warn(
          "email_command_send_failed eventId={} type={} template={} emailFingerprint={} error={}",
          eventId,
          message.type(),
          message.template(),
          emailFingerprint,
          sanitizedError);
      // FAILED is now durable, so the retry sweep owns future delivery attempts.
      return EmailCommandProcessingOutcome.RECOVERABLE_STATE_PERSISTED;
    }
  }

  private void sendVerificationEmail(EmailCommandMessage message) {
    String html =
        templateRenderer.render(
            message.template(),
            Map.of("verificationCode", message.verificationCode(), "appName", appName));
    emailSender.sendHtml(message.toEmail(), verificationSubject, html);
  }

  /**
   * Classifies malformed event identifiers as invalid commands, keeping them out of the recoverable
   * send-failure path.
   */
  private UUID parseEventId(String eventId) {
    try {
      return UUID.fromString(eventId);
    } catch (RuntimeException exception) {
      throw new InvalidEmailCommandException("eventId_invalid_uuid");
    }
  }
}
