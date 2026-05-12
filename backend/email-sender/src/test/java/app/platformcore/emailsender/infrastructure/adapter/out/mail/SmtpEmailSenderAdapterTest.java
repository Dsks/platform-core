package app.platformcore.emailsender.infrastructure.adapter.out.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderAdapterTest {

  @Mock private JavaMailSender javaMailSender;

  private SmtpEmailSenderAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SmtpEmailSenderAdapter(javaMailSender, "noreply@platformcore.app");
  }

  @Test
  void sendHtml_validInput_buildsMimeMessageAndDelegatesToMailSender() throws Exception {
    MimeMessage mimeMessage = new MimeMessage((Session) null);
    when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

    adapter.sendHtml("user@example.com", "Welcome", "<h1>Hello</h1>");

    verify(javaMailSender).send(same(mimeMessage));
    Address[] from = mimeMessage.getFrom();
    assertNotNull(from);
    assertEquals(1, from.length);
    assertEquals("noreply@platformcore.app", from[0].toString());
    assertEquals("Welcome", mimeMessage.getSubject());
    assertEquals(
        "user@example.com", mimeMessage.getRecipients(Message.RecipientType.TO)[0].toString());
    assertEquals("<h1>Hello</h1>", mimeMessage.getContent().toString().trim());
  }

  @Test
  void sendHtml_invalidConfiguredFromEmail_wrapsMessagingFailureAsIllegalState() {
    SmtpEmailSenderAdapter adapterWithInvalidFrom =
        new SmtpEmailSenderAdapter(javaMailSender, "bad\r\naddress");
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> adapterWithInvalidFrom.sendHtml("user@example.com", "Welcome", "<h1>Hello</h1>"));

    assertEquals("Unable to build email message", exception.getMessage());
  }
}
