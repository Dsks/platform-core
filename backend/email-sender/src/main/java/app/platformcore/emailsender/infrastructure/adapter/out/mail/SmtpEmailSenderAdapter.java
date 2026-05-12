package app.platformcore.emailsender.infrastructure.adapter.out.mail;

import app.platformcore.emailsender.application.port.out.EmailSenderPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * {@link EmailSenderPort} implementation that crosses the boundary to the configured SMTP/email
 * provider through Spring's {@link JavaMailSender}.
 *
 * <p>The application layer is expected to provide the final recipient address, subject, and
 * rendered HTML body. This adapter hides MIME message construction, UTF-8 helper configuration,
 * configured sender address application, and provider transport details. Its observable side effect
 * is handing the message to the configured mail sender.
 *
 * <p>Message-construction failures from Jakarta Mail are wrapped in {@link IllegalStateException}.
 * Runtime transport failures raised by {@link JavaMailSender} are allowed to propagate.
 */
public class SmtpEmailSenderAdapter implements EmailSenderPort {

  private final JavaMailSender javaMailSender;
  private final String fromEmail;

  public SmtpEmailSenderAdapter(JavaMailSender javaMailSender, String fromEmail) {
    this.javaMailSender = javaMailSender;
    this.fromEmail = fromEmail;
  }

  /**
   * Builds a UTF-8 HTML MIME message and delegates delivery to the configured mail sender.
   *
   * @param to recipient address prepared by the application layer
   * @param subject subject line selected by the use case
   * @param html rendered HTML content; this adapter does not render or sanitize it
   * @throws IllegalStateException when the MIME message cannot be constructed
   */
  @Override
  public void sendHtml(String to, String subject, String html) {
    try {
      MimeMessage mimeMessage = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
      helper.setFrom(fromEmail);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(html, true);
      javaMailSender.send(mimeMessage);
    } catch (MessagingException exception) {
      // Only MIME construction is normalized; transport/runtime failures keep mail-sender
      // semantics.
      throw new IllegalStateException("Unable to build email message", exception);
    }
  }
}
