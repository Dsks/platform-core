package app.qomo.emailsender.infrastructure.adapter.out.mail;

import app.qomo.emailsender.domain.port.out.EmailSenderPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class SmtpEmailSenderAdapter implements EmailSenderPort {

  private final JavaMailSender javaMailSender;
  private final String fromEmail;

  public SmtpEmailSenderAdapter(JavaMailSender javaMailSender, String fromEmail) {
    this.javaMailSender = javaMailSender;
    this.fromEmail = fromEmail;
  }

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
      throw new IllegalStateException("Unable to build email message", exception);
    }
  }
}