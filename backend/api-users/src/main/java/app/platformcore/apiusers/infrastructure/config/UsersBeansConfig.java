package app.platformcore.apiusers.infrastructure.config;

import app.platformcore.apiusers.application.port.in.CreateUserUseCase;
import app.platformcore.apiusers.application.port.in.DeleteUserUseCase;
import app.platformcore.apiusers.application.port.in.GetCurrentUserUseCase;
import app.platformcore.apiusers.application.port.in.GetUserUseCase;
import app.platformcore.apiusers.application.port.in.LoginUseCase;
import app.platformcore.apiusers.application.port.in.RegisterUserUseCase;
import app.platformcore.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.platformcore.apiusers.application.port.in.VerifyEmailUseCase;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.CurrentUserPort;
import app.platformcore.apiusers.application.port.out.JwtTokenProviderPort;
import app.platformcore.apiusers.application.port.out.OutboxEventPublisherPort;
import app.platformcore.apiusers.application.port.out.OutboxRepositoryPort;
import app.platformcore.apiusers.application.port.out.PasswordHasherPort;
import app.platformcore.apiusers.application.port.out.PasswordVerifierPort;
import app.platformcore.apiusers.application.port.out.RoleRepositoryPort;
import app.platformcore.apiusers.application.port.out.UserEventPublisherPort;
import app.platformcore.apiusers.application.port.out.UserRepositoryPort;
import app.platformcore.apiusers.application.port.out.VerificationTokenRepositoryPort;
import app.platformcore.apiusers.application.service.CreateUserService;
import app.platformcore.apiusers.application.service.DeleteUserService;
import app.platformcore.apiusers.application.service.GetCurrentUserService;
import app.platformcore.apiusers.application.service.GetUserService;
import app.platformcore.apiusers.application.service.IssueEmailVerificationService;
import app.platformcore.apiusers.application.service.LoginService;
import app.platformcore.apiusers.application.service.RegisterUserService;
import app.platformcore.apiusers.application.service.ResendEmailVerificationService;
import app.platformcore.apiusers.application.service.VerifyEmailService;
import app.platformcore.apiusers.infrastructure.adapter.out.clock.SystemClockAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.crypto.BCryptPasswordVerifierAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.crypto.BcryptPasswordHasherAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.event.KafkaOutboxPublisherAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.event.LogUserEventPublisherAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.persistence.PostgresOutboxRepositoryAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.persistence.PostgresRoleRepositoryAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.persistence.PostgresUserRepositoryAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.persistence.PostgresVerificationTokenRepositoryAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.security.JwtTokenProviderNimbusAdapter;
import app.platformcore.apiusers.infrastructure.adapter.out.security.SecurityCurrentUserAdapter;
import app.platformcore.apiusers.infrastructure.jobs.OutboxPublisherJob;
import app.platformcore.apiusers.infrastructure.util.OtpGenerator;
import app.platformcore.apiusers.infrastructure.util.TokenHasher;
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

/**
 * Assembles the users bounded context by binding application ports to the concrete infrastructure
 * adapters used at runtime.
 *
 * <p>This class owns manual wiring for security primitives, persistence adapters, Kafka outbox
 * publishing, email-verification support, and application use cases. Keeping those bindings here
 * makes the dependency direction explicit: web and jobs call application ports, while adapters
 * satisfy outbound ports. Spring Boot still provides lower-level infrastructure such as the
 * datasource and Kafka client libraries.
 *
 * <p>External properties consumed here cover JWT signing material and expiry, Kafka bootstrap
 * servers, outbox worker tuning, email-verification limits and topics, and the optional initial
 * superadmin credentials. Request authorization rules and CSRF cookie behavior are intentionally
 * centralized in {@link SecurityConfig}.
 */
@Configuration
public class UsersBeansConfig {

  /**
   * Provides the password encoder shared by hashing and verification adapters.
   *
   * <p>The encoder is defined explicitly so both outbound password ports use the same BCrypt
   * implementation rather than relying on separate framework-created instances.
   */
  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Adapts the Spring-managed datasource into the JDBC API used by PostgreSQL repository adapters.
   *
   * @param dataSource the datasource configured by the runtime environment
   */
  @Bean
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  /**
   * Exposes the JSON mapper used by outbox and email-verification payload wiring.
   *
   * <p>Module auto-discovery keeps Java time and other registered datatypes aligned with the rest
   * of the JVM without making each adapter configure Jackson independently.
   */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  /**
   * Connects application time access to the system clock adapter.
   *
   * <p>Use cases and workers receive the {@link ClockPort} instead of calling the JVM clock
   * directly, keeping runtime time access behind an outbound port.
   */
  @Bean
  public ClockPort clockPort() {
    return new SystemClockAdapter();
  }

  /**
   * Provides JWT issuance and validation for the authentication use cases and security filter.
   *
   * @param secret sensitive signing secret from {@code platformcore.security.jwt.secret}
   * @param expirationMs token lifetime from {@code platformcore.security.jwt.expiration-ms};
   *     defaults to one day when the property is absent
   */
  @Bean
  public JwtTokenProviderPort jwtTokenProviderPort(
      @Value("${platformcore.security.jwt.secret}") String secret,
      @Value("${platformcore.security.jwt.expiration-ms:86400000}") long expirationMs) {
    return new JwtTokenProviderNimbusAdapter(secret, expirationMs);
  }

  /**
   * Binds password hashing requests from application services to the BCrypt adapter.
   *
   * @param encoder the shared BCrypt encoder used for persisted password hashes
   */
  @Bean
  public PasswordHasherPort passwordHasherPort(BCryptPasswordEncoder encoder) {
    return new BcryptPasswordHasherAdapter(encoder);
  }

  /**
   * Binds password verification requests from login flows to the BCrypt adapter.
   *
   * @param encoder the shared BCrypt encoder used to compare candidate credentials
   */
  @Bean
  public PasswordVerifierPort passwordVerifierPort(BCryptPasswordEncoder encoder) {
    return new BCryptPasswordVerifierAdapter(encoder);
  }

  /**
   * Publishes user-domain events through the current logging adapter.
   *
   * <p>The bean keeps the application port wired even when no external event broker is used for
   * immediate user events; durable integration messages are handled separately through the outbox
   * publisher.
   */
  @Bean
  public UserEventPublisherPort userEventPublisherPort() {
    return new LogUserEventPublisherAdapter();
  }

  /**
   * Connects user persistence ports to the PostgreSQL repository implementation.
   *
   * @param jdbcTemplate JDBC access configured from the application datasource
   */
  @Bean
  public UserRepositoryPort userRepositoryPort(JdbcTemplate jdbcTemplate) {
    return new PostgresUserRepositoryAdapter(jdbcTemplate);
  }

  /**
   * Connects role lookup ports to the PostgreSQL repository implementation.
   *
   * @param jdbcTemplate JDBC access configured from the application datasource
   */
  @Bean
  public RoleRepositoryPort roleRepositoryPort(JdbcTemplate jdbcTemplate) {
    return new PostgresRoleRepositoryAdapter(jdbcTemplate);
  }

  /**
   * Connects verification-token persistence to PostgreSQL for email verification flows.
   *
   * @param jdbcTemplate JDBC access configured from the application datasource
   */
  @Bean
  public VerificationTokenRepositoryPort verificationTokenRepositoryPort(
      JdbcTemplate jdbcTemplate) {
    return new PostgresVerificationTokenRepositoryAdapter(jdbcTemplate);
  }

  /**
   * Connects the transactional outbox port to its PostgreSQL repository.
   *
   * @param jdbcTemplate JDBC access configured from the application datasource
   */
  @Bean
  public OutboxRepositoryPort outboxRepositoryPort(JdbcTemplate jdbcTemplate) {
    return new PostgresOutboxRepositoryAdapter(jdbcTemplate);
  }

  /**
   * Reads the authenticated user from Spring Security for application services.
   *
   * <p>This adapter is separated from HTTP controllers so use cases can depend on {@link
   * CurrentUserPort} without knowing about the security context implementation.
   */
  @Bean
  public CurrentUserPort currentUserPort() {
    return new SecurityCurrentUserAdapter();
  }

  /** Supplies one-time-password generation for email verification orchestration. */
  @Bean
  public OtpGenerator otpGenerator() {
    return new OtpGenerator();
  }

  /**
   * Supplies token hashing for storing and comparing verification tokens without persisting raw OTP
   * values.
   */
  @Bean
  public TokenHasher tokenHasher() {
    return new TokenHasher();
  }

  /**
   * Configures the Kafka producer used by the outbox publisher.
   *
   * @param bootstrapServers Kafka bootstrap servers from {@code spring.kafka.bootstrap-servers};
   *     defaults to {@code kafka:9092} for the containerized runtime
   */
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

  /**
   * Provides the Kafka template used by the outbox event publisher adapter.
   *
   * @param producerFactory producer factory configured with the runtime Kafka bootstrap servers
   */
  @Bean
  public KafkaTemplate<String, String> kafkaTemplate(
      ProducerFactory<String, String> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  /**
   * Connects durable outbox messages to Kafka publication.
   *
   * @param kafkaTemplate Kafka client used to send serialized outbox payloads
   * @param objectMapper mapper used by the adapter to serialize event envelopes
   */
  @Bean
  public OutboxEventPublisherPort outboxEventPublisherPort(
      KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
    return new KafkaOutboxPublisherAdapter(kafkaTemplate, objectMapper);
  }

  /**
   * Wires the outbox worker that drains persisted integration events to the configured publisher.
   *
   * <p>The worker is enabled by {@code platformcore.outbox.publisher.enabled}, which defaults to
   * enabled. Batch size, maximum attempts, and minimum message age are runtime-tunable properties
   * so retry pressure can be changed without altering the job implementation.
   *
   * @param outboxRepository repository used to load and update pending outbox rows
   * @param outboxPublisher publisher used to deliver eligible messages
   * @param clock shared time port used for retry and age decisions
   * @param batchSize value of {@code platformcore.outbox.publisher.batch-size}; defaults to {@code
   *     50}
   * @param maxAttempts value of {@code platformcore.outbox.publisher.max-attempts}; defaults to
   *     {@code 10}
   * @param minAgeMs value of {@code platformcore.outbox.publisher.min-age-ms}; defaults to {@code
   *     500}
   */
  @Bean
  @ConditionalOnProperty(
      value = "platformcore.outbox.publisher.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OutboxPublisherJob outboxPublisherJob(
      OutboxRepositoryPort outboxRepository,
      OutboxEventPublisherPort outboxPublisher,
      ClockPort clock,
      @Value("${platformcore.outbox.publisher.batch-size:50}") int batchSize,
      @Value("${platformcore.outbox.publisher.max-attempts:10}") int maxAttempts,
      @Value("${platformcore.outbox.publisher.min-age-ms:500}") long minAgeMs) {
    return new OutboxPublisherJob(
        outboxRepository,
        outboxPublisher,
        clock,
        batchSize,
        maxAttempts,
        Duration.ofMillis(minAgeMs));
  }

  /**
   * Assembles the administrative user-creation use case.
   *
   * <p>The service is wired manually because it crosses persistence, role lookup, password hashing,
   * event publication, clock access, and current-user authorization context.
   *
   * @param userRepository user persistence port used by the service
   * @param roleRepository role lookup port used to assign requested roles
   * @param passwordHasher outbound port for hashing generated or supplied passwords
   * @param publisher outbound publisher for user-domain events
   * @param clock shared time port for creation timestamps
   * @param currentUserPort security-context adapter used for the acting user
   */
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

  /**
   * Assembles the user-read use case with the user repository port.
   *
   * @param userRepository user persistence port queried by the service
   * @param clock shared time port used for idempotent user updates
   */
  @Bean
  public GetUserUseCase getUserUseCase(UserRepositoryPort userRepository, ClockPort clock) {
    return new GetUserService(userRepository, clock);
  }

  /**
   * Assembles the administrative soft-delete use case with the user repository and clock ports.
   *
   * @param userRepository user persistence port used to load and anonymize the target account
   * @param clock shared time port used for deletion audit timestamps
   */
  @Bean
  public DeleteUserUseCase deleteUserUseCase(UserRepositoryPort userRepository, ClockPort clock) {
    return new DeleteUserService(userRepository, clock);
  }

  /**
   * Assembles the current-user read use case with the security-context and user repository ports.
   *
   * @param currentUserPort security-context adapter used to identify the authenticated principal
   * @param userRepository user persistence port queried for current account state
   */
  @Bean
  public GetCurrentUserUseCase getCurrentUserUseCase(
      CurrentUserPort currentUserPort, UserRepositoryPort userRepository) {
    return new GetCurrentUserService(currentUserPort, userRepository);
  }

  /**
   * Wires the email-verification issuer used by registration, login, and resend flows.
   *
   * <p>The service persists hashed OTP material and emits an email command through the outbox. The
   * OTP TTL, resend interval, and target email-command topic are externally configurable.
   *
   * @param verificationTokenRepository token repository used to persist hashed OTP state
   * @param outboxRepository outbox repository used to enqueue email commands transactionally
   * @param clock shared time port for token expiry and resend windows
   * @param otpGenerator generator for user-facing verification codes
   * @param tokenHasher hashing utility used before storing verification codes
   * @param objectMapper mapper used to serialize email-command payloads
   * @param emailOtpTtlSeconds value of {@code platformcore.security.verification.otp.ttl-seconds};
   *     defaults to {@code 600}
   * @param resendMinIntervalSeconds value of {@code
   *     platformcore.security.verification.resend-min-interval-seconds}; defaults to {@code 60}
   * @param emailCommandsTopic Kafka topic from {@code platformcore.kafka.topics.email-commands};
   *     defaults to {@code platformcore.email.commands}
   */
  @Bean
  public IssueEmailVerificationService issueEmailVerificationService(
      VerificationTokenRepositoryPort verificationTokenRepository,
      OutboxRepositoryPort outboxRepository,
      ClockPort clock,
      OtpGenerator otpGenerator,
      TokenHasher tokenHasher,
      ObjectMapper objectMapper,
      @Value("${platformcore.security.verification.otp.ttl-seconds:600}") long emailOtpTtlSeconds,
      @Value("${platformcore.security.verification.resend-min-interval-seconds:60}")
          long resendMinIntervalSeconds,
      @Value("${platformcore.kafka.topics.email-commands:platformcore.email.commands}")
          String emailCommandsTopic) {
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

  /**
   * Assembles the login use case with credential verification and email-verification issuing.
   *
   * @param userRepository user persistence port used to load the login subject
   * @param passwordVerifier outbound port for comparing submitted credentials
   * @param clock shared time port for login-time decisions
   * @param issueEmailVerificationService service used when login requires email verification
   */
  @Bean
  public LoginUseCase loginUseCase(
      UserRepositoryPort userRepository,
      PasswordVerifierPort passwordVerifier,
      ClockPort clock,
      IssueEmailVerificationService issueEmailVerificationService) {
    return new LoginService(userRepository, passwordVerifier, clock, issueEmailVerificationService);
  }

  /**
   * Assembles the public registration use case with persistence, role lookup, password hashing, and
   * email-verification issuing.
   *
   * @param userRepository user persistence port used to create the account
   * @param roleRepository role lookup port used for registration roles
   * @param passwordHasher outbound port for hashing the submitted password
   * @param clock shared time port for registration timestamps
   * @param issueEmailVerificationService service used to issue the initial verification code
   */
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

  /**
   * Assembles the verification-email resend use case.
   *
   * @param userRepository user persistence port used to locate the target account
   * @param issueEmailVerificationService service that enforces resend timing and enqueues email
   *     commands
   */
  @Bean
  public ResendEmailVerificationUseCase resendEmailVerificationUseCase(
      UserRepositoryPort userRepository,
      IssueEmailVerificationService issueEmailVerificationService) {
    return new ResendEmailVerificationService(userRepository, issueEmailVerificationService);
  }

  /**
   * Assembles the email-verification use case that validates stored hashed OTP material.
   *
   * @param verificationTokenRepository token repository used to load and update verification state
   * @param userRepository user persistence port used to mark accounts as verified
   * @param clock shared time port for expiry decisions
   * @param tokenHasher hashing utility used to compare submitted OTP values
   * @param maxAttempts value of {@code platformcore.security.verification.otp.max-attempts};
   *     defaults to {@code 5}
   */
  @Bean
  public VerifyEmailUseCase verifyEmailUseCase(
      VerificationTokenRepositoryPort verificationTokenRepository,
      UserRepositoryPort userRepository,
      ClockPort clock,
      TokenHasher tokenHasher,
      @Value("${platformcore.security.verification.otp.max-attempts:5}") int maxAttempts) {
    return new VerifyEmailService(
        verificationTokenRepository, userRepository, clock, tokenHasher, maxAttempts);
  }

  /**
   * Wires the startup runner that optionally creates the initial superadmin user.
   *
   * <p>{@code platformcore.bootstrap.superadmin.initial-password} is sensitive bootstrap material
   * and may be blank to disable creation. {@code platformcore.bootstrap.superadmin.email}
   * identifies the account and defaults to {@code superadmin@platformcore.app}.
   *
   * @param userRepository user persistence port used to detect and create the bootstrap account
   * @param roleRepository role lookup port used to attach the superadmin role
   * @param passwordHasher outbound port for hashing the sensitive initial password
   * @param clock shared time port for account creation and verification timestamps
   * @param superAdminInitialPassword sensitive value of {@code
   *     platformcore.bootstrap.superadmin.initial-password}
   * @param superAdminEmail value of {@code platformcore.bootstrap.superadmin.email}; defaults to
   *     {@code superadmin@platformcore.app}
   */
  @Bean
  public SuperAdminBootstrap superAdminBootstrap(
      UserRepositoryPort userRepository,
      RoleRepositoryPort roleRepository,
      PasswordHasherPort passwordHasher,
      ClockPort clock,
      @Value("${platformcore.bootstrap.superadmin.initial-password:}")
          String superAdminInitialPassword,
      @Value("${platformcore.bootstrap.superadmin.email:superadmin@platformcore.app}")
          String superAdminEmail) {
    return new SuperAdminBootstrap(
        userRepository,
        roleRepository,
        passwordHasher,
        clock,
        superAdminInitialPassword,
        superAdminEmail);
  }
}
