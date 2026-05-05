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

/**
 * Outbound adapter for {@link OutboxRepositoryPort} backed by the PostgreSQL {@code
 * auth_outbox_events} table.
 *
 * <p>It implements the persistence side of the transactional outbox contract for application
 * services and publishing jobs: events are inserted as {@code PENDING}, claimed as {@code
 * IN_PROGRESS}, and then marked {@code SENT}, {@code FAILED}, or {@code DEAD}. Its side effects are
 * SQL inserts and status updates on the outbox table; actual broker publication is handled by the
 * outbox publisher port.
 */
public class PostgresOutboxRepositoryAdapter implements OutboxRepositoryPort {

  private static final int LAST_ERROR_MAX_LENGTH = 2048;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Creates the repository adapter using Spring's JDBC abstraction.
   *
   * @param jdbcTemplate JDBC client used for PostgreSQL reads and writes
   */
  public PostgresOutboxRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Inserts a new outbox record in {@code PENDING} status.
   *
   * @param event domain outbox event carrying aggregate metadata, target topic, key, payload JSON,
   *     and creation timestamps
   */
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

  /**
   * Atomically claims a batch of publishable outbox events.
   *
   * <p>The query selects {@code PENDING} and {@code FAILED} rows older than the supplied threshold,
   * orders them by creation time, and uses {@code FOR UPDATE SKIP LOCKED} so concurrent workers can
   * claim different rows without blocking each other. Claimed rows are moved to {@code IN_PROGRESS}
   * and returned to the caller for publication.
   *
   * @param batchSize maximum number of rows to claim
   * @param olderThan only rows with {@code updated_at} before this instant are eligible
   * @param now timestamp written as the claim update time
   * @return claimed events ordered by their original creation time
   */
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

  /**
   * Marks a claimed event as sent.
   *
   * <p>The update is guarded by {@code status = 'IN_PROGRESS'} so stale workers do not overwrite
   * rows that are no longer owned by the current publication attempt.
   *
   * @param id outbox event identifier
   * @param now timestamp written to {@code updated_at} and {@code sent_at}
   */
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

  /**
   * Marks a claimed event as failed and increments its attempt counter.
   *
   * @param id outbox event identifier
   * @param error recoverable publication error to persist after whitespace compaction and length
   *     limiting
   * @param now timestamp written to {@code updated_at}
   */
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

  /**
   * Marks a claimed event as dead and increments its attempt counter.
   *
   * @param id outbox event identifier
   * @param error terminal publication error to persist after whitespace compaction and length
   *     limiting
   * @param now timestamp written to {@code updated_at}
   */
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

  /**
   * Maps a claimed PostgreSQL row to the domain outbox model used by the publisher.
   *
   * <p>Status, {@code sent_at}, and {@code last_error} are intentionally not part of the returned
   * domain object because claimed rows are always handed off as work items for publication.
   */
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

  /**
   * Normalizes stored error diagnostics so the outbox table does not keep oversized stack traces or
   * multi-line broker errors.
   */
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
