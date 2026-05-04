package app.qomo.apiusers.infrastructure.config;

import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.qomo.apiusers.application.port.in.VerifyEmailUseCase;
import app.qomo.apiusers.application.service.CreateUserService;
import app.qomo.apiusers.application.service.GetUserService;
import app.qomo.apiusers.application.service.IssueEmailVerificationService;
import app.qomo.apiusers.application.service.LoginService;
import app.qomo.apiusers.application.service.RegisterUserService;
import app.qomo.apiusers.application.service.ResendEmailVerificationService;
import app.qomo.apiusers.application.service.VerifyEmailService;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.CurrentUserPort;
import app.qomo.apiusers.domain.port.out.JwtTokenProviderPort;
import app.qomo.apiusers.domain.port.out.OutboxEventPublisherPort;
import app.qomo.apiusers.domain.port.out.OutboxRepositoryPort;
import app.qomo.apiusers.domain.port.out.PasswordHasherPort;
import app.qomo.apiusers.domain.port.out.PasswordVerifierPort;
import app.qomo.apiusers.domain.port.out.RoleRepositoryPort;
import app.qomo.apiusers.domain.port.out.UserEventPublisherPort;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.port.out.VerificationTokenRepositoryPort;
import app.qomo.apiusers.infrastructure.adapter.out.clock.SystemClockAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.crypto.BCryptPasswordVerifierAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.crypto.BcryptPasswordHasherAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.event.KafkaOutboxPublisherAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.event.LogUserEventPublisherAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.persistence.PostgresOutboxRepositoryAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.persistence.PostgresRoleRepositoryAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.persistence.PostgresUserRepositoryAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.persistence.PostgresVerificationTokenRepositoryAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.security.JwtTokenProviderNimbusAdapter;
import app.qomo.apiusers.infrastructure.adapter.out.security.SecurityCurrentUserAdapter;
import app.qomo.apiusers.infrastructure.jobs.OutboxPublisherJob;
import app.qomo.apiusers.infrastructure.util.OtpGenerator;
import app.qomo.apiusers.infrastructure.util.TokenHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class UsersBeansConfig {

  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  @Bean
  public ClockPort clockPort() {
    return new SystemClockAdapter();
  }

  @Bean
  public JwtTokenProviderPort jwtTokenProviderPort(
      @Value("${qomo.security.jwt.secret}") String secret,
      @Value("${qomo.security.jwt.expiration-ms:86400000}") long expirationMs) {
    return new JwtTokenProviderNimbusAdapter(secret, expirationMs);
  }

  @Bean
  public PasswordHasherPort passwordHasherPort(BCryptPasswordEncoder encoder) {
    return new BcryptPasswordHasherAdapter(encoder);
  }

  @Bean
  public PasswordVerifierPort passwordVerifierPort(BCryptPasswordEncoder encoder) {
    return new BCryptPasswordVerifierAdapter(encoder);
  }

  @Bean
  public UserEventPublisherPort userEventPublisherPort() {
    return new LogUserEventPublisherAdapter();
  }

  @Bean
  public UserRepositoryPort userRepositoryPort(JdbcTemplate jdbcTemplate) {
    return new PostgresUserRepositoryAdapter(jdbcTemplate);
  }

  @Bean
  public RoleRepositoryPort roleRepositoryPort(JdbcTemplate jdbcTemplate) {
    return new PostgresRoleRepositoryAdapter(jdbcTemplate);
  }

  @Bean
  public VerificationTokenRepositoryPort verificationTokenRepositoryPort(
      JdbcTemplate jdbcTemplate) {
    return new PostgresVerificationTokenRepositoryAdapter(jdbcTemplate);
  }

  @Bean
  public OutboxRepositoryPort outboxRepositoryPort(JdbcTemplate jdbcTemplate) {
    return new PostgresOutboxRepositoryAdapter(jdbcTemplate);
  }

  @Bean
  public CurrentUserPort currentUserPort() {
    return new SecurityCurrentUserAdapter();
  }

  @Bean
  public OtpGenerator otpGenerator() {
    return new OtpGenerator();
  }

  @Bean
  public TokenHasher tokenHasher() {
    return new TokenHasher();
  }

  @Bean
  public ProducerFactory<String, String> kafkaProducerFactory(
      @Value("${spring.kafka.bootstrap-servers:kafka:9092}") String bootstrapServers) {
    Map<String, Object> config = new HashMap<>();
    config.put(
        org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        bootstrapServers);
    config.put(
        org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringSerializer.class);
    config.put(
        org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  public KafkaTemplate<String, String> kafkaTemplate(
      ProducerFactory<String, String> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  public OutboxEventPublisherPort outboxEventPublisherPort(
      KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
    return new KafkaOutboxPublisherAdapter(kafkaTemplate, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(
      value = "qomo.outbox.publisher.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OutboxPublisherJob outboxPublisherJob(
      OutboxRepositoryPort outboxRepository,
      OutboxEventPublisherPort outboxPublisher,
      ClockPort clock,
      @Value("${qomo.outbox.publisher.batch-size:50}") int batchSize,
      @Value("${qomo.outbox.publisher.max-attempts:10}") int maxAttempts,
      @Value("${qomo.outbox.publisher.min-age-ms:500}") long minAgeMs) {
    return new OutboxPublisherJob(
        outboxRepository,
        outboxPublisher,
        clock,
        batchSize,
        maxAttempts,
        Duration.ofMillis(minAgeMs));
  }

  @Bean
  public CreateUserUseCase createUserUseCase(
      UserRepositoryPort userRepository,
      RoleRepositoryPort roleRepository,
      PasswordHasherPort passwordHasher,
      UserEventPublisherPort publisher,
      ClockPort clock,
      CurrentUserPort currentUserPort) {
    return new CreateUserService(
        userRepository, roleRepository, passwordHasher, publisher, clock, currentUserPort);
  }

  @Bean
  public GetUserUseCase getUserUseCase(UserRepositoryPort userRepository) {
    return new GetUserService(userRepository);
  }

  @Bean
  public IssueEmailVerificationService issueEmailVerificationService(
      VerificationTokenRepositoryPort verificationTokenRepository,
      OutboxRepositoryPort outboxRepository,
      ClockPort clock,
      OtpGenerator otpGenerator,
      TokenHasher tokenHasher,
      ObjectMapper objectMapper,
      @Value("${qomo.security.verification.otp.ttl-seconds:600}") long emailOtpTtlSeconds,
      @Value("${qomo.security.verification.resend-min-interval-seconds:60}")
          long resendMinIntervalSeconds,
      @Value("${qomo.kafka.topics.email-commands:qomo.email.commands}") String emailCommandsTopic) {
    return new IssueEmailVerificationService(
        verificationTokenRepository,
        outboxRepository,
        clock,
        otpGenerator,
        tokenHasher,
        Duration.ofSeconds(emailOtpTtlSeconds),
        Duration.ofSeconds(resendMinIntervalSeconds),
        objectMapper,
        emailCommandsTopic);
  }

  @Bean
  public LoginUseCase loginUseCase(
      UserRepositoryPort userRepository,
      PasswordVerifierPort passwordVerifier,
      ClockPort clock,
      IssueEmailVerificationService issueEmailVerificationService) {
    return new LoginService(userRepository, passwordVerifier, clock, issueEmailVerificationService);
  }

  @Bean
  public RegisterUserUseCase registerUserUseCase(
      UserRepositoryPort userRepository,
      RoleRepositoryPort roleRepository,
      PasswordHasherPort passwordHasher,
      ClockPort clock,
      IssueEmailVerificationService issueEmailVerificationService) {
    return new RegisterUserService(
        userRepository, roleRepository, passwordHasher, clock, issueEmailVerificationService);
  }

  @Bean
  public ResendEmailVerificationUseCase resendEmailVerificationUseCase(
      UserRepositoryPort userRepository,
      IssueEmailVerificationService issueEmailVerificationService) {
    return new ResendEmailVerificationService(userRepository, issueEmailVerificationService);
  }

  @Bean
  public VerifyEmailUseCase verifyEmailUseCase(
      VerificationTokenRepositoryPort verificationTokenRepository,
      UserRepositoryPort userRepository,
      ClockPort clock,
      TokenHasher tokenHasher,
      @Value("${qomo.security.verification.otp.max-attempts:5}") int maxAttempts) {
    return new VerifyEmailService(
        verificationTokenRepository, userRepository, clock, tokenHasher, maxAttempts);
  }

  @Bean
  public SuperAdminBootstrap superAdminBootstrap(
      UserRepositoryPort userRepository,
      RoleRepositoryPort roleRepository,
      PasswordHasherPort passwordHasher,
      ClockPort clock,
      @Value("${qomo.bootstrap.superadmin.initial-password:}") String superAdminInitialPassword,
      @Value("${qomo.bootstrap.superadmin.email:superadmin@qomo.app}") String superAdminEmail) {
    return new SuperAdminBootstrap(
        userRepository,
        roleRepository,
        passwordHasher,
        clock,
        superAdminInitialPassword,
        superAdminEmail);
  }
}
