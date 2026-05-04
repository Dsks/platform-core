package app.qomo.emailsender.application.port.out;

public interface EmailSenderPort {

  void sendHtml(String to, String subject, String html);
}
