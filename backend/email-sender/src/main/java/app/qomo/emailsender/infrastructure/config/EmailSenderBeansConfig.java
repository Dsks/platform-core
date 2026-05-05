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

/**
 * Owns the email-sender application wiring that is specific to the runtime infrastructure.
 *
 * <p>This class connects application ports to concrete adapters for time, SMTP delivery, template
 * rendering, and encrypted payload handling, then assembles the command and retry use cases around
 * those boundaries. External mail identity, subjects, retry limits, and the payload key are read
 * from Spring configuration. Business rules for deciding what email to send, when a job is
 * retryable, or how attempts are recorded should remain in the application services and not be
 * added here.
 */
@Configuration
public class EmailSenderBeansConfig {

  /**
   * Provides the application with a clock boundary backed by the host system time.
   *
   * <p>The port keeps use cases independent from the static Java time APIs so time-sensitive
   * decisions can stay behind the application contract.
   */
  @Bean
  public ClockPort clockPort() {
    return new SystemClockAdapter();
  }

  /**
   * Exposes classpath-based template rendering through the application port.
   *
   * <p>Email use cases depend on this port to resolve and render message templates without knowing
   * how resources are loaded by Spring.
   */
  @Bean
  public EmailTemplateRendererPort emailTemplateRendererPort(ResourceLoader resourceLoader) {
    return new ClasspathEmailTemplateRendererAdapter(resourceLoader);
  }

  /**
   * Connects the application email delivery port to the configured SMTP boundary.
   *
   * <p>The adapter delegates transport details to {@link JavaMailSender} and uses the configured
   * sender identity from {@code qomo.mail.from}. Host, credentials, and provider-specific SMTP
   * settings remain outside this bean.
   */
  @Bean
  public EmailSenderPort emailSenderPort(
      JavaMailSender javaMailSender, @Value("${qomo.mail.from}") String fromEmail) {
    return new SmtpEmailSenderAdapter(javaMailSender, fromEmail);
  }

  /**
   * Supplies payload encryption and decryption for email jobs through the application crypto port.
   *
   * <p>The key material is provided by external configuration through {@code
   * qomo.email.payload-key-b64}. This bean only wires the configured adapter; it does not embed
   * secrets or define higher-level security policy.
   */
  @Bean
  public PayloadCryptoPort payloadCryptoPort(
      @Value("${qomo.email.payload-key-b64}") String keyB64) {
    return new AesGcmPayloadCryptoAdapter(keyB64);
  }

  /**
   * Assembles the command use case that processes a single incoming email request.
   *
   * <p>The bean bridges application logic with template rendering, SMTP delivery, persistence,
   * payload crypto, and time. Runtime-facing mail presentation values such as the application name
   * and verification subject are injected from configuration while command handling decisions
   * remain in {@link ProcessEmailCommandService}.
   */
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

  /**
   * Assembles the retry use case used to reprocess stored email jobs.
   *
   * <p>The configured batch size, maximum attempts, and minimum job age are operational resilience
   * controls read from {@code qomo.email.retry.*}. This wiring connects persistence, crypto,
   * rendering, SMTP delivery, JSON mapping, and time while leaving retry eligibility and state
   * transitions to {@link RetryEmailJobsService}.
   */
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
