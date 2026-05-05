package app.qomo.apiusers.application.command;

import java.time.Instant;

/**
 * Application command serialized into the outbox to request an email verification message.
 *
 * <p>Created by {@code IssueEmailVerificationService} after issuing a new EMAIL_VERIFICATION token
 * and consumed downstream by the email delivery pipeline. The command intentionally carries the
 * destination email and plaintext verification code so the email worker can send them; both are
 * sensitive and must not be written to logs. The users service stores only the hashed verification
 * code before publishing this command.
 *
 * <p>The event id should be unique per outbound command, the correlation id should preserve the
 * originating registration, login, or resend flow, and the user id is the aggregate identifier also
 * used in outbox metadata. The type and template are expected to match the constants in this
 * record.
 *
 * @param eventId unique event id for idempotency and tracing of this email command
 * @param occurredAt application time when the verification email command was created
 * @param correlationId upstream request or correlation id propagated to the email pipeline
 * @param userId user aggregate id included in the outbox event key and payload
 * @param toEmail destination email address; PII, avoid raw logs
 * @param verificationCode plaintext OTP sent to the user; secret token, never log
 * @param template email template key expected by email delivery consumers
 * @param type command/event type; expected to be EMAIL_VERIFICATION_REQUESTED
 */
public record EmailVerificationRequestedCommand(
    String eventId,
    Instant occurredAt,
    String correlationId,
    String userId,
    String toEmail,
    String verificationCode,
    String template,
    String type) {

  public static final String EMAIL_VERIFICATION_REQUESTED = "EMAIL_VERIFICATION_REQUESTED";
  public static final String EMAIL_VERIFICATION_TEMPLATE = "EMAIL_VERIFICATION";

  public static EmailVerificationRequestedCommand emailVerification(
      String eventId,
      Instant occurredAt,
      String correlationId,
      String userId,
      String toEmail,
      String verificationCode) {
    return new EmailVerificationRequestedCommand(
        eventId,
        occurredAt,
        correlationId,
        userId,
        toEmail,
        verificationCode,
        EMAIL_VERIFICATION_TEMPLATE,
        EMAIL_VERIFICATION_REQUESTED);
  }
}
