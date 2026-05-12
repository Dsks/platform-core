package app.platformcore.emailsender.infrastructure.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.platformcore.emailsender.TestContainersConfig;
import app.platformcore.emailsender.domain.model.EmailJobRecord;
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
class PostgresEmailJobRepositoryAdapterIT {

  @Autowired private PostgresEmailJobRepositoryAdapter repository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTable() {
    jdbcTemplate.update("DELETE FROM email_jobs");
  }

  @Test
  void tryCreatePending_persistsInitialJobAndReturnsTrue() {
    UUID eventId = UUID.fromString("4ce6a6cb-e7b9-4dce-b722-6314e48d5406");
    Instant now = Instant.parse("2026-03-15T10:15:30Z");
    byte[] payloadEnc = new byte[] {1, 2, 3};
    byte[] payloadNonce = new byte[] {9, 8, 7};

    boolean created =
        repository.tryCreatePending(
            eventId,
            "corr-100",
            "EMAIL_VERIFICATION_REQUESTED",
            "EMAIL_VERIFICATION",
            "fp-100",
            payloadEnc,
            payloadNonce,
            now);

    assertTrue(created);

    Map<String, Object> row = findRow(eventId);
    assertEquals("PENDING", row.get("status"));
    assertEquals(0, row.get("attempts"));
    assertNull(row.get("last_error"));
    assertNull(row.get("sent_at"));
    assertEquals(Timestamp.from(now), row.get("created_at"));
    assertEquals(Timestamp.from(now), row.get("updated_at"));
    assertArrayEquals(payloadEnc, (byte[]) row.get("payload_enc"));
    assertArrayEquals(payloadNonce, (byte[]) row.get("payload_nonce"));
  }

  @Test
  void tryCreatePending_whenEventIdAlreadyExists_deduplicatesAndKeepsOriginalRow() {
    UUID eventId = UUID.fromString("98e60be9-9345-4ec7-a132-f04628573409");
    Instant originalNow = Instant.parse("2026-03-15T10:00:00Z");
    Instant duplicateNow = Instant.parse("2026-03-15T12:00:00Z");

    boolean firstInsert =
        repository.tryCreatePending(
            eventId,
            "corr-original",
            "EMAIL_VERIFICATION_REQUESTED",
            "EMAIL_VERIFICATION",
            "fp-original",
            new byte[] {1, 1, 1},
            new byte[] {2, 2, 2},
            originalNow);

    boolean duplicatedInsert =
        repository.tryCreatePending(
            eventId,
            "corr-duplicate",
            "OTHER_TYPE",
            "OTHER_TEMPLATE",
            "fp-duplicate",
            new byte[] {9, 9, 9},
            new byte[] {8, 8, 8},
            duplicateNow);

    assertTrue(firstInsert);
    assertFalse(duplicatedInsert);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM email_jobs WHERE event_id = ?", Integer.class, eventId);
    assertEquals(1, count);

    Map<String, Object> row = findRow(eventId);
    assertEquals("corr-original", row.get("correlation_id"));
    assertEquals("EMAIL_VERIFICATION_REQUESTED", row.get("type"));
    assertEquals("EMAIL_VERIFICATION", row.get("template"));
    assertEquals("fp-original", row.get("to_email_fp"));
    assertEquals(Timestamp.from(originalNow), row.get("created_at"));
    assertEquals(Timestamp.from(originalNow), row.get("updated_at"));
    assertArrayEquals(new byte[] {1, 1, 1}, (byte[]) row.get("payload_enc"));
    assertArrayEquals(new byte[] {2, 2, 2}, (byte[]) row.get("payload_nonce"));
  }

  @Test
  void claimRetryCandidates_returnsOnlyEligibleByStatusAttemptsAgeAndMarksUpdatedAt() {
    Instant base = Instant.parse("2026-03-16T00:00:00Z");
    Instant olderThan = base.minusSeconds(120);
    Instant claimNow = base;

    UUID eligibleFailed =
        insertJob(
            UUID.fromString("44f4e700-f3d1-4f4f-9a45-1d4d59f638e8"),
            "FAILED",
            1,
            olderThan.minusSeconds(5),
            null);

    UUID eligiblePending =
        insertJob(
            UUID.fromString("d7f4cd5d-70ef-4dc5-9658-2e62a9c57240"),
            "PENDING",
            0,
            olderThan.minusSeconds(10),
            null);

    insertJob(
        UUID.fromString("2c35d4df-09cb-4f08-8f68-5ce6dc6fcd42"),
        "FAILED",
        3,
        olderThan.minusSeconds(10),
        null);
    insertJob(
        UUID.fromString("84b6e2ec-c9f0-40dd-bf0b-b7ca2136ea7e"),
        "FAILED",
        1,
        olderThan.plusSeconds(30),
        null);
    insertJob(
        UUID.fromString("cb8cf2d6-f3f8-4234-8e53-a85090af2cf8"),
        "SENT",
        0,
        olderThan.minusSeconds(10),
        base.minusSeconds(10));

    List<EmailJobRecord> claimed = repository.claimRetryCandidates(3, olderThan, 10, claimNow);

    assertEquals(2, claimed.size());
    List<UUID> claimedIds =
        claimed.stream()
            .map(EmailJobRecord::eventId)
            .sorted(Comparator.comparing(UUID::toString))
            .toList();
    assertEquals(
        List.of(eligiblePending, eligibleFailed).stream()
            .sorted(Comparator.comparing(UUID::toString))
            .toList(),
        claimedIds);

    for (UUID eventId : claimedIds) {
      Map<String, Object> row = findRow(eventId);
      assertEquals(Timestamp.from(claimNow), row.get("updated_at"));
    }
  }

  @Test
  void claimRetryCandidates_respectsLimitAndOldestUpdatedAtFirst() {
    Instant olderThan = Instant.parse("2026-03-16T10:00:00Z");
    Instant claimNow = Instant.parse("2026-03-16T10:30:00Z");

    UUID oldest =
        insertJob(
            UUID.fromString("f34c6d1f-97b2-4d57-bf70-5e34018fc0c2"),
            "FAILED",
            0,
            olderThan.minusSeconds(120),
            null);

    UUID secondOldest =
        insertJob(
            UUID.fromString("2bf0348f-fca0-4cec-8fd2-c2c592f43fd9"),
            "PENDING",
            0,
            olderThan.minusSeconds(60),
            null);

    UUID newestEligible =
        insertJob(
            UUID.fromString("c46c8ed5-5d2f-4148-8d52-e57eb4785d95"),
            "FAILED",
            0,
            olderThan.minusSeconds(30),
            null);

    List<EmailJobRecord> claimed = repository.claimRetryCandidates(3, olderThan, 2, claimNow);

    assertEquals(2, claimed.size());
    assertEquals(
        List.of(oldest, secondOldest), claimed.stream().map(EmailJobRecord::eventId).toList());

    assertEquals(Timestamp.from(claimNow), findRow(oldest).get("updated_at"));
    assertEquals(Timestamp.from(claimNow), findRow(secondOldest).get("updated_at"));
    assertEquals(
        Timestamp.from(olderThan.minusSeconds(30)), findRow(newestEligible).get("updated_at"));
  }

  @Test
  void markSent_transitionsStatusAndSetsSentAtAndUpdatedAtWithoutChangingAttempts() {
    UUID eventId = UUID.fromString("ebf2cdc8-1a4b-4c70-a215-37466a0ac6f3");
    Instant createdAt = Instant.parse("2026-03-16T10:00:00Z");
    Instant sentNow = Instant.parse("2026-03-16T11:00:00Z");
    insertJob(eventId, "PENDING", 2, createdAt, null);

    repository.markSent(eventId, sentNow);

    Map<String, Object> row = findRow(eventId);
    assertEquals("SENT", row.get("status"));
    assertEquals(2, row.get("attempts"));
    assertEquals(Timestamp.from(sentNow), row.get("sent_at"));
    assertEquals(Timestamp.from(sentNow), row.get("updated_at"));
  }

  @Test
  void markFailed_andMarkDead_incrementAttemptsAndPersistLastErrorAndUpdatedAt() {
    UUID failedEvent = UUID.fromString("e70680bf-7cf7-4742-aed3-5d5c5feff8d8");
    UUID deadEvent = UUID.fromString("8d933306-d787-43f4-b8b5-a5e501eff379");
    Instant base = Instant.parse("2026-03-16T12:00:00Z");
    insertJob(failedEvent, "PENDING", 0, base.minusSeconds(30), null);
    insertJob(deadEvent, "FAILED", 2, base.minusSeconds(40), null);

    Instant failedNow = base;
    String failedError = "smtp timeout";
    repository.markFailed(failedEvent, failedError, failedNow);

    Map<String, Object> failedRow = findRow(failedEvent);
    assertEquals("FAILED", failedRow.get("status"));
    assertEquals(1, failedRow.get("attempts"));
    assertEquals(failedError, failedRow.get("last_error"));
    assertEquals(Timestamp.from(failedNow), failedRow.get("updated_at"));
    assertNull(failedRow.get("sent_at"));

    Instant deadNow = base.plusSeconds(90);
    String deadError = "max_attempts_exceeded";
    repository.markDead(deadEvent, deadError, deadNow);

    Map<String, Object> deadRow = findRow(deadEvent);
    assertEquals("DEAD", deadRow.get("status"));
    assertEquals(3, deadRow.get("attempts"));
    assertEquals(deadError, deadRow.get("last_error"));
    assertEquals(Timestamp.from(deadNow), deadRow.get("updated_at"));
    assertNull(deadRow.get("sent_at"));
  }

  private UUID insertJob(
      UUID eventId, String status, int attempts, Instant updatedAt, Instant sentAt) {
    jdbcTemplate.update(
        """
            INSERT INTO email_jobs (
              event_id, correlation_id, type, template, to_email_fp,
              status, attempts, last_error,
              payload_enc, payload_nonce,
              created_at, updated_at, sent_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        eventId,
        "corr-" + eventId,
        "EMAIL_VERIFICATION_REQUESTED",
        "EMAIL_VERIFICATION",
        "fp-" + eventId,
        status,
        attempts,
        null,
        new byte[] {1, 2, 3},
        new byte[] {4, 5, 6},
        Timestamp.from(updatedAt.minusSeconds(10)),
        Timestamp.from(updatedAt),
        sentAt == null ? null : Timestamp.from(sentAt));
    return eventId;
  }

  private Map<String, Object> findRow(UUID eventId) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT event_id, correlation_id, type, template, to_email_fp, status, attempts,
                   last_error, payload_enc, payload_nonce, created_at, updated_at, sent_at
            FROM email_jobs
            WHERE event_id = ?
            """,
            eventId);
    assertNotNull(row);
    return row;
  }
}
