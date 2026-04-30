package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.qomo.apiusers.TestContainersConfig;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
import app.qomo.apiusers.domain.model.VerificationTokenId;
import app.qomo.apiusers.domain.port.out.VerificationTokenRepositoryPort;
import java.sql.Timestamp;
import java.time.Instant;
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
class PostgresVerificationTokenRepositoryAdapterIT {

  @Autowired
  private VerificationTokenRepositoryPort tokenRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.update("TRUNCATE TABLE auth_verification_tokens, auth_users_roles, auth_users");
  }

  @Test
  void save_shouldPersistTokenAndMapNullableColumnsWhenReadingLatest() {
    UserId userId = insertUser("token-user-1@example.com");
    VerificationToken token = new VerificationToken(
        new VerificationTokenId(UUID.fromString("f6d88db3-66aa-4d0e-a22d-3254cba52602")),
        userId,
        "hash-otp-1",
        VerificationToken.Type.EMAIL_VERIFICATION,
        UUID.fromString("fd2d06fb-945d-4d56-9be3-0db693f6e311"),
        Instant.parse("2026-03-24T10:10:00Z"),
        Instant.parse("2026-03-24T10:00:00Z"),
        null,
        0,
        null,
        Instant.parse("2026-03-24T10:00:00Z"));

    tokenRepository.save(token);

    VerificationToken stored = tokenRepository.findLatestByUserAndType(
        userId,
        VerificationToken.Type.EMAIL_VERIFICATION).orElseThrow();

    assertThat(stored.id()).isEqualTo(token.id());
    assertThat(stored.userId()).isEqualTo(userId);
    assertThat(stored.tokenHash()).isEqualTo("hash-otp-1");
    assertThat(stored.type()).isEqualTo(VerificationToken.Type.EMAIL_VERIFICATION);
    assertThat(stored.consumedAt()).isNull();
    assertThat(stored.lastAttemptAt()).isNull();
    assertThat(stored.lastSentAt()).isEqualTo(Instant.parse("2026-03-24T10:00:00Z"));
  }

  @Test
  void findLatestActiveByUserId_shouldReturnNewestTokenThatIsNotConsumedAndNotExpired() {
    UserId userId = insertUser("token-user-2@example.com");
    Instant now = Instant.parse("2026-03-24T11:00:00Z");

    tokenRepository.save(newToken(userId,
        "11111111-1111-4111-8111-111111111111",
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "hash-old-active",
        "2026-03-24T10:00:00Z",
        "2026-03-24T12:00:00Z",
        null));

    tokenRepository.save(newToken(userId,
        "22222222-2222-4222-8222-222222222222",
        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        "hash-expired",
        "2026-03-24T10:30:00Z",
        "2026-03-24T10:59:59Z",
        null));

    tokenRepository.save(newToken(userId,
        "33333333-3333-4333-8333-333333333333",
        "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
        "hash-consumed",
        "2026-03-24T10:50:00Z",
        "2026-03-24T11:30:00Z",
        Instant.parse("2026-03-24T10:55:00Z")));

    VerificationToken newestActive = newToken(userId,
        "44444444-4444-4444-8444-444444444444",
        "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
        "hash-new-active",
        "2026-03-24T10:58:00Z",
        "2026-03-24T11:40:00Z",
        null);
    tokenRepository.save(newestActive);

    VerificationToken found = tokenRepository.findLatestActiveByUserId(
        userId,
        VerificationToken.Type.EMAIL_VERIFICATION,
        now).orElseThrow();

    assertThat(found.id()).isEqualTo(newestActive.id());
    assertThat(found.tokenHash()).isEqualTo("hash-new-active");
  }

  @Test
  void findActiveBySessionAndType_shouldIgnoreExpiredAndConsumedTokens() {
    UserId userId = insertUser("token-user-3@example.com");
    Instant now = Instant.parse("2026-03-24T11:00:00Z");

    UUID activeSession = UUID.fromString("f6b9f76a-150f-4dc2-9d4a-f8a814e0d1d1");
    UUID expiredSession = UUID.fromString("be520ee7-6969-4637-b349-cab2a4546390");
    UUID consumedSession = UUID.fromString("9dd1ec6f-3243-4f24-b17f-a3f19bb488a7");

    tokenRepository.save(new VerificationToken(
        new VerificationTokenId(UUID.fromString("55555555-5555-4555-8555-555555555555")),
        userId,
        "active-hash",
        VerificationToken.Type.EMAIL_VERIFICATION,
        activeSession,
        Instant.parse("2026-03-24T11:15:00Z"),
        Instant.parse("2026-03-24T10:45:00Z"),
        null,
        0,
        null,
        Instant.parse("2026-03-24T10:45:00Z")));

    tokenRepository.save(new VerificationToken(
        new VerificationTokenId(UUID.fromString("66666666-6666-4666-8666-666666666666")),
        userId,
        "expired-hash",
        VerificationToken.Type.PASS_RESET,
        expiredSession,
        Instant.parse("2026-03-24T10:50:00Z"),
        Instant.parse("2026-03-24T10:20:00Z"),
        null,
        0,
        null,
        Instant.parse("2026-03-24T10:20:00Z")));

    tokenRepository.save(new VerificationToken(
        new VerificationTokenId(UUID.fromString("77777777-7777-4777-8777-777777777777")),
        userId,
        "consumed-hash",
        VerificationToken.Type.PASS_RESET,
        consumedSession,
        Instant.parse("2026-03-24T11:20:00Z"),
        Instant.parse("2026-03-24T10:10:00Z"),
        Instant.parse("2026-03-24T10:15:00Z"),
        0,
        null,
        Instant.parse("2026-03-24T10:10:00Z")));

    assertThat(tokenRepository.findActiveBySessionAndType(activeSession,
        VerificationToken.Type.EMAIL_VERIFICATION, now)).isPresent();
    assertThat(tokenRepository.findActiveBySessionAndType(expiredSession,
        VerificationToken.Type.PASS_RESET, now)).isEmpty();
    assertThat(tokenRepository.findActiveBySessionAndType(consumedSession,
        VerificationToken.Type.PASS_RESET, now)).isEmpty();
  }

  @Test
  void invalidateAndMutations_shouldOnlyAffectExpectedRowsAndPersistAttemptsAndConsumption() {
    UserId userId = insertUser("token-user-4@example.com");
    Instant now = Instant.parse("2026-03-24T12:00:00Z");

    VerificationToken activeA = newToken(userId,
        "88888888-8888-4888-8888-888888888888",
        "2a5b2fc8-a6e0-4b7e-a42f-6b9daf16f9e3",
        "active-a",
        "2026-03-24T11:00:00Z",
        "2026-03-24T12:30:00Z",
        null);
    VerificationToken activeB = newToken(userId,
        "99999999-9999-4999-8999-999999999999",
        "f21ad6f2-1f8b-4f17-9408-fde2c6f8dbdd",
        "active-b",
        "2026-03-24T11:10:00Z",
        "2026-03-24T12:30:00Z",
        null);
    VerificationToken consumed = newToken(userId,
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "7bb95c80-3166-4b71-8417-7b88e0ea864f",
        "already-consumed",
        "2026-03-24T11:20:00Z",
        "2026-03-24T12:30:00Z",
        Instant.parse("2026-03-24T11:25:00Z"));

    tokenRepository.save(activeA);
    tokenRepository.save(activeB);
    tokenRepository.save(consumed);

    tokenRepository.invalidateActiveTokens(userId, VerificationToken.Type.EMAIL_VERIFICATION, now);

    Integer invalidatedCount = jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*) FROM auth_verification_tokens
            WHERE user_id = ? AND consumed_at = ?
            """,
        Integer.class,
        userId.value(),
        Timestamp.from(now));
    assertThat(invalidatedCount).isEqualTo(2);

    tokenRepository.incrementAttempts(activeA.id().value(), Instant.parse("2026-03-24T12:05:00Z"));
    tokenRepository.incrementAttempts(activeA.id().value(), Instant.parse("2026-03-24T12:07:00Z"));
    tokenRepository.markConsumed(activeA.id().value(), Instant.parse("2026-03-24T12:08:00Z"));

    var row = jdbcTemplate.queryForMap(
        "SELECT attempts, last_attempt_at, consumed_at FROM auth_verification_tokens WHERE id = ?",
        activeA.id().value());

    assertThat(row.get("attempts")).isEqualTo(2);
    assertThat(((Timestamp) row.get("last_attempt_at")).toInstant())
        .isEqualTo(Instant.parse("2026-03-24T12:07:00Z"));
    assertThat(((Timestamp) row.get("consumed_at")).toInstant())
        .isEqualTo(Instant.parse("2026-03-24T12:08:00Z"));
  }

  @Test
  void deleteByUserAndType_shouldDeleteOnlyRequestedTokenTypeForUser() {
    UserId userId = insertUser("token-user-5@example.com");

    tokenRepository.save(newToken(userId,
        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        "f8ce5284-85d6-4f97-b1cc-d36de95f2dcf",
        "email-token",
        "2026-03-24T11:00:00Z",
        "2026-03-24T13:00:00Z",
        null));

    tokenRepository.save(new VerificationToken(
        new VerificationTokenId(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc")),
        userId,
        "reset-token",
        VerificationToken.Type.PASS_RESET,
        UUID.fromString("9f14ab1c-8a44-4d56-a82d-c2b1af8fc4d5"),
        Instant.parse("2026-03-24T13:00:00Z"),
        Instant.parse("2026-03-24T11:30:00Z"),
        null,
        0,
        null,
        Instant.parse("2026-03-24T11:30:00Z")));

    tokenRepository.deleteByUserAndType(userId, VerificationToken.Type.EMAIL_VERIFICATION);

    Integer total = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM auth_verification_tokens WHERE user_id = ?",
        Integer.class,
        userId.value());
    Integer passReset = jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*) FROM auth_verification_tokens
            WHERE user_id = ? AND type = 'PASS_RESET'
            """,
        Integer.class,
        userId.value());

    assertThat(total).isEqualTo(1);
    assertThat(passReset).isEqualTo(1);
  }

  private UserId insertUser(String email) {
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-03-24T10:00:00Z");
    jdbcTemplate.update(
        """
            INSERT INTO auth_users (id, email, password_hash, is_active, is_verified, last_login, created_at, updated_at)
            VALUES (?, ?, 'hash', TRUE, FALSE, NULL, ?, ?)
            """,
        id,
        email,
        Timestamp.from(now),
        Timestamp.from(now));
    return new UserId(id);
  }

  private VerificationToken newToken(
      UserId userId,
      String id,
      String sessionId,
      String tokenHash,
      String createdAt,
      String expiresAt,
      Instant consumedAt) {
    Instant created = Instant.parse(createdAt);
    return new VerificationToken(
        new VerificationTokenId(UUID.fromString(id)),
        userId,
        tokenHash,
        VerificationToken.Type.EMAIL_VERIFICATION,
        UUID.fromString(sessionId),
        Instant.parse(expiresAt),
        created,
        consumedAt,
        0,
        null,
        created);
  }
}