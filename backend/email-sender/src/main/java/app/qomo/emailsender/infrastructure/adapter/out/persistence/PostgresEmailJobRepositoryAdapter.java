package app.qomo.emailsender.infrastructure.adapter.out.persistence;

import app.qomo.emailsender.application.port.out.EmailJobRepositoryPort;
import app.qomo.emailsender.domain.model.EmailJobRecord;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

/**
 * {@link EmailJobRepositoryPort} implementation backed by the PostgreSQL {@code email_jobs} table.
 *
 * <p>This adapter encapsulates JDBC access, SQL shape, persisted status values, timestamp columns,
 * encrypted payload storage, recipient fingerprint storage, and PostgreSQL row-claiming semantics.
 * The application layer sees only durable job creation, lifecycle transitions, and retry claims.
 *
 * <p>Writes performed here are the durable side effects of the email workflow. Idempotent creation
 * relies on the database enforcing uniqueness for the inserted job key; this adapter interprets a
 * duplicate-key failure during insert as an already persisted job.
 */
@Repository
public class PostgresEmailJobRepositoryAdapter implements EmailJobRepositoryPort {

  private final JdbcTemplate jdbcTemplate;

  public PostgresEmailJobRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Inserts a new {@code PENDING} row with zero attempts and encrypted payload material.
   *
   * <p>The raw recipient and payload are expected to have been transformed before this boundary is
   * called: only {@code toEmailFp}, ciphertext, and nonce are stored. Any {@link
   * DuplicateKeyException} raised by the insert is treated as evidence that the event was already
   * represented in {@code email_jobs}; no existing row is updated in that case.
   *
   * @return {@code true} when the row was inserted, {@code false} when the insert hit a duplicate
   *     key
   */
  @Override
  public boolean tryCreatePending(
      UUID eventId,
      String correlationId,
      String type,
      String template,
      String toEmailFp,
      byte[] payloadEnc,
      byte[] payloadNonce,
      Instant now) {
    try {
      var tsNow = Timestamp.from(now);

      jdbcTemplate.update(
          """
              INSERT INTO email_jobs (
                event_id, correlation_id, type, template, to_email_fp,
                status, attempts,
                payload_enc, payload_nonce,
                created_at, updated_at
              ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
              """,
          eventId,
          new SqlParameterValue(Types.VARCHAR, correlationId),
          type,
          template,
          toEmailFp,
          payloadEnc,
          payloadNonce,
          tsNow,
          tsNow);
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }

  /**
   * Persists the successful delivery transition for the job identified by {@code eventId}.
   *
   * <p>The update sets {@code status}, {@code sent_at}, and {@code updated_at}. It is keyed only by
   * {@code event_id}, so this adapter does not enforce a previous status or report whether a row
   * was matched.
   */
  @Override
  public void markSent(UUID eventId, Instant now) {

    var tsNow = Timestamp.from(now);

    jdbcTemplate.update(
        "UPDATE email_jobs SET status = 'SENT', sent_at = ?, updated_at = ? WHERE event_id = ?",
        tsNow,
        tsNow,
        eventId);
  }

  /**
   * Records a failed processing attempt and leaves the job eligible for later retry decisions.
   *
   * <p>The update moves the row to {@code FAILED}, increments {@code attempts}, stores the supplied
   * diagnostic text, and refreshes {@code updated_at}. It does not validate the current status or
   * expose the affected-row count to the application layer.
   */
  @Override
  public void markFailed(UUID eventId, String error, Instant now) {

    var tsNow = Timestamp.from(now);

    jdbcTemplate.update(
        """
            UPDATE email_jobs
            SET status = 'FAILED', attempts = attempts + 1, last_error = ?, updated_at = ?
            WHERE event_id = ?
            """,
        error,
        tsNow,
        eventId);
  }

  /**
   * Atomically claims retryable jobs from {@code email_jobs} for the current processing pass.
   *
   * <p>Candidates are {@code FAILED} or still-{@code PENDING} rows with attempts below {@code
   * maxAttempts} and an {@code updated_at} timestamp older than {@code olderThan}. PostgreSQL
   * {@code FOR UPDATE SKIP LOCKED} lets concurrent workers skip rows already claimed by another
   * transaction, and the oldest candidates are preferred first. The claim itself only refreshes
   * {@code updated_at}; delivery state and attempt counters are changed by the later lifecycle
   * methods.
   *
   * @return claimed jobs in retry order, or an empty list when no row currently satisfies the
   *     criteria
   */
  @Override
  public List<EmailJobRecord> claimRetryCandidates(
      int maxAttempts, Instant olderThan, int limit, Instant now) {
    return jdbcTemplate.query(
        """
             WITH candidates AS (
               SELECT event_id
               FROM email_jobs
               WHERE status IN ('FAILED', 'PENDING')
                 AND attempts < ?
                 AND updated_at < ?
               ORDER BY updated_at ASC
               FOR UPDATE SKIP LOCKED
               LIMIT ?
             )
             UPDATE email_jobs jobs
             SET updated_at = ?
             FROM candidates
             WHERE jobs.event_id = candidates.event_id
             RETURNING jobs.event_id,
                       jobs.correlation_id,
                       jobs.type,
                       jobs.template,
                       jobs.to_email_fp,
                       jobs.payload_enc,
                       jobs.payload_nonce,
                       jobs.attempts
            """,
        (rs, rowNum) ->
            new EmailJobRecord(
                rs.getObject("event_id", UUID.class),
                rs.getString("correlation_id"),
                rs.getString("type"),
                rs.getString("template"),
                rs.getString("to_email_fp"),
                rs.getBytes("payload_enc"),
                rs.getBytes("payload_nonce"),
                rs.getInt("attempts")),
        maxAttempts,
        Timestamp.from(olderThan),
        limit,
        Timestamp.from(now));
  }

  /**
   * Persists the terminal failure transition for a job that should no longer be retried.
   *
   * <p>The row is moved to {@code DEAD}, {@code attempts} is incremented for the final failed pass,
   * and the supplied error is stored as the last failure. As with the other state transitions, this
   * update is keyed by {@code event_id} without checking the previous status.
   */
  @Override
  public void markDead(UUID eventId, String error, Instant now) {
    jdbcTemplate.update(
        """
            UPDATE email_jobs
            SET status = 'DEAD', attempts = attempts + 1, last_error = ?, updated_at = ?
            WHERE event_id = ?
            """,
        error,
        Timestamp.from(now),
        eventId);
  }
}
