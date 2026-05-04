package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
import app.qomo.apiusers.domain.model.VerificationTokenId;
import app.qomo.apiusers.domain.port.out.VerificationTokenRepositoryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

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

  public PostgresVerificationTokenRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
  }

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

  @Override
  public void saveNewToken(VerificationToken token) {
    save(token);
  }

  @Override
  public void deleteByUserAndType(UserId userId, VerificationToken.Type type) {
    jdbcTemplate.update(SQL_DELETE_BY_USER_AND_TYPE, userId.value(), type.name());
  }

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

  @Override
  public void invalidateActiveTokens(UserId userId, VerificationToken.Type type, Instant now) {
    jdbcTemplate.update(
        SQL_INVALIDATE_ACTIVE_TOKENS, Timestamp.from(now), userId.value(), type.name());
  }

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

  @Override
  public void markConsumed(UUID tokenId, Instant consumedAt) {
    jdbcTemplate.update(SQL_MARK_CONSUMED, Timestamp.from(consumedAt), tokenId);
  }

  @Override
  public void incrementAttempts(UUID tokenId, Instant attemptedAt) {
    jdbcTemplate.update(SQL_INCREMENT_ATTEMPTS, Timestamp.from(attemptedAt), tokenId);
  }

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

  private Timestamp toTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private Instant toInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
