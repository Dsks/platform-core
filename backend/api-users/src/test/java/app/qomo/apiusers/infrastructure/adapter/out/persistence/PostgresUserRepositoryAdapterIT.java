package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.qomo.apiusers.TestContainersConfig;
import app.qomo.apiusers.domain.constant.SystemRoleIds;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
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
class PostgresUserRepositoryAdapterIT {

  @Autowired
  private UserRepositoryPort userRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.update("TRUNCATE TABLE auth_verification_tokens, auth_users_roles, auth_users");
  }

  @Test
  void save_shouldPersistAndReadBackUserWithRolesAndNullableLastLogin() {
    UserId userId = new UserId(UUID.fromString("0b8cc67d-c9dd-4f32-b042-4a16c350e0a7"));
    Instant now = Instant.parse("2026-03-25T10:00:00Z");

    User user = User.createNew(userId, Email.of("alice@example.com"), new PasswordHash("hash-1"),
        now);
    user.addRole(Role.user(SystemRoleIds.USER), now);
    user.addRole(Role.admin(SystemRoleIds.ADMIN), now);

    userRepository.save(user);

    assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
    User stored = userRepository.findByEmail("alice@example.com").orElseThrow();

    assertThat(stored.id()).isEqualTo(userId);
    assertThat(stored.email().value()).isEqualTo("alice@example.com");
    assertThat(stored.passwordHash().value()).isEqualTo("hash-1");
    assertThat(stored.isActive()).isTrue();
    assertThat(stored.isVerified()).isFalse();
    assertThat(stored.lastLogin()).isNull();
    assertThat(stored.createdAt()).isEqualTo(now);
    assertThat(stored.updatedAt()).isEqualTo(now);
    assertThat(stored.roles()).extracting(Role::name).containsExactlyInAnyOrder("USER", "ADMIN");

    Timestamp lastLogin = jdbcTemplate.queryForObject(
        "SELECT last_login FROM auth_users WHERE id = ?",
        Timestamp.class,
        userId.value());
    assertThat(lastLogin).isNull();
  }

  @Test
  void findMethods_shouldReturnEmptyWhenUserDoesNotExist() {
    UserId unknown = new UserId(UUID.fromString("09bec6df-efef-4f0f-83bc-b79fdbcb56e4"));

    assertThat(userRepository.findById(unknown)).isEmpty();
    assertThat(userRepository.findByEmail("missing@example.com")).isEmpty();
    assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
  }

  @Test
  void save_onExistingUser_shouldUpsertFieldsAndReplaceRoleAssignments() {
    UserId id = new UserId(UUID.fromString("4f69f671-dcb8-40fe-95f5-1df0f584b8d2"));
    Instant createdAt = Instant.parse("2026-03-20T09:00:00Z");
    Instant firstUpdate = Instant.parse("2026-03-20T09:01:00Z");
    Instant secondUpdate = Instant.parse("2026-03-20T10:30:00Z");

    User initial = User.restore(
        id,
        Email.of("bob@example.com"),
        new PasswordHash("hash-old"),
        true,
        false,
        null,
        createdAt,
        firstUpdate,
        Set.of(Role.user(SystemRoleIds.USER)));
    userRepository.save(initial);

    User updated = User.restore(
        id,
        Email.of("bob+updated@example.com"),
        new PasswordHash("hash-new"),
        false,
        true,
        Instant.parse("2026-03-20T10:00:00Z"),
        createdAt,
        secondUpdate,
        Set.of(Role.admin(SystemRoleIds.ADMIN)));

    userRepository.save(updated);

    User persisted = userRepository.findById(id).orElseThrow();
    assertThat(persisted.email().value()).isEqualTo("bob+updated@example.com");
    assertThat(persisted.passwordHash().value()).isEqualTo("hash-new");
    assertThat(persisted.isActive()).isFalse();
    assertThat(persisted.isVerified()).isTrue();
    assertThat(persisted.lastLogin()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
    assertThat(persisted.updatedAt()).isEqualTo(secondUpdate);
    assertThat(persisted.roles()).extracting(Role::name).containsExactly("ADMIN");

    Integer roleLinks = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM auth_users_roles WHERE user_id = ?",
        Integer.class,
        id.value());
    assertThat(roleLinks).isEqualTo(1);
  }

  @Test
  void setVerified_shouldUpdateVerificationFlagAndUpdatedAtTimestamp() {
    UserId id = new UserId(UUID.fromString("fce85bd9-702b-44a8-a874-415ec7da6b65"));
    Instant base = Instant.parse("2026-03-21T08:00:00Z");
    User user = User.createNew(id, Email.of("verify-me@example.com"), new PasswordHash("hash"),
        base);
    user.addRole(Role.user(SystemRoleIds.USER), base);
    userRepository.save(user);

    Instant verificationInstant = Instant.parse("2026-03-21T09:00:00Z");
    userRepository.setVerified(id, verificationInstant);

    User persisted = userRepository.findById(id).orElseThrow();
    assertThat(persisted.isVerified()).isTrue();
    assertThat(persisted.updatedAt()).isEqualTo(verificationInstant);
  }
}