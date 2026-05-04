package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.qomo.apiusers.TestContainersConfig;
import app.qomo.apiusers.application.port.out.OutboxRepositoryPort;
import app.qomo.apiusers.domain.model.OutboxEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestContainersConfig.class)
class PostgresOutboxRepositoryAdapterIT {

  @Autowired private OutboxRepositoryPort outboxRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTable() {
    jdbcTemplate.update("TRUNCATE TABLE auth_outbox_events");
  }

  @Test
  void insertPending_shouldPersistPendingEventWithExpectedColumns() {
    Instant createdAt = Instant.parse("2026-03-20T10:00:00Z");
    Instant updatedAt = Instant.parse("2026-03-20T10:00:05Z");
    OutboxEvent event =
        new OutboxEvent(
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            "USER",
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
            "USER_REGISTERED",
            "qomo.email.commands",
            "user:aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "{\"event\":\"USER_REGISTERED\"}",
            0,
            createdAt,
            updatedAt);

    outboxRepository.insertPending(event);

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM auth_outbox_events WHERE id = ?", event.id());

    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(((Integer) row.get("attempts"))).isEqualTo(0);
    assertThat(row.get("last_error")).isNull();
    assertThat(row.get("sent_at")).isNull();
    assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(createdAt);
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(updatedAt);
  }

  @Test
  void claimPublishable_shouldClaimOnlyEligibleEventsRespectingStatusAgeAndBatchSize() {
    Instant olderThan = Instant.parse("2026-03-20T10:00:00Z");
    Instant now = Instant.parse("2026-03-20T10:10:00Z");

    UUID pendingOldest =
        insertEvent("PENDING", 0, "2026-03-20T09:30:00Z", "2026-03-20T09:40:00Z", null);
    UUID failedSecond =
        insertEvent("FAILED", 2, "2026-03-20T09:35:00Z", "2026-03-20T09:41:00Z", "err");
    UUID pendingFresh =
        insertEvent("PENDING", 0, "2026-03-20T09:20:00Z", "2026-03-20T10:00:00Z", null);
    UUID inProgressOld =
        insertEvent("IN_PROGRESS", 1, "2026-03-20T09:00:00Z", "2026-03-20T09:10:00Z", null);
    UUID sentOld = insertEvent("SENT", 1, "2026-03-20T08:00:00Z", "2026-03-20T08:10:00Z", null);

    List<OutboxEvent> claimed = outboxRepository.claimPublishable(2, olderThan, now);

    assertThat(claimed)
        .extracting(OutboxEvent::id)
        .containsExactlyInAnyOrder(pendingOldest, failedSecond);
    List<Instant> claimedCreatedAt =
        claimed.stream().map(OutboxEvent::createdAt).sorted(Comparator.naturalOrder()).toList();
    assertThat(claimedCreatedAt)
        .containsExactly(
            Instant.parse("2026-03-20T09:30:00Z"), Instant.parse("2026-03-20T09:35:00Z"));

    assertStatusAndUpdatedAt(pendingOldest, "IN_PROGRESS", now);
    assertStatusAndUpdatedAt(failedSecond, "IN_PROGRESS", now);
    assertStatusAndUpdatedAt(pendingFresh, "PENDING", Instant.parse("2026-03-20T10:00:00Z"));
    assertStatusAndUpdatedAt(inProgressOld, "IN_PROGRESS", Instant.parse("2026-03-20T09:10:00Z"));
    assertStatusAndUpdatedAt(sentOld, "SENT", Instant.parse("2026-03-20T08:10:00Z"));
  }

  @Test
  void markSent_shouldMoveInProgressToSentAndClearLastError() {
    UUID id =
        insertEvent(
            "IN_PROGRESS", 3, "2026-03-20T09:00:00Z", "2026-03-20T09:05:00Z", "temporary failure");
    Instant now = Instant.parse("2026-03-20T09:10:00Z");

    outboxRepository.markSent(id, now);

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM auth_outbox_events WHERE id = ?", id);
    assertThat(row.get("status")).isEqualTo("SENT");
    assertThat(((Integer) row.get("attempts"))).isEqualTo(3);
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(now);
    assertThat(((Timestamp) row.get("sent_at")).toInstant()).isEqualTo(now);
    assertThat(row.get("last_error")).isNull();
  }

  @Test
  void markFailed_shouldIncrementAttemptsAndSanitizeErrorForInProgressEvents() {
    UUID id = insertEvent("IN_PROGRESS", 1, "2026-03-20T09:00:00Z", "2026-03-20T09:05:00Z", null);
    Instant now = Instant.parse("2026-03-20T09:20:00Z");

    outboxRepository.markFailed(id, "  timeout\n\n contacting    kafka  ", now);

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM auth_outbox_events WHERE id = ?", id);
    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat(((Integer) row.get("attempts"))).isEqualTo(2);
    assertThat(row.get("last_error")).isEqualTo("timeout contacting kafka");
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(now);
    assertThat(row.get("sent_at")).isNull();
  }

  @Test
  void markDead_shouldIncrementAttemptsAndTruncateErrorToDatabaseLimit() {
    UUID id = insertEvent("IN_PROGRESS", 4, "2026-03-20T09:00:00Z", "2026-03-20T09:05:00Z", null);
    Instant now = Instant.parse("2026-03-20T09:30:00Z");
    String longError = ("x ").repeat(3000);

    outboxRepository.markDead(id, longError, now);

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM auth_outbox_events WHERE id = ?", id);
    assertThat(row.get("status")).isEqualTo("DEAD");
    assertThat(((Integer) row.get("attempts"))).isEqualTo(5);
    assertThat(((String) row.get("last_error")).length()).isEqualTo(2048);
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(now);
    assertThat(row.get("sent_at")).isNull();
  }

  @Test
  void terminalAndFailureTransitions_shouldIgnoreRowsThatAreNotInProgress() {
    UUID pending = insertEvent("PENDING", 0, "2026-03-20T09:00:00Z", "2026-03-20T09:05:00Z", null);
    Instant now = Instant.parse("2026-03-20T09:40:00Z");

    outboxRepository.markSent(pending, now);
    outboxRepository.markFailed(pending, "should not update", now);
    outboxRepository.markDead(pending, "should not update", now);

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM auth_outbox_events WHERE id = ?", pending);
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(((Integer) row.get("attempts"))).isEqualTo(0);
    assertThat(row.get("last_error")).isNull();
    assertThat(((Timestamp) row.get("updated_at")).toInstant())
        .isEqualTo(Instant.parse("2026-03-20T09:05:00Z"));
    assertThat(row.get("sent_at")).isNull();
  }

  private UUID insertEvent(
      String status, int attempts, String createdAtIso, String updatedAtIso, String lastError) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
            INSERT INTO auth_outbox_events (
              id, aggregate_type, aggregate_id, event_type, topic, key, payload_json,
              status, attempts, last_error, created_at, updated_at, sent_at
            ) VALUES (?, 'USER', ?, 'USER_REGISTERED', 'qomo.email.commands', ?, '{"event":"USER_REGISTERED"}',
              ?, ?, ?, ?, ?, NULL)
            """,
        id,
        UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
        "user:" + id,
        status,
        attempts,
        lastError,
        Timestamp.from(Instant.parse(createdAtIso)),
        Timestamp.from(Instant.parse(updatedAtIso)));
    return id;
  }

  private void assertStatusAndUpdatedAt(UUID id, String status, Instant updatedAt) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, updated_at FROM auth_outbox_events WHERE id = ?", id);
    assertThat(row.get("status")).isEqualTo(status);
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(updatedAt);
  }
}
