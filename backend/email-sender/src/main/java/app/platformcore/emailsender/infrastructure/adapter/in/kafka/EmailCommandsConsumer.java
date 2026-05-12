package app.platformcore.emailsender.infrastructure.adapter.in.kafka;

import app.platformcore.emailsender.application.command.EmailCommandMessage;
import app.platformcore.emailsender.application.exception.InvalidEmailCommandException;
import app.platformcore.emailsender.application.observability.HashUtil;
import app.platformcore.emailsender.application.port.in.ProcessEmailCommandUseCase;
import app.platformcore.emailsender.application.port.in.ProcessEmailCommandUseCase.EmailCommandProcessingOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for email command records received from the configured email-commands topic.
 *
 * <p>The listener reads raw String values from {@code platformcore.kafka.topics.email-commands},
 * defaulting to {@code platformcore.email.commands}, translates the payload into the application
 * command command, and delegates accepted messages to {@link ProcessEmailCommandUseCase}. Transport
 * concerns stay in this adapter: JSON parsing, lightweight schema checks, manual acknowledgement
 * decisions, and safe logging for rejected or failed records.
 *
 * <p>Invalid payloads are acknowledged and discarded because the adapter can prove that re-delivery
 * will not make them parseable or valid. Unexpected runtime failures are left unacknowledged so the
 * Kafka container can apply its configured retry or redelivery behavior. Logs identify payloads by
 * a SHA-256 digest and payload length only; raw payload contents, email addresses, and verification
 * codes are intentionally not emitted here.
 */
@Component
public class EmailCommandsConsumer {

  private static final Logger log = LoggerFactory.getLogger(EmailCommandsConsumer.class);
  private static final String EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";
  private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
  private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern SIX_DIGIT_CODE = Pattern.compile("^\\d{6}$");

  private final HashUtil hashUtil;
  private final ObjectMapper objectMapper;
  private final ProcessEmailCommandUseCase processEmailCommandUseCase;

  /**
   * Creates the adapter with infrastructure collaborators needed for safe payload handling and
   * application delegation.
   *
   * @param hashUtil creates stable payload fingerprints for logs without exposing message content
   * @param objectMapper deserializes the Kafka value into the application command shape
   * @param processEmailCommandUseCase application use case invoked after adapter-level validation
   */
  public EmailCommandsConsumer(
      HashUtil hashUtil,
      ObjectMapper objectMapper,
      ProcessEmailCommandUseCase processEmailCommandUseCase) {
    this.hashUtil = hashUtil;
    this.objectMapper = objectMapper;
    this.processEmailCommandUseCase = processEmailCommandUseCase;
  }

  /**
   * Handles one Kafka value from the email-commands topic using manual acknowledgement.
   *
   * <p>The expected payload is a JSON object compatible with {@link EmailCommandMessage}. Before
   * calling the use case, the adapter requires {@code eventId}, {@code type}, {@code template},
   * {@code toEmail}, and {@code verificationCode} to be present and non-blank. For the supported
   * verification command pair ({@code EMAIL_VERIFICATION_REQUESTED} / {@code EMAIL_VERIFICATION})
   * it also checks that the recipient has a simple email shape and the verification code contains
   * six digits.
   *
   * <p>Malformed JSON, adapter validation failures, and {@link InvalidEmailCommandException} from
   * the use case are treated as non-recoverable input problems: they are logged with a payload
   * digest and acknowledged to discard the record. Successful processing and recoverable state
   * persisted by the use case are also acknowledged. Other {@link RuntimeException}s remain
   * unacknowledged to preserve broker retry/redelivery semantics.
   *
   * @param payload raw Kafka record value; {@code null} is treated as an empty payload for hashing
   *     and parsing
   * @param ack manual acknowledgement supplied by the Spring Kafka listener container
   */
  @KafkaListener(
      topics = "${platformcore.kafka.topics.email-commands:platformcore.email.commands}",
      groupId = "${spring.kafka.consumer.group-id:platformcore-email-sender}")
  public void consume(String payload, Acknowledgment ack) {
    // Normalize null records into the invalid-payload path while keeping logs fingerprint-only.
    String safePayload = payload == null ? "" : payload;
    String payloadSha = hashUtil.sha256Hex(safePayload);

    AckDecision decision = AckDecision.DO_NOT_ACK;

    EmailCommandMessage message;
    try {
      message = objectMapper.readValue(safePayload, EmailCommandMessage.class);
    } catch (JsonProcessingException ex) {
      // Malformed JSON is terminal input; redelivery would only repeat the same parse failure.
      log.warn(
          "email_command_invalid reason={} sha={} size={}",
          "json_parse_error",
          payloadSha,
          safePayload.length());
      decision = AckDecision.ACK_DISCARD_INVALID;
      acknowledgeIfRequired(ack, decision);
      return;
    }

    String invalidReason = firstValidationError(message);
    if (invalidReason != null) {
      log.warn(
          "email_command_invalid reason={} sha={} size={}",
          invalidReason,
          payloadSha,
          safePayload.length());
      decision = AckDecision.ACK_DISCARD_INVALID;
      acknowledgeIfRequired(ack, decision);
      return;
    }

    try {
      EmailCommandProcessingOutcome outcome =
          processEmailCommandUseCase.process(message, safePayload);
      decision = ackDecisionFor(outcome);
    } catch (InvalidEmailCommandException exception) {
      log.warn(
          "email_command_invalid reason={} sha={} size={}",
          exception.getMessage(),
          payloadSha,
          safePayload.length());
      decision = AckDecision.ACK_DISCARD_INVALID;
    } catch (RuntimeException exception) {
      // Leave the offset uncommitted so Kafka/container retry policy handles unexpected failures.
      log.error(
          "email_command_processing_failed sha={} size={}",
          payloadSha,
          safePayload.length(),
          exception);
    }

    acknowledgeIfRequired(ack, decision);
  }

  /**
   * Applies the selected acknowledgement policy in one place so every exit path shares the same
   * offset decision.
   */
  private void acknowledgeIfRequired(Acknowledgment ack, AckDecision decision) {
    if (decision.shouldAck()) {
      ack.acknowledge();
    }
  }

  /**
   * Translates application-visible outcomes into broker offset handling.
   *
   * <p>{@link EmailCommandProcessingOutcome#RECOVERABLE_STATE_PERSISTED} is safe to acknowledge
   * because the use case has already stored the information needed by the retry worker.
   */
  private AckDecision ackDecisionFor(EmailCommandProcessingOutcome outcome) {
    return switch (outcome) {
      case COMPLETED, RECOVERABLE_STATE_PERSISTED -> AckDecision.ACK_SAFE_STATE_REACHED;
    };
  }

  /**
   * Internal acknowledgement policy for the listener.
   *
   * <p>The names encode why an offset can be committed, or why it must be left pending for
   * container-level retry/redelivery.
   */
  private enum AckDecision {
    ACK_SAFE_STATE_REACHED(true),
    ACK_DISCARD_INVALID(true),
    DO_NOT_ACK(false);

    private final boolean shouldAck;

    AckDecision(boolean shouldAck) {
      this.shouldAck = shouldAck;
    }

    boolean shouldAck() {
      return shouldAck;
    }
  }

  /**
   * Performs adapter-level checks that classify a record before it reaches the application use
   * case.
   *
   * <p>The method returns the first stable reason code so invalid input can be acknowledged and
   * discarded without logging field values. The stricter email and code checks intentionally apply
   * only to the supported verification command; unsupported type/template combinations are left to
   * the use case after the mandatory fields have been proven present.
   */
  private String firstValidationError(EmailCommandMessage message) {
    if (isBlank(message.eventId())) {
      return "eventId_blank";
    }
    if (!isValidUuid(message.eventId())) {
      return "eventId_invalid_uuid";
    }
    if (isBlank(message.type())) {
      return "type_blank";
    }
    if (isBlank(message.template())) {
      return "template_blank";
    }
    if (isBlank(message.toEmail())) {
      return "toEmail_blank";
    }
    if (isBlank(message.verificationCode())) {
      return "verificationCode_blank";
    }
    // Format checks are intentionally limited to the command pair this adapter understands.
    if (isSupportedVerificationCommand(message)
        && !SIMPLE_EMAIL.matcher(message.toEmail().trim()).matches()) {
      return "toEmail_invalid_format";
    }
    if (isSupportedVerificationCommand(message)
        && !SIX_DIGIT_CODE.matcher(message.verificationCode().trim()).matches()) {
      return "verificationCode_invalid_format";
    }
    return null;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * Identifies the command pair for which this adapter can safely enforce recipient and code format
   * before application processing.
   */
  private boolean isSupportedVerificationCommand(EmailCommandMessage message) {
    return EMAIL_VERIFICATION_REQUESTED.equals(message.type())
        && EMAIL_VERIFICATION.equals(message.template());
  }

  private boolean isValidUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
