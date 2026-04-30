package app.qomo.emailsender.infrastructure.adapter.in.kafka;

import app.qomo.emailsender.application.exception.InvalidEmailCommandException;
import app.qomo.emailsender.application.model.EmailCommandMessage;
import app.qomo.emailsender.application.observability.HashUtil;
import app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase;
import app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase.EmailCommandProcessingOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

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

  public EmailCommandsConsumer(HashUtil hashUtil, ObjectMapper objectMapper,
      ProcessEmailCommandUseCase processEmailCommandUseCase) {
    this.hashUtil = hashUtil;
    this.objectMapper = objectMapper;
    this.processEmailCommandUseCase = processEmailCommandUseCase;
  }

  @KafkaListener(
      topics = "${qomo.kafka.topics.email-commands:qomo.email.commands}",
      groupId = "${spring.kafka.consumer.group-id:qomo-email-sender}")
  public void consume(String payload, Acknowledgment ack) {
    String safePayload = payload == null ? "" : payload;
    String payloadSha = hashUtil.sha256Hex(safePayload);

    AckDecision decision = AckDecision.DO_NOT_ACK;

    EmailCommandMessage message;
    try {
      message = objectMapper.readValue(safePayload, EmailCommandMessage.class);
    } catch (JsonProcessingException ex) {
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
      EmailCommandProcessingOutcome outcome = processEmailCommandUseCase.process(message,
          safePayload);
      decision = ackDecisionFor(outcome);
    } catch (InvalidEmailCommandException exception) {
      log.warn(
          "email_command_invalid reason={} sha={} size={}",
          exception.getMessage(),
          payloadSha,
          safePayload.length());
      decision = AckDecision.ACK_DISCARD_INVALID;
    } catch (RuntimeException exception) {
      log.error(
          "email_command_processing_failed sha={} size={}",
          payloadSha,
          safePayload.length(),
          exception);
    }

    acknowledgeIfRequired(ack, decision);
  }

  private void acknowledgeIfRequired(Acknowledgment ack, AckDecision decision) {
    if (decision.shouldAck()) {
      ack.acknowledge();
    }
  }

  private AckDecision ackDecisionFor(EmailCommandProcessingOutcome outcome) {
    return switch (outcome) {
      case COMPLETED, RECOVERABLE_STATE_PERSISTED -> AckDecision.ACK_SAFE_STATE_REACHED;
    };
  }

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