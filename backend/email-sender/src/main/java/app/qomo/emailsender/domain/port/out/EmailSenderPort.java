package app.qomo.emailsender.domain.port.out;

public interface EmailSenderPort {

  void sendHtml(String to, String subject, String html);
}
