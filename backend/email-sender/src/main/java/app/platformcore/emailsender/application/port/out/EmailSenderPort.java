package app.platformcore.emailsender.application.port.out;

/**
 * Boundary between application use cases and the configured email delivery provider or SMTP server.
 *
 * <p>Implementations normally belong to infrastructure and hide provider APIs, protocol details,
 * authentication, connection handling, and message formatting concerns. Application services pass a
 * recipient, subject, and HTML body that have already been selected or rendered by the application;
 * this port performs the delivery side effect and does not expose provider-specific response
 * details to the use case.
 */
public interface EmailSenderPort {

  /**
   * Delivers an HTML email using the configured provider.
   *
   * @param to recipient address prepared by the application layer
   * @param subject subject line selected by the use case
   * @param html rendered HTML body ready for delivery
   */
  void sendHtml(String to, String subject, String html);
}
