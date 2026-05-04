package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import app.qomo.apiusers.application.port.out.OutboxRepositoryPort;
import app.qomo.apiusers.domain.model.OutboxEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresOutboxRepositoryAdapter implements OutboxRepositoryPort {

  private static final int LAST_ERROR_MAX_LENGTH = 2048;

  private final JdbcTemplate jdbcTemplate;

  public PostgresOutboxRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void insertPending(OutboxEvent event) {
    jdbcTemplate.update(
        """
            INSERT INTO auth_outbox_events (
              id, aggregate_type, aggregate_id, event_type, topic, key, payload_json, status,
              attempts, last_error, created_at, updated_at, sent_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, NULL, ?, ?, NULL)
            """,
        event.id(),
        event.aggregateType(),
        event.aggregateId(),
        event.eventType(),
        event.topic(),
        event.key(),
        event.payloadJson(),
        event.attempts(),
        Timestamp.from(event.createdAt()),
        Timestamp.from(event.updatedAt()));
  }

  @Override
  public List<OutboxEvent> claimPublishable(int batchSize, Instant olderThan, Instant now) {
    return jdbcTemplate.query(
        """
             WITH claimable AS (
                SELECT id
                  FROM auth_outbox_events
                 WHERE status IN ('PENDING', 'FAILED')
                   AND updated_at < ?
                 ORDER BY created_at ASC
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
              )
              UPDATE auth_outbox_events outbox
                 SET status = 'IN_PROGRESS',
                     updated_at = ?
                FROM claimable
               WHERE outbox.id = claimable.id
              RETURNING outbox.id,
                        outbox.aggregate_type,
                        outbox.aggregate_id,
                        outbox.event_type,
                        outbox.topic,
                        outbox.key,
                        outbox.payload_json,
                        outbox.attempts,
                        outbox.created_at,
                        outbox.updated_at
            """,
        (rs, rowNum) -> map(rs),
        Timestamp.from(olderThan),
        batchSize,
        Timestamp.from(now));
  }

  @Override
  public void markSent(UUID id, Instant now) {
    jdbcTemplate.update(
        """
            UPDATE auth_outbox_events
               SET status = 'SENT',
                   updated_at = ?,
                   sent_at = ?,
                   last_error = NULL
             WHERE id = ?
             AND status = 'IN_PROGRESS'
            """,
        Timestamp.from(now),
        Timestamp.from(now),
        id);
  }

  @Override
  public void markFailed(UUID id, String error, Instant now) {
    jdbcTemplate.update(
        """
            UPDATE auth_outbox_events
               SET status = 'FAILED',
                   attempts = attempts + 1,
                   last_error = ?,
                   updated_at = ?
             WHERE id = ?
             AND status = 'IN_PROGRESS'
            """,
        sanitizeError(error),
        Timestamp.from(now),
        id);
  }

  @Override
  public void markDead(UUID id, String error, Instant now) {
    jdbcTemplate.update(
        """
            UPDATE auth_outbox_events
               SET status = 'DEAD',
                   attempts = attempts + 1,
                   last_error = ?,
                   updated_at = ?
             WHERE id = ?
             AND status = 'IN_PROGRESS'
            """,
        sanitizeError(error),
        Timestamp.from(now),
        id);
  }

  private OutboxEvent map(ResultSet rs) throws SQLException {
    return new OutboxEvent(
        rs.getObject("id", UUID.class),
        rs.getString("aggregate_type"),
        rs.getObject("aggregate_id", UUID.class),
        rs.getString("event_type"),
        rs.getString("topic"),
        rs.getString("key"),
        rs.getString("payload_json"),
        rs.getInt("attempts"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private String sanitizeError(String error) {
    if (error == null) {
      return null;
    }
    String compact = error.replaceAll("\\s+", " ").trim();
    if (compact.length() <= LAST_ERROR_MAX_LENGTH) {
      return compact;
    }
    return compact.substring(0, LAST_ERROR_MAX_LENGTH);
  }
}
