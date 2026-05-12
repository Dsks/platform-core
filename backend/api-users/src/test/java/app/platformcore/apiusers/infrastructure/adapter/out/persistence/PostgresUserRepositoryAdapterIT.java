package app.platformcore.apiusers.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import app.platformcore.apiusers.TestContainersConfig;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.DeletedFilter;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.SortBy;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.SortDirection;
import app.platformcore.apiusers.application.port.out.UserRepositoryPort;
import app.platformcore.apiusers.domain.constant.SystemRoleIds;
import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.PasswordHash;
import app.platformcore.apiusers.domain.model.Role;
import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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

  @Autowired private UserRepositoryPort userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.update("TRUNCATE TABLE auth_verification_tokens, auth_users_roles, auth_users");
  }

  @Test
  void save_shouldPersistAndReadBackUserWithRolesAndNullableLastLogin() {
    UserId userId = new UserId(UUID.fromString("0b8cc67d-c9dd-4f32-b042-4a16c350e0a7"));
    Instant now = Instant.parse("2026-03-25T10:00:00Z");

    User user =
        User.createNew(userId, Email.of("alice@example.com"), new PasswordHash("hash-1"), now);
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

    Timestamp lastLogin =
        jdbcTemplate.queryForObject(
            "SELECT last_login FROM auth_users WHERE id = ?", Timestamp.class, userId.value());
    assertThat(lastLogin).isNull();

    Timestamp deletedAt =
        jdbcTemplate.queryForObject(
            "SELECT deleted_at FROM auth_users WHERE id = ?", Timestamp.class, userId.value());
    assertThat(deletedAt).isNull();
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

    User initial =
        User.restore(
            id,
            Email.of("bob@example.com"),
            new PasswordHash("hash-old"),
            true,
            false,
            null,
            createdAt,
            firstUpdate,
            null,
            Set.of(Role.user(SystemRoleIds.USER)));
    userRepository.save(initial);

    User updated =
        User.restore(
            id,
            Email.of("bob+updated@example.com"),
            new PasswordHash("hash-new"),
            false,
            true,
            Instant.parse("2026-03-20T10:00:00Z"),
            createdAt,
            secondUpdate,
            null,
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

    Integer roleLinks =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_users_roles WHERE user_id = ?", Integer.class, id.value());
    assertThat(roleLinks).isEqualTo(1);
  }

  @Test
  void softDeleteAndAnonymize_shouldSetDeletedAtAndPreserveRoles() {
    UserId id = new UserId(UUID.fromString("e76ff8b1-e8bf-4ed5-a01a-9eb4f3882511"));
    Instant createdAt = Instant.parse("2026-03-22T08:00:00Z");
    Instant lastLogin = Instant.parse("2026-03-22T08:30:00Z");
    Instant initialUpdate = Instant.parse("2026-03-22T09:00:00Z");
    Instant deletionInstant = Instant.parse("2026-03-22T10:00:00Z");
    Email anonymizedEmail = Email.of("deleted-" + id + "@deleted.app");
    PasswordHash unusablePasswordHash = new PasswordHash("deleted:" + id);

    User user =
        User.restore(
            id,
            Email.of("delete-me@example.com"),
            new PasswordHash("hash-before-delete"),
            true,
            true,
            lastLogin,
            createdAt,
            initialUpdate,
            null,
            Set.of(Role.user(SystemRoleIds.USER), Role.admin(SystemRoleIds.ADMIN)));
    userRepository.save(user);

    userRepository.softDeleteAndAnonymize(
        id, anonymizedEmail, unusablePasswordHash, deletionInstant);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
                SELECT email, password_hash, is_active, is_verified, last_login, deleted_at, updated_at
                FROM auth_users
                WHERE id = ?
                """,
            id.value());
    assertThat(row.get("email")).isEqualTo(anonymizedEmail.value());
    assertThat(row.get("password_hash")).isEqualTo(unusablePasswordHash.value());
    assertThat(row.get("is_active")).isEqualTo(false);
    assertThat(row.get("is_verified")).isEqualTo(false);
    assertThat(row.get("last_login")).isNull();
    assertThat(((Timestamp) row.get("deleted_at")).toInstant()).isEqualTo(deletionInstant);
    assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(deletionInstant);

    User persisted = userRepository.findById(id).orElseThrow();
    assertThat(persisted.roles()).extracting(Role::name).containsExactlyInAnyOrder("USER", "ADMIN");

    Integer roleLinks =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_users_roles WHERE user_id = ?", Integer.class, id.value());
    assertThat(roleLinks).isEqualTo(2);
  }

  @Test
  void setVerified_shouldUpdateVerificationFlagAndUpdatedAtTimestamp() {
    UserId id = new UserId(UUID.fromString("fce85bd9-702b-44a8-a874-415ec7da6b65"));
    Instant base = Instant.parse("2026-03-21T08:00:00Z");
    User user =
        User.createNew(id, Email.of("verify-me@example.com"), new PasswordHash("hash"), base);
    user.addRole(Role.user(SystemRoleIds.USER), base);
    userRepository.save(user);

    Instant verificationInstant = Instant.parse("2026-03-21T09:00:00Z");
    userRepository.setVerified(id, verificationInstant);

    User persisted = userRepository.findById(id).orElseThrow();
    assertThat(persisted.isVerified()).isTrue();
    assertThat(persisted.updatedAt()).isEqualTo(verificationInstant);
  }

  @Test
  void findAllPage_shouldReturnAllUsersWithoutSearch() {
    Instant base = Instant.parse("2026-03-24T08:00:00Z");
    saveUser(
        "10000000-0000-4000-8000-000000000001",
        "alpha@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "10000000-0000-4000-8000-000000000002",
        "beta@example.com",
        base.plusSeconds(1),
        Role.admin(SystemRoleIds.ADMIN));
    saveUser(
        "10000000-0000-4000-8000-000000000003",
        "gamma@example.com",
        base.plusSeconds(2),
        Role.of(SystemRoleIds.SUPERADMIN, "SUPERADMIN"));

    var page = userRepository.findAllPage(null, 0, 10);

    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.content())
        .extracting(user -> user.email().value())
        .containsExactly("gamma@example.com", "beta@example.com", "alpha@example.com");
  }

  @Test
  void findAllPage_shouldSearchEmailSubstringCaseInsensitiveAndKeepPageTotals() {
    Instant base = Instant.parse("2026-03-24T09:00:00Z");
    saveUser(
        "20000000-0000-4000-8000-000000000001",
        "alpha.one@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "20000000-0000-4000-8000-000000000002",
        "beta@example.com",
        base.plusSeconds(1),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "20000000-0000-4000-8000-000000000003",
        "another-alpha@example.com",
        base.plusSeconds(2),
        Role.admin(SystemRoleIds.ADMIN));

    var firstPage = userRepository.findAllPage("ALPHA", 0, 1);
    var secondPage = userRepository.findAllPage("ALPHA", 1, 1);

    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.content()).hasSize(1);
    assertThat(secondPage.totalElements()).isEqualTo(2);
    assertThat(secondPage.totalPages()).isEqualTo(2);
    assertThat(secondPage.content()).hasSize(1);
    assertThat(firstPage.content().get(0).email().value()).isEqualTo("another-alpha@example.com");
    assertThat(secondPage.content().get(0).email().value()).isEqualTo("alpha.one@example.com");
  }

  @Test
  void findAllPage_shouldFilterDeletedUsers() {
    Instant base = Instant.parse("2026-03-24T09:30:00Z");
    saveUser(
        "21000000-0000-4000-8000-000000000001",
        "active-user@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "21000000-0000-4000-8000-000000000002",
        "deleted-user@example.com",
        base.plusSeconds(1),
        true,
        true,
        base.plusSeconds(10),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "21000000-0000-4000-8000-000000000003",
        "active-admin@example.com",
        base.plusSeconds(2),
        Role.admin(SystemRoleIds.ADMIN));

    var excluded = userRepository.findAllPage(null, DeletedFilter.EXCLUDE, null, null, 0, 10);
    var only = userRepository.findAllPage(null, DeletedFilter.ONLY, null, null, 0, 10);
    var all = userRepository.findAllPage(null, DeletedFilter.ALL, null, null, 0, 10);

    assertThat(excluded.totalElements()).isEqualTo(2);
    assertThat(excluded.content())
        .extracting(user -> user.email().value())
        .containsExactly("active-admin@example.com", "active-user@example.com");
    assertThat(only.totalElements()).isEqualTo(1);
    assertThat(only.content())
        .extracting(user -> user.email().value())
        .containsExactly("deleted-user@example.com");
    assertThat(all.totalElements()).isEqualTo(3);
  }

  @Test
  void findAllPage_shouldCombineSearchActiveVerifiedAndDeletedFiltersInCountsAndPages() {
    Instant base = Instant.parse("2026-03-24T09:45:00Z");
    saveUser(
        "22000000-0000-4000-8000-000000000001",
        "target-one@example.com",
        base,
        true,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "22000000-0000-4000-8000-000000000002",
        "target-two@example.com",
        base.plusSeconds(1),
        true,
        false,
        null,
        Role.admin(SystemRoleIds.ADMIN));
    saveUser(
        "22000000-0000-4000-8000-000000000003",
        "target-inactive@example.com",
        base.plusSeconds(2),
        false,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "22000000-0000-4000-8000-000000000004",
        "target-verified@example.com",
        base.plusSeconds(3),
        true,
        true,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "22000000-0000-4000-8000-000000000005",
        "other-active@example.com",
        base.plusSeconds(4),
        true,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "22000000-0000-4000-8000-000000000006",
        "target-deleted@example.com",
        base.plusSeconds(5),
        true,
        false,
        base.plusSeconds(20),
        Role.user(SystemRoleIds.USER));

    var firstPage = userRepository.findAllPage("target", DeletedFilter.EXCLUDE, true, false, 0, 1);
    var secondPage = userRepository.findAllPage("target", DeletedFilter.EXCLUDE, true, false, 1, 1);

    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(secondPage.totalElements()).isEqualTo(2);
    assertThat(secondPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.content())
        .extracting(user -> user.email().value())
        .containsExactly("target-two@example.com");
    assertThat(secondPage.content())
        .extracting(user -> user.email().value())
        .containsExactly("target-one@example.com");
  }

  @Test
  void findAllPage_shouldFilterByRoleAndKeepPageTotals() {
    Instant base = Instant.parse("2026-03-24T09:50:00Z");
    saveUser(
        "23000000-0000-4000-8000-000000000001",
        "role-user-one@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "23000000-0000-4000-8000-000000000002",
        "role-admin@example.com",
        base.plusSeconds(1),
        Role.admin(SystemRoleIds.ADMIN));
    saveUser(
        "23000000-0000-4000-8000-000000000003",
        "role-superadmin@example.com",
        base.plusSeconds(2),
        Role.of(SystemRoleIds.SUPERADMIN, "SUPERADMIN"));
    saveUser(
        "23000000-0000-4000-8000-000000000004",
        "role-user-two@example.com",
        base.plusSeconds(3),
        Role.user(SystemRoleIds.USER));

    var firstUserPage =
        userRepository.findAllPage(null, DeletedFilter.EXCLUDE, null, null, "USER", 0, 1);
    var secondUserPage =
        userRepository.findAllPage(null, DeletedFilter.EXCLUDE, null, null, "USER", 1, 1);
    var adminPage =
        userRepository.findAllPage(null, DeletedFilter.EXCLUDE, null, null, "ADMIN", 0, 10);
    var superAdminPage =
        userRepository.findAllPage(null, DeletedFilter.EXCLUDE, null, null, "SUPERADMIN", 0, 10);

    assertThat(firstUserPage.totalElements()).isEqualTo(2);
    assertThat(firstUserPage.totalPages()).isEqualTo(2);
    assertThat(secondUserPage.totalElements()).isEqualTo(2);
    assertThat(secondUserPage.totalPages()).isEqualTo(2);
    assertThat(firstUserPage.content())
        .extracting(user -> user.email().value())
        .containsExactly("role-user-two@example.com");
    assertThat(secondUserPage.content())
        .extracting(user -> user.email().value())
        .containsExactly("role-user-one@example.com");
    assertThat(adminPage.content())
        .extracting(user -> user.email().value())
        .containsExactly("role-admin@example.com");
    assertThat(superAdminPage.content())
        .extracting(user -> user.email().value())
        .containsExactly("role-superadmin@example.com");
  }

  @Test
  void findAllPage_shouldCombineRoleWithSearchDeletedActiveAndVerifiedFilters() {
    Instant base = Instant.parse("2026-03-24T09:55:00Z");
    saveUser(
        "24000000-0000-4000-8000-000000000001",
        "role-target-user@example.com",
        base,
        true,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "24000000-0000-4000-8000-000000000002",
        "role-target-admin@example.com",
        base.plusSeconds(1),
        true,
        false,
        null,
        Role.admin(SystemRoleIds.ADMIN));
    saveUser(
        "24000000-0000-4000-8000-000000000003",
        "role-target-inactive@example.com",
        base.plusSeconds(2),
        false,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "24000000-0000-4000-8000-000000000004",
        "role-target-verified@example.com",
        base.plusSeconds(3),
        true,
        true,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "24000000-0000-4000-8000-000000000005",
        "role-target-deleted@example.com",
        base.plusSeconds(4),
        true,
        false,
        base.plusSeconds(20),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "24000000-0000-4000-8000-000000000006",
        "role-other-user@example.com",
        base.plusSeconds(5),
        true,
        false,
        null,
        Role.user(SystemRoleIds.USER));

    var page =
        userRepository.findAllPage("target", DeletedFilter.EXCLUDE, true, false, "USER", 0, 10);

    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.content())
        .extracting(user -> user.email().value())
        .containsExactly("role-target-user@example.com");
  }

  @Test
  void findAllPage_shouldSortByEmailAscAndDesc() {
    Instant base = Instant.parse("2026-03-24T12:00:00Z");
    saveUser(
        "25000000-0000-4000-8000-000000000001",
        "bravo@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "25000000-0000-4000-8000-000000000002",
        "alpha@example.com",
        base.plusSeconds(1),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "25000000-0000-4000-8000-000000000003",
        "charlie@example.com",
        base.plusSeconds(2),
        Role.user(SystemRoleIds.USER));

    var asc =
        userRepository.findAllPage(
            null, DeletedFilter.EXCLUDE, null, null, null, SortBy.EMAIL, SortDirection.ASC, 0, 10);
    var desc =
        userRepository.findAllPage(
            null, DeletedFilter.EXCLUDE, null, null, null, SortBy.EMAIL, SortDirection.DESC, 0, 10);

    assertThat(asc.content())
        .extracting(user -> user.email().value())
        .containsExactly("alpha@example.com", "bravo@example.com", "charlie@example.com");
    assertThat(desc.content())
        .extracting(user -> user.email().value())
        .containsExactly("charlie@example.com", "bravo@example.com", "alpha@example.com");
  }

  @Test
  void findAllPage_shouldSortByTimestampFieldsWithNullsLast() {
    Instant base = Instant.parse("2026-03-24T12:30:00Z");
    saveUser(
        "26000000-0000-4000-8000-000000000001",
        "created-first@example.com",
        base,
        base.plusSeconds(30),
        base.plusSeconds(300),
        true,
        true,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "26000000-0000-4000-8000-000000000002",
        "created-second@example.com",
        base.plusSeconds(10),
        base.plusSeconds(10),
        null,
        true,
        true,
        base.plusSeconds(100),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "26000000-0000-4000-8000-000000000003",
        "created-third@example.com",
        base.plusSeconds(20),
        base.plusSeconds(20),
        base.plusSeconds(200),
        true,
        true,
        base.plusSeconds(200),
        Role.user(SystemRoleIds.USER));

    var byCreatedAt =
        userRepository.findAllPage(
            null, DeletedFilter.ALL, null, null, null, SortBy.CREATED_AT, SortDirection.ASC, 0, 10);
    var byUpdatedAt =
        userRepository.findAllPage(
            null, DeletedFilter.ALL, null, null, null, SortBy.UPDATED_AT, SortDirection.ASC, 0, 10);
    var byLastLoginAt =
        userRepository.findAllPage(
            null,
            DeletedFilter.ALL,
            null,
            null,
            null,
            SortBy.LAST_LOGIN_AT,
            SortDirection.ASC,
            0,
            10);
    var byLastLoginAtDesc =
        userRepository.findAllPage(
            null,
            DeletedFilter.ALL,
            null,
            null,
            null,
            SortBy.LAST_LOGIN_AT,
            SortDirection.DESC,
            0,
            10);
    var byDeletedAtAsc =
        userRepository.findAllPage(
            null, DeletedFilter.ALL, null, null, null, SortBy.DELETED_AT, SortDirection.ASC, 0, 10);
    var byDeletedAtDesc =
        userRepository.findAllPage(
            null,
            DeletedFilter.ALL,
            null,
            null,
            null,
            SortBy.DELETED_AT,
            SortDirection.DESC,
            0,
            10);

    assertThat(byCreatedAt.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "created-first@example.com", "created-second@example.com", "created-third@example.com");
    assertThat(byUpdatedAt.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "created-second@example.com", "created-third@example.com", "created-first@example.com");
    assertThat(byLastLoginAt.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "created-third@example.com", "created-first@example.com", "created-second@example.com");
    assertThat(byLastLoginAtDesc.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "created-first@example.com", "created-third@example.com", "created-second@example.com");
    assertThat(byDeletedAtAsc.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "created-second@example.com", "created-third@example.com", "created-first@example.com");
    assertThat(byDeletedAtDesc.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "created-third@example.com", "created-second@example.com", "created-first@example.com");
  }

  @Test
  void findAllPage_shouldCombineSortWithSearchAndFilters() {
    Instant base = Instant.parse("2026-03-24T13:00:00Z");
    saveUser(
        "27000000-0000-4000-8000-000000000001",
        "target-bravo@example.com",
        base,
        true,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "27000000-0000-4000-8000-000000000002",
        "target-alpha@example.com",
        base.plusSeconds(1),
        true,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "27000000-0000-4000-8000-000000000003",
        "target-inactive@example.com",
        base.plusSeconds(2),
        false,
        false,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "27000000-0000-4000-8000-000000000004",
        "other-alpha@example.com",
        base.plusSeconds(3),
        true,
        false,
        null,
        Role.user(SystemRoleIds.USER));

    var page =
        userRepository.findAllPage(
            "target",
            DeletedFilter.EXCLUDE,
            true,
            false,
            "USER",
            SortBy.EMAIL,
            SortDirection.ASC,
            0,
            10);

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .extracting(user -> user.email().value())
        .containsExactly("target-alpha@example.com", "target-bravo@example.com");
  }

  @Test
  void findAllPage_shouldKeepStablePaginationWithIdTieBreaker() {
    Instant base = Instant.parse("2026-03-24T13:30:00Z");
    saveUser(
        "28000000-0000-4000-8000-000000000002",
        "stable-two@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "28000000-0000-4000-8000-000000000001",
        "stable-one@example.com",
        base,
        Role.user(SystemRoleIds.USER));

    var firstPage =
        userRepository.findAllPage(
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            1);
    var secondPage =
        userRepository.findAllPage(
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            1,
            1);

    assertThat(firstPage.content())
        .extracting(user -> user.id().toString())
        .containsExactly("28000000-0000-4000-8000-000000000001");
    assertThat(secondPage.content())
        .extracting(user -> user.id().toString())
        .containsExactly("28000000-0000-4000-8000-000000000002");
  }

  @Test
  void findAllPage_shouldSortByRolePriorityWithoutDuplicatingUsers() {
    Instant base = Instant.parse("2026-03-24T14:00:00Z");
    saveUser(
        "29000000-0000-4000-8000-000000000001",
        "user-role@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "29000000-0000-4000-8000-000000000002",
        "admin-and-user@example.com",
        base.plusSeconds(1),
        Role.admin(SystemRoleIds.ADMIN),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "29000000-0000-4000-8000-000000000003",
        "superadmin-role@example.com",
        base.plusSeconds(2),
        Role.of(SystemRoleIds.SUPERADMIN, "SUPERADMIN"));
    saveUser("29000000-0000-4000-8000-000000000004", "no-role@example.com", base.plusSeconds(3));

    var asc =
        userRepository.findAllPage(
            null, DeletedFilter.EXCLUDE, null, null, null, SortBy.ROLE, SortDirection.ASC, 0, 10);
    var desc =
        userRepository.findAllPage(
            null, DeletedFilter.EXCLUDE, null, null, null, SortBy.ROLE, SortDirection.DESC, 0, 10);

    assertThat(asc.totalElements()).isEqualTo(4);
    assertThat(asc.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "superadmin-role@example.com",
            "admin-and-user@example.com",
            "user-role@example.com",
            "no-role@example.com");
    assertThat(desc.totalElements()).isEqualTo(4);
    assertThat(desc.content())
        .extracting(user -> user.email().value())
        .containsExactly(
            "no-role@example.com",
            "user-role@example.com",
            "admin-and-user@example.com",
            "superadmin-role@example.com");
  }

  @Test
  void findPageByVisibleRoles_shouldApplySearchWithinAdminVisibilityOnly() {
    Instant base = Instant.parse("2026-03-24T10:00:00Z");
    saveUser(
        "30000000-0000-4000-8000-000000000001",
        "visible-match@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "30000000-0000-4000-8000-000000000002",
        "admin-match@example.com",
        base.plusSeconds(1),
        Role.admin(SystemRoleIds.ADMIN));
    saveUser(
        "30000000-0000-4000-8000-000000000003",
        "superadmin-match@example.com",
        base.plusSeconds(2),
        Role.of(SystemRoleIds.SUPERADMIN, "SUPERADMIN"));
    saveUser(
        "30000000-0000-4000-8000-000000000004",
        "visible-other@example.com",
        base.plusSeconds(3),
        Role.user(SystemRoleIds.USER));

    var page = userRepository.findPageByVisibleRoles(Set.of("USER", "ADMIN"), "match", 0, 10);

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.content())
        .extracting(user -> user.email().value())
        .containsExactly("admin-match@example.com", "visible-match@example.com");
  }

  @Test
  void findPageByVisibleRoles_shouldApplyFiltersWithoutExposingSuperAdmin() {
    Instant base = Instant.parse("2026-03-24T10:30:00Z");
    saveUser(
        "31000000-0000-4000-8000-000000000001",
        "visible-filter-match@example.com",
        base,
        true,
        true,
        null,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "31000000-0000-4000-8000-000000000002",
        "admin-filter-match@example.com",
        base.plusSeconds(1),
        true,
        true,
        null,
        Role.admin(SystemRoleIds.ADMIN));
    saveUser(
        "31000000-0000-4000-8000-000000000003",
        "superadmin-filter-match@example.com",
        base.plusSeconds(2),
        true,
        true,
        null,
        Role.of(SystemRoleIds.SUPERADMIN, "SUPERADMIN"));
    saveUser(
        "31000000-0000-4000-8000-000000000004",
        "inactive-filter-match@example.com",
        base.plusSeconds(3),
        false,
        true,
        null,
        Role.user(SystemRoleIds.USER));

    var page =
        userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"), "filter-match", DeletedFilter.ALL, true, true, 0, 10);

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .extracting(user -> user.email().value())
        .containsExactly("admin-filter-match@example.com", "visible-filter-match@example.com");
  }

  @Test
  void findPageByVisibleRoles_shouldReturnEmptyWhenAdminFiltersBySuperAdminRole() {
    Instant base = Instant.parse("2026-03-24T10:45:00Z");
    saveUser(
        "32000000-0000-4000-8000-000000000001",
        "visible-admin@example.com",
        base,
        Role.admin(SystemRoleIds.ADMIN));
    saveUser(
        "32000000-0000-4000-8000-000000000002",
        "hidden-superadmin@example.com",
        base.plusSeconds(1),
        Role.of(SystemRoleIds.SUPERADMIN, "SUPERADMIN"));

    var page =
        userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"), null, DeletedFilter.EXCLUDE, null, null, "SUPERADMIN", 0, 10);

    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
    assertThat(page.content()).isEmpty();
  }

  @Test
  void findAllPage_shouldTreatLikeWildcardsAndEscapeCharacterAsLiteralSearch() {
    Instant base = Instant.parse("2026-03-24T11:00:00Z");
    saveUser(
        "40000000-0000-4000-8000-000000000001",
        "literal%mark@example.com",
        base,
        Role.user(SystemRoleIds.USER));
    saveUser(
        "40000000-0000-4000-8000-000000000002",
        "literalxmark@example.com",
        base.plusSeconds(1),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "40000000-0000-4000-8000-000000000003",
        "literal_under@example.com",
        base.plusSeconds(2),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "40000000-0000-4000-8000-000000000004",
        "literalxunder@example.com",
        base.plusSeconds(3),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "40000000-0000-4000-8000-000000000005",
        "slash\\value@example.com",
        base.plusSeconds(4),
        Role.user(SystemRoleIds.USER));
    saveUser(
        "40000000-0000-4000-8000-000000000006",
        "slash-value@example.com",
        base.plusSeconds(5),
        Role.user(SystemRoleIds.USER));

    assertThat(userRepository.findAllPage("%", 0, 10).content())
        .extracting(user -> user.email().value())
        .containsExactly("literal%mark@example.com");
    assertThat(userRepository.findAllPage("_", 0, 10).content())
        .extracting(user -> user.email().value())
        .containsExactly("literal_under@example.com");
    assertThat(userRepository.findAllPage("\\", 0, 10).content())
        .extracting(user -> user.email().value())
        .containsExactly("slash\\value@example.com");
  }

  private User saveUser(String id, String email, Instant timestamp, Role... roles) {
    return saveUser(id, email, timestamp, true, true, null, roles);
  }

  private User saveUser(
      String id,
      String email,
      Instant timestamp,
      boolean active,
      boolean verified,
      Instant deletedAt,
      Role... roles) {
    return saveUser(id, email, timestamp, timestamp, null, active, verified, deletedAt, roles);
  }

  private User saveUser(
      String id,
      String email,
      Instant createdAt,
      Instant updatedAt,
      Instant lastLogin,
      boolean active,
      boolean verified,
      Instant deletedAt,
      Role... roles) {
    User user =
        User.restore(
            UserId.of(id),
            Email.of(email),
            new PasswordHash("hash-" + id),
            active,
            verified,
            lastLogin,
            createdAt,
            updatedAt,
            deletedAt,
            Set.of(roles));
    User saved = userRepository.save(user);
    if (deletedAt != null) {
      jdbcTemplate.update(
          "UPDATE auth_users SET deleted_at = ? WHERE id = ?",
          Timestamp.from(deletedAt),
          saved.id().value());
    }
    return saved;
  }
}
