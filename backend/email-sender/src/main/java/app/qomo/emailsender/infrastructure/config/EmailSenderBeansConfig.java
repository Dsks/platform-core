package app.qomo.emailsender.infrastructure.config;

import app.qomo.emailsender.application.port.in.ProcessEmailCommandUseCase;
import app.qomo.emailsender.application.port.in.RetryEmailJobsUseCase;
import app.qomo.emailsender.application.port.out.ClockPort;
import app.qomo.emailsender.application.port.out.EmailJobRepositoryPort;
import app.qomo.emailsender.application.port.out.EmailSenderPort;
import app.qomo.emailsender.application.port.out.EmailTemplateRendererPort;
import app.qomo.emailsender.application.port.out.PayloadCryptoPort;
import app.qomo.emailsender.application.service.ProcessEmailCommandService;
import app.qomo.emailsender.application.service.RetryEmailJobsService;
import app.qomo.emailsender.infrastructure.adapter.out.clock.SystemClockAdapter;
import app.qomo.emailsender.infrastructure.adapter.out.crypto.AesGcmPayloadCryptoAdapter;
import app.qomo.emailsender.infrastructure.adapter.out.mail.SmtpEmailSenderAdapter;
import app.qomo.emailsender.infrastructure.adapter.out.template.ClasspathEmailTemplateRendererAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailSenderBeansConfig {

  @Bean
  public ClockPort clockPort() {
    return new SystemClockAdapter();
  }

  @Bean
  public EmailTemplateRendererPort emailTemplateRendererPort(ResourceLoader resourceLoader) {
    return new ClasspathEmailTemplateRendererAdapter(resourceLoader);
  }

  @Bean
  public EmailSenderPort emailSenderPort(
      JavaMailSender javaMailSender, @Value("${qomo.mail.from}") String fromEmail) {
    return new SmtpEmailSenderAdapter(javaMailSender, fromEmail);
  }

  @Bean
  public PayloadCryptoPort payloadCryptoPort(
      @Value("${qomo.email.payload-key-b64}") String keyB64) {
    return new AesGcmPayloadCryptoAdapter(keyB64);
  }

  @Bean
  public ProcessEmailCommandUseCase processEmailCommandUseCase(
      EmailTemplateRendererPort emailTemplateRendererPort,
      EmailSenderPort emailSenderPort,
      EmailJobRepositoryPort emailJobRepositoryPort,
      PayloadCryptoPort payloadCrypto,
      ClockPort clock,
      @Value("${qomo.mail.app-name}") String appName,
      @Value("${qomo.mail.subject.email-verification}") String verificationSubject) {
    return new ProcessEmailCommandService(
        emailTemplateRendererPort,
        emailSenderPort,
        emailJobRepositoryPort,
        payloadCrypto,
        clock,
        appName,
        verificationSubject);
  }

  @Bean
  public RetryEmailJobsUseCase retryEmailJobsUseCase(
      EmailJobRepositoryPort emailJobRepositoryPort,
      PayloadCryptoPort payloadCrypto,
      EmailTemplateRendererPort emailTemplateRendererPort,
      EmailSenderPort emailSenderPort,
      ObjectMapper objectMapper,
      ClockPort clock,
      @Value("${qomo.email.retry.batch-size:20}") int batchSize,
      @Value("${qomo.email.retry.max-attempts:5}") int maxAttempts,
      @Value("${qomo.email.retry.min-age-seconds:30}") int minAgeSeconds,
      @Value("${qomo.mail.app-name}") String appName,
      @Value("${qomo.mail.subject.email-verification}") String verificationSubject) {
    return new RetryEmailJobsService(
        emailJobRepositoryPort,
        payloadCrypto,
        emailTemplateRendererPort,
        emailSenderPort,
        objectMapper,
        clock,
        batchSize,
        maxAttempts,
        minAgeSeconds,
        appName,
        verificationSubject);
  }
}
