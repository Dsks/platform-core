package app.qomo.emailsender.application.service;

import app.qomo.emailsender.application.command.EmailCommandMessage;
import app.qomo.emailsender.application.exception.InvalidEmailCommandException;
import app.qomo.emailsender.application.observability.PiiUtil;
import app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase;
import app.qomo.emailsender.application.port.out.ClockPort;
import app.qomo.emailsender.application.port.out.EmailJobRepositoryPort;
import app.qomo.emailsender.application.port.out.EmailSenderPort;
import app.qomo.emailsender.application.port.out.EmailTemplateRendererPort;
import app.qomo.emailsender.application.port.out.PayloadCryptoPort;
import app.qomo.emailsender.application.util.ErrorSanitizer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      String sanitizedError = ErrorSanitizer.sanitize(exception, message.verificationCode());
      emailJobRepository.markFailed(eventId, sanitizedError, now);
      log.warn(
          "email_command_send_failed eventId={} type={} template={} emailFingerprint={} error={}",
          eventId,
          message.type(),
          message.template(),
          emailFingerprint,
          sanitizedError);
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

  private UUID parseEventId(String eventId) {
    try {
      return UUID.fromString(eventId);
    } catch (RuntimeException exception) {
      throw new InvalidEmailCommandException("eventId_invalid_uuid");
    }
  }
}
