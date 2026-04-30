package app.qomo.apiusers.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import app.qomo.apiusers.QomoApiUsersApplication;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.infrastructure.jobs.OutboxPublisherJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationCrossServiceE2ETest {

  private static final String TOPIC_EMAIL_COMMANDS = "qomo.email.commands.e2e";
  private static final Duration EVENTUAL_CONSISTENCY_TIMEOUT = Duration.ofSeconds(20);

  @Container
  private static final PostgreSQLContainer<?> usersDb =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  private static final PostgreSQLContainer<?> emailDb =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  private static final GenericContainer<?> kafka =
      new GenericContainer<>(
          DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.2.5"))
          .withExposedPorts(9092)
          .withCommand(
              "redpanda",
              "start",
              "--overprovisioned",
              "--smp",
              "1",
              "--memory",
              "512M",
              "--reserve-memory",
              "0M",
              "--node-id",
              "0",
              "--check=false",
              "--kafka-addr",
              "PLAINTEXT://0.0.0.0:9092",
              "--advertise-kafka-addr",
              "PLAINTEXT://127.0.0.1:9092");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ConfigurableApplicationContext usersContext;
  private ConfigurableApplicationContext emailSenderContext;

  @BeforeAll
  static void startInfrastructure() {
    usersDb.start();
    emailDb.start();
    kafka.start();
  }

  @AfterEach
  void tearDownContexts() {
    if (usersContext != null) {
      usersContext.close();
    }
    if (emailSenderContext != null) {
      emailSenderContext.close();
    }
  }

  @Test
  void shouldProcessEmailVerificationAcrossApiUsersAndEmailSender() throws Exception {
    Class<?> emailSenderApplicationClass =
        Class.forName("app.qomo.emailsender.QomoEmailSenderApplication");

    emailSenderContext =
        new SpringApplicationBuilder(emailSenderApplicationClass)
            .properties(
                Map.of(
                    "spring.datasource.url", emailDb.getJdbcUrl(),
                    "spring.datasource.username", emailDb.getUsername(),
                    "spring.datasource.password", emailDb.getPassword(),
                    "spring.kafka.bootstrap-servers", kafkaBootstrapServers(),
                    "spring.kafka.consumer.group-id", "email-sender-e2e",
                    "qomo.kafka.topics.email-commands", TOPIC_EMAIL_COMMANDS,
                    "spring.task.scheduling.enabled", "false",
                    "spring.mail.host", "127.0.0.1",
                    "spring.mail.port", "1",
                    "qomo.email.payload-key-b64",
                    "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="))
            .run();

    usersContext =
        new SpringApplicationBuilder(QomoApiUsersApplication.class)
            .properties(
                Map.of(
                    "spring.datasource.url", usersDb.getJdbcUrl(),
                    "spring.datasource.username", usersDb.getUsername(),
                    "spring.datasource.password", usersDb.getPassword(),
                    "spring.kafka.bootstrap-servers", kafkaBootstrapServers(),
                    "qomo.kafka.topics.email-commands", TOPIC_EMAIL_COMMANDS,
                    "qomo.security.jwt.secret", "jwt-secret-for-e2e-only",
                    "spring.task.scheduling.enabled", "false"))
            .run();

    RegisterUserUseCase registerUserUseCase = usersContext.getBean(RegisterUserUseCase.class);
    JdbcTemplate usersJdbc = usersContext.getBean(JdbcTemplate.class);

    registerUserUseCase.register(
        new RegisterUserUseCase.Command("cross-service-e2e@qomo.app", "StrongPassw0rd!"));

    Map<String, Object> outboxRow =
        usersJdbc.queryForMap(
            """
                SELECT payload_json, status
                FROM auth_outbox_events
                ORDER BY created_at DESC
                LIMIT 1
                """);

    assertThat(outboxRow.get("status")).isEqualTo("PENDING");

    JsonNode payload = OBJECT_MAPPER.readTree((String) outboxRow.get("payload_json"));
    UUID eventId = UUID.fromString(payload.get("eventId").asText());

    OutboxPublisherJob outboxPublisherJob = usersContext.getBean(OutboxPublisherJob.class);
    outboxPublisherJob.publishPendingEvents();

    String outboxStatusAfterPublishing =
        usersJdbc.queryForObject(
            "SELECT status FROM auth_outbox_events ORDER BY created_at DESC LIMIT 1", String.class);
    assertThat(outboxStatusAfterPublishing).isEqualTo("SENT");

    JdbcTemplate emailJdbc = emailSenderContext.getBean(JdbcTemplate.class);
    Map<String, Object> emailJobRow =
        waitUntilEmailJobExists(emailJdbc, eventId, EVENTUAL_CONSISTENCY_TIMEOUT);

    assertThat(emailJobRow.get("status")).isEqualTo("FAILED");
    assertThat((Integer) emailJobRow.get("attempts")).isEqualTo(1);
    assertThat(emailJobRow.get("type")).isEqualTo("EMAIL_VERIFICATION_REQUESTED");
    assertThat(emailJobRow.get("template")).isEqualTo("EMAIL_VERIFICATION");
    assertThat(emailJobRow.get("payload_enc")).isNotNull();
    assertThat(emailJobRow.get("payload_nonce")).isNotNull();
    assertThat(emailJobRow.get("to_email_fp")).isNotNull();
  }

  private Map<String, Object> waitUntilEmailJobExists(
      JdbcTemplate emailJdbc,
      UUID eventId,
      Duration timeout) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      var rows =
          emailJdbc.queryForList(
              """
                  SELECT status, attempts, type, template, payload_enc, payload_nonce, to_email_fp
                  FROM email_jobs
                  WHERE event_id = ?
                  """,
              eventId);

      if (!rows.isEmpty()) {
        return rows.getFirst();
      }

      try {
        Thread.sleep(150);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for email job creation",
            interruptedException);
      }
    }

    throw new AssertionError("Timed out waiting for email job for eventId=" + eventId);
  }

  private String kafkaBootstrapServers() {
    return kafka.getHost() + ":" + kafka.getMappedPort(9092);
  }
}