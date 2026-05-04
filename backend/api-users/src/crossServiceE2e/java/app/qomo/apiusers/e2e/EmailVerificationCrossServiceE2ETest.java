package app.qomo.apiusers.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import app.qomo.apiusers.QomoApiUsersApplication;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.infrastructure.jobs.OutboxPublisherJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationCrossServiceE2ETest {

  private static final String TOPIC_EMAIL_COMMANDS = "qomo.email.commands.e2e";
  private static final Duration EVENTUAL_CONSISTENCY_TIMEOUT = Duration.ofSeconds(30);

  @Container
  private static final PostgreSQLContainer<?> usersDb =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  private static final PostgreSQLContainer<?> emailDb =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  private static final RedpandaContainer kafka =
      new RedpandaContainer(
          DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.2.5"));

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
            .profiles("test")
            .run(
                "--spring.datasource.url=" + emailDb.getJdbcUrl(),
                "--spring.datasource.username=" + emailDb.getUsername(),
                "--spring.datasource.password=" + emailDb.getPassword(),
                "--spring.flyway.locations=" + emailSenderFlywayLocation(),
                "--spring.kafka.bootstrap-servers=" + kafkaBootstrapServers(),
                "--spring.kafka.consumer.group-id=email-sender-e2e",
                "--spring.kafka.consumer.auto-offset-reset=earliest",
                "--spring.kafka.listener.auto-startup=true",
                "--qomo.kafka.topics.email-commands=" + TOPIC_EMAIL_COMMANDS,
                "--qomo.email.payload-key-b64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "--spring.mail.host=127.0.0.1",
                "--spring.mail.port=1",
                "--spring.task.scheduling.enabled=false",
                "--server.port=0");

    assertKafkaListenersRunning(emailSenderContext);

    usersContext =
        new SpringApplicationBuilder(QomoApiUsersApplication.class)
            .run(
                "--spring.profiles.active=test",
                "--spring.datasource.url=" + usersDb.getJdbcUrl(),
                "--spring.datasource.username=" + usersDb.getUsername(),
                "--spring.datasource.password=" + usersDb.getPassword(),
                "--spring.flyway.locations=" + apiUsersFlywayLocation(),
                "--spring.kafka.bootstrap-servers=" + kafkaBootstrapServers(),
                "--qomo.kafka.topics.email-commands=" + TOPIC_EMAIL_COMMANDS,
                "--qomo.security.jwt.secret=jwt-secret-for-e2e-only",
                "--spring.task.scheduling.enabled=false",
                "--server.port=0");

    RegisterUserUseCase registerUserUseCase = usersContext.getBean(RegisterUserUseCase.class);
    JdbcTemplate usersJdbc = usersContext.getBean(JdbcTemplate.class);

    registerUserUseCase.register(
        new RegisterUserUseCase.Command("cross-service-e2e@qomo.app", "StrongPassw0rd!"));

    Map<String, Object> outboxRow =
        usersJdbc.queryForMap(
            """
                SELECT id, payload_json, status
                FROM auth_outbox_events
                ORDER BY created_at DESC
                LIMIT 1
                """);

    assertThat(outboxRow.get("status")).isEqualTo("PENDING");

    UUID outboxId = UUID.fromString(outboxRow.get("id").toString());

    JsonNode payload = OBJECT_MAPPER.readTree((String) outboxRow.get("payload_json"));
    UUID eventId = UUID.fromString(payload.get("eventId").asText());

    OutboxPublisherJob outboxPublisherJob = usersContext.getBean(OutboxPublisherJob.class);
    outboxPublisherJob.publishPendingEvents();

    String outboxStatusAfterPublishing =
        waitUntilOutboxStatus(usersJdbc, outboxId, "SENT", EVENTUAL_CONSISTENCY_TIMEOUT);
    assertThat(outboxStatusAfterPublishing).isEqualTo("SENT");

    JdbcTemplate emailJdbc = emailSenderContext.getBean(JdbcTemplate.class);
    Map<String, Object> emailJobRow =
        waitUntilEmailJobStatus(emailJdbc, eventId, "FAILED", EVENTUAL_CONSISTENCY_TIMEOUT);

    assertThat(emailJobRow.get("status")).isEqualTo("FAILED");
    assertThat((Integer) emailJobRow.get("attempts")).isEqualTo(1);
    assertThat(emailJobRow.get("type")).isEqualTo("EMAIL_VERIFICATION_REQUESTED");
    assertThat(emailJobRow.get("template")).isEqualTo("EMAIL_VERIFICATION");
    assertThat(emailJobRow.get("payload_enc")).isNotNull();
    assertThat(emailJobRow.get("payload_nonce")).isNotNull();
    assertThat(emailJobRow.get("to_email_fp")).isNotNull();
  }

  private String waitUntilOutboxStatus(
      JdbcTemplate usersJdbc, UUID outboxId, String expectedStatus, Duration timeout) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      var status =
          usersJdbc.queryForList(
              "SELECT status FROM auth_outbox_events WHERE id = ?", String.class, outboxId);

      if (!status.isEmpty() && expectedStatus.equals(status.getFirst())) {
        return status.getFirst();
      }

      try {
        Thread.sleep(150);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for outbox status change", interruptedException);
      }
    }

    throw new AssertionError(
        "Timed out waiting for outbox status=" + expectedStatus + " for outboxId=" + outboxId);
  }

  private Map<String, Object> waitUntilEmailJobStatus(
      JdbcTemplate emailJdbc, UUID eventId, String expectedStatus, Duration timeout) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    Map<String, Object> lastObservedRow = null;

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
        lastObservedRow = rows.getFirst();
        if (expectedStatus.equals(lastObservedRow.get("status"))) {
          return lastObservedRow;
        }
      }

      try {
        Thread.sleep(150);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for email job status", interruptedException);
      }
    }

    Long totalJobs = emailJdbc.queryForObject("SELECT count(*) FROM email_jobs", Long.class);
    var latestRows =
        emailJdbc.queryForList(
            """
                SELECT event_id, status, attempts, type, template
                FROM email_jobs
                ORDER BY created_at DESC
                LIMIT 5
                """);

    throw new AssertionError(
        "Timed out waiting for email job status="
            + expectedStatus
            + " for eventId="
            + eventId
            + ", lastObservedRow="
            + lastObservedRow
            + ", totalJobs="
            + totalJobs
            + ", latestRows="
            + latestRows);
  }

  private void assertKafkaListenersRunning(ConfigurableApplicationContext context) {
    KafkaListenerEndpointRegistry registry = context.getBean(KafkaListenerEndpointRegistry.class);
    var listenerContainers = registry.getListenerContainers();

    assertThat(listenerContainers)
        .as("email-sender must register Kafka listener containers in E2E")
        .isNotEmpty();

    boolean allRunning = listenerContainers.stream().allMatch(container -> container.isRunning());
    assertThat(allRunning)
        .as("all email-sender Kafka listener containers must be running in E2E")
        .isTrue();
  }

  private String emailSenderFlywayLocation() {
    return "filesystem:"
        + Path.of("../email-sender/src/main/resources/db/migration").toAbsolutePath().normalize();
  }

  private String apiUsersFlywayLocation() {
    return "filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath().normalize();
  }

  private String kafkaBootstrapServers() {
    return kafka.getBootstrapServers();
  }
}
