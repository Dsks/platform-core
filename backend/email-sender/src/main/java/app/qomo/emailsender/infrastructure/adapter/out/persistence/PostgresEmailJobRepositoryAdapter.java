package app.qomo.emailsender.infrastructure.adapter.out.persistence;

import app.qomo.emailsender.domain.model.EmailJobRecord;
import app.qomo.emailsender.domain.port.out.EmailJobRepositoryPort;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresEmailJobRepositoryAdapter implements EmailJobRepositoryPort {

  private final JdbcTemplate jdbcTemplate;

  public PostgresEmailJobRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

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

  @Override
  public void markSent(UUID eventId, Instant now) {

    var tsNow = Timestamp.from(now);

    jdbcTemplate.update(
        "UPDATE email_jobs SET status = 'SENT', sent_at = ?, updated_at = ? WHERE event_id = ?",
        tsNow,
        tsNow,
        eventId);
  }

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
