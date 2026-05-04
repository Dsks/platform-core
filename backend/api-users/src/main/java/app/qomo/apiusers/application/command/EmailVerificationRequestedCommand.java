package app.qomo.apiusers.application.command;

import java.time.Instant;

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
