package app.qomo.emailsender.application.model;

public record EmailCommandMessage(
    String eventId,
    String occurredAt,
    String correlationId,
    String userId,
    String toEmail,
    String verificationCode,
    String template,
    String type) {

}