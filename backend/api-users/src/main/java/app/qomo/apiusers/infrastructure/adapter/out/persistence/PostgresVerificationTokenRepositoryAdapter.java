package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import app.qomo.apiusers.application.port.out.VerificationTokenRepositoryPort;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
import app.qomo.apiusers.domain.model.VerificationTokenId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Outbound adapter for {@link VerificationTokenRepositoryPort} backed by the PostgreSQL {@code
 * auth_verification_tokens} table.
 *
 * <p>The adapter manages verification-token lifecycle state for the application layer: hashed token
 * storage, session association, expiration checks, consumption timestamps, retry attempts, and
 * resend/attempt timestamps. Active-token lookups consistently require {@code consumed_at IS NULL}
 * and {@code expires_at} later than the caller-supplied clock value.
 */
public class PostgresVerificationTokenRepositoryAdapter implements VerificationTokenRepositoryPort {

  private static final String SQL_DELETE_BY_USER_AND_TYPE =
      "DELETE FROM auth_verification_tokens WHERE user_id = ? AND type = ?";
  private static final String SQL_INSERT =
      """
          INSERT INTO auth_verification_tokens
          (id, user_id, token, type, session_id, expires_at, created_at, consumed_at, attempts, last_attempt_at, last_sent_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """;
  private static final String SQL_FIND_LATEST_BY_USER_AND_TYPE =
      """
          SELECT id, user_id, token, type, session_id, expires_at, created_at, consumed_at, attempts, last_attempt_at, last_sent_at
          FROM auth_verification_tokens
          WHERE user_id = ? AND type = ?
          ORDER BY created_at DESC
          LIMIT 1
          """;
  private static final String SQL_FIND_LATEST_ACTIVE_BY_USER_AND_TYPE =
      """
          SELECT id, user_id, token, type, session_id, expires_at, created_at, consumed_at, attempts, last_attempt_at, last_sent_at
          FROM auth_verification_tokens
          WHERE user_id = ?
            AND type = ?
            AND consumed_at IS NULL
            AND expires_at > ?
          ORDER BY created_at DESC
          LIMIT 1
          """;
  private static final String SQL_INVALIDATE_ACTIVE_TOKENS =
      """
          UPDATE auth_verification_tokens
          SET consumed_at = ?
          WHERE user_id = ?
            AND type = ?
            AND consumed_at IS NULL
          """;
  private static final String SQL_FIND_ACTIVE_BY_SESSION_AND_TYPE =
      """
          SELECT id, user_id, token, type, session_id, expires_at, created_at, consumed_at, attempts, last_attempt_at, last_sent_at
          FROM auth_verification_tokens
          WHERE session_id = ?
            AND type = ?
            AND consumed_at IS NULL
            AND expires_at > ?
          ORDER BY created_at DESC
          LIMIT 1
          """;
  private static final String SQL_MARK_CONSUMED =
      "UPDATE auth_verification_tokens SET consumed_at = ? WHERE id = ?";
  private static final String SQL_INCREMENT_ATTEMPTS =
      """
          UPDATE auth_verification_tokens
          SET attempts = attempts + 1,
              last_attempt_at = ?
          WHERE id = ?
          """;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Creates the repository adapter using Spring's JDBC abstraction.
   *
   * @param jdbcTemplate JDBC client used for PostgreSQL verification-token operations
   * @throws NullPointerException if {@code jdbcTemplate} is {@code null}
   */
  public PostgresVerificationTokenRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
  }

  /**
   * Inserts a verification token row.
   *
   * <p>The domain token hash is persisted in the table's {@code token} column. Optional lifecycle
   * timestamps such as {@code consumed_at}, {@code last_attempt_at}, and {@code last_sent_at} are
   * stored as SQL {@code NULL} when absent.
   *
   * @param token verification token aggregate to persist
   */
  @Override
  public void save(VerificationToken token) {
    jdbcTemplate.update(
        SQL_INSERT,
        token.id().value(),
        token.userId().value(),
        token.tokenHash(),
        token.type().name(),
        token.sessionId(),
        Timestamp.from(token.expiresAt()),
        Timestamp.from(token.createdAt()),
        toTimestamp(token.consumedAt()),
        token.attempts(),
        toTimestamp(token.lastAttemptAt()),
        toTimestamp(token.lastSentAt()));
  }

  /**
   * Inserts a newly generated verification token.
   *
   * <p>This adapter applies the same insert-only persistence behavior as {@link #save}; any
   * invalidation or deletion of older tokens is expected to be requested explicitly through the
   * corresponding repository method.
   *
   * @param token newly generated verification token aggregate
   */
  @Override
  public void saveNewToken(VerificationToken token) {
    save(token);
  }

  /**
   * Deletes all verification tokens for a user and token type.
   *
   * @param userId owner of the token rows to delete
   * @param type verification flow type to delete
   */
  @Override
  public void deleteByUserAndType(UserId userId, VerificationToken.Type type) {
    jdbcTemplate.update(SQL_DELETE_BY_USER_AND_TYPE, userId.value(), type.name());
  }

  /**
   * Finds the most recently created token for a user and type, regardless of expiration or
   * consumption state.
   *
   * @param userId owner of the token row
   * @param type verification flow type to search
   * @return latest token ordered by {@code created_at DESC}, or {@link Optional#empty()} if none
   *     exists
   */
  @Override
  public Optional<VerificationToken> findLatestByUserAndType(
      UserId userId, VerificationToken.Type type) {
    return jdbcTemplate
        .query(
            SQL_FIND_LATEST_BY_USER_AND_TYPE,
            (rs, rowNum) -> mapToken(rs),
            userId.value(),
            type.name())
        .stream()
        .findFirst();
  }

  /**
   * Finds the latest currently active token for a user and type.
   *
   * <p>A token is considered active by this SQL adapter when it has not been consumed and its
   * expiration timestamp is later than {@code now}. Results are ordered newest first and limited to
   * one row.
   *
   * @param userId owner of the token row
   * @param type verification flow type to search
   * @param now clock value used for expiration comparison
   * @return latest active token, or {@link Optional#empty()} when no active row exists
   */
  @Override
  public Optional<VerificationToken> findLatestActiveByUserId(
      UserId userId, VerificationToken.Type type, Instant now) {
    return jdbcTemplate
        .query(
            SQL_FIND_LATEST_ACTIVE_BY_USER_AND_TYPE,
            (rs, rowNum) -> mapToken(rs),
            userId.value(),
            type.name(),
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  /**
   * Invalidates all unconsumed tokens for a user and type by setting {@code consumed_at}.
   *
   * @param userId owner of the token rows
   * @param type verification flow type to invalidate
   * @param now timestamp written as the consumption time
   */
  @Override
  public void invalidateActiveTokens(UserId userId, VerificationToken.Type type, Instant now) {
    // Consuming old rows preserves token history while making previous codes unusable.
    jdbcTemplate.update(
        SQL_INVALIDATE_ACTIVE_TOKENS, Timestamp.from(now), userId.value(), type.name());
  }

  /**
   * Finds the latest active token associated with a session and token type.
   *
   * <p>The lookup uses the same active-state definition as user-based active lookups: unconsumed
   * and not expired at the supplied clock value.
   *
   * @param sessionId verification session identifier stored with the token
   * @param type verification flow type to search
   * @param now clock value used for expiration comparison
   * @return latest active session token, or {@link Optional#empty()} when no active row exists
   */
  @Override
  public Optional<VerificationToken> findActiveBySessionAndType(
      UUID sessionId, VerificationToken.Type type, Instant now) {
    return jdbcTemplate
        .query(
            SQL_FIND_ACTIVE_BY_SESSION_AND_TYPE,
            (rs, rowNum) -> mapToken(rs),
            sessionId,
            type.name(),
            Timestamp.from(now))
        .stream()
        .findFirst();
  }

  /**
   * Marks one token as consumed.
   *
   * @param tokenId token row identifier
   * @param consumedAt timestamp written to {@code consumed_at}
   */
  @Override
  public void markConsumed(UUID tokenId, Instant consumedAt) {
    jdbcTemplate.update(SQL_MARK_CONSUMED, Timestamp.from(consumedAt), tokenId);
  }

  /**
   * Records a verification attempt for a token.
   *
   * @param tokenId token row identifier
   * @param attemptedAt timestamp written to {@code last_attempt_at} while incrementing {@code
   *     attempts}
   */
  @Override
  public void incrementAttempts(UUID tokenId, Instant attemptedAt) {
    jdbcTemplate.update(SQL_INCREMENT_ATTEMPTS, Timestamp.from(attemptedAt), tokenId);
  }

  /**
   * Maps SQL token lifecycle columns to the domain model.
   *
   * <p>Nullable SQL timestamps are preserved as {@code null} domain instants; enum state is
   * restored from the stored token type name.
   */
  private VerificationToken mapToken(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new VerificationToken(
        new VerificationTokenId(rs.getObject("id", UUID.class)),
        new UserId(rs.getObject("user_id", UUID.class)),
        rs.getString("token"),
        VerificationToken.Type.valueOf(rs.getString("type")),
        rs.getObject("session_id", UUID.class),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getTimestamp("created_at").toInstant(),
        toInstant(rs.getTimestamp("consumed_at")),
        rs.getInt("attempts"),
        toInstant(rs.getTimestamp("last_attempt_at")),
        toInstant(rs.getTimestamp("last_sent_at")));
  }

  /** Converts nullable token lifecycle instants to JDBC timestamps. */
  private Timestamp toTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  /** Converts nullable JDBC timestamps without changing absent lifecycle state. */
  private Instant toInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
