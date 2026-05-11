package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import app.qomo.apiusers.application.port.in.GetUserUseCase.DeletedFilter;
import app.qomo.apiusers.application.port.in.GetUserUseCase.SortBy;
import app.qomo.apiusers.application.port.in.GetUserUseCase.SortDirection;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.RoleId;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter for {@link UserRepositoryPort} backed by PostgreSQL authentication tables.
 *
 * <p>The adapter persists the {@link User} aggregate in {@code auth_users} and its role membership
 * in {@code auth_users_roles}, joining {@code auth_roles} when restoring the aggregate. It
 * encapsulates SQL upserts, role join-table replacement, nullable login timestamps, and required
 * creation/update timestamps for the application layer.
 */
public class PostgresUserRepositoryAdapter implements UserRepositoryPort {

  private static final String SQL_EXISTS_BY_EMAIL =
      "SELECT 1 FROM auth_users WHERE email = ? LIMIT 1";
  private static final String SQL_FIND_USER_BY_ID =
      """
          SELECT id, email, password_hash, is_active, is_verified, last_login, created_at, updated_at, deleted_at
          FROM auth_users
          WHERE id = ?
          """;
  private static final String SQL_FIND_USER_BY_EMAIL =
      """
          SELECT id, email, password_hash, is_active, is_verified, last_login, created_at, updated_at, deleted_at
          FROM auth_users
          WHERE email = ?
          """;
  private static final String SQL_FIND_ROLES_BY_USER_ID =
      """
          SELECT r.id, r.name
          FROM auth_users_roles ur
          JOIN auth_roles r ON r.id = ur.role_id
          WHERE ur.user_id = ?
          """;
  private static final String SQL_UPSERT_USER =
      """
          INSERT INTO auth_users (id, email, password_hash, is_active, is_verified, last_login, created_at, updated_at, deleted_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT (id) DO UPDATE SET
              email = EXCLUDED.email,
              password_hash = EXCLUDED.password_hash,
              is_active = EXCLUDED.is_active,
              is_verified = EXCLUDED.is_verified,
              last_login = EXCLUDED.last_login,
              created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at
          """;
  private static final String SQL_DELETE_USER_ROLES =
      "DELETE FROM auth_users_roles WHERE user_id = ?";
  private static final String SQL_INSERT_USER_ROLE =
      "INSERT INTO auth_users_roles (user_id, role_id) VALUES (?, ?)";
  private static final String SQL_SET_VERIFIED =
      "UPDATE auth_users SET is_verified = TRUE, updated_at = ? WHERE id = ?";
  private static final String SQL_SOFT_DELETE_AND_ANONYMIZE =
      """
          UPDATE auth_users
          SET email = ?,
              password_hash = ?,
              is_active = FALSE,
              is_verified = FALSE,
              last_login = NULL,
              deleted_at = ?,
              updated_at = ?
          WHERE id = ?
          """;

  private final JdbcTemplate jdbcTemplate;

  /**
   * Creates the repository adapter using Spring's JDBC abstraction.
   *
   * @param jdbcTemplate JDBC client used for PostgreSQL user and role mapping operations
   * @throws NullPointerException if {@code jdbcTemplate} is {@code null}
   */
  public PostgresUserRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
  }

  /**
   * Checks whether an email already exists in {@code auth_users}.
   *
   * <p>The query selects a single sentinel row with {@code LIMIT 1}; it does not restore the user
   * aggregate or inspect role membership.
   *
   * @param email email value to compare with the stored canonical email column
   * @return {@code true} when at least one user row exists for the email
   */
  @Override
  public boolean existsByEmail(String email) {
    Integer exists = jdbcTemplate.query(SQL_EXISTS_BY_EMAIL, rs -> rs.next() ? 1 : null, email);
    return exists != null;
  }

  /**
   * Restores a user aggregate by identifier.
   *
   * @param id domain user identifier mapped to {@code auth_users.id}
   * @return restored user with roles, or {@link Optional#empty()} when no user row exists
   */
  @Override
  public Optional<User> findById(UserId id) {
    return findUser(SQL_FIND_USER_BY_ID, id.value());
  }

  /**
   * Restores a user aggregate by email.
   *
   * @param email email value mapped to {@code auth_users.email}
   * @return restored user with roles, or {@link Optional#empty()} when no user row exists
   */
  @Override
  public Optional<User> findByEmail(String email) {
    return findUser(SQL_FIND_USER_BY_EMAIL, email);
  }

  /** Lists all users using stable ordering for administrative pagination. */
  @Override
  public UserRepositoryPort.Page<User> findAllPage(
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      String role,
      SortBy sortBy,
      SortDirection sortDirection,
      int page,
      int size) {
    UserFilters filters = userFilters(emailSearch, deleted, active, verified, role);
    String countSql = "SELECT COUNT(*) FROM auth_users u " + whereClause(filters.predicates());
    String orderBy = orderByClause(sortBy, sortDirection);
    String pageSql =
        """
            SELECT u.id, u.email, u.password_hash, u.is_active, u.is_verified,
                   u.last_login, u.created_at, u.updated_at, u.deleted_at
            FROM auth_users u
            %s
            ORDER BY %s
            LIMIT ? OFFSET ?
            """
            .formatted(whereClause(filters.predicates()), orderBy);

    Long total = jdbcTemplate.queryForObject(countSql, Long.class, filters.args().toArray());

    List<Object> pageArgs = new ArrayList<>(filters.args());
    pageArgs.add(size);
    pageArgs.add(offset(page, size));
    List<UserRow> rows = jdbcTemplate.query(pageSql, this::mapUserRow, pageArgs.toArray());
    return toPage(rows, page, size, total == null ? 0 : total);
  }

  /** Lists users whose complete role set is inside the caller-visible role set. */
  @Override
  public UserRepositoryPort.Page<User> findPageByVisibleRoles(
      Set<String> visibleRoleNames,
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      String role,
      SortBy sortBy,
      SortDirection sortDirection,
      int page,
      int size) {
    Objects.requireNonNull(visibleRoleNames, "visibleRoleNames cannot be null");
    List<String> roles =
        visibleRoleNames.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(userRole -> !userRole.isBlank())
            .sorted()
            .toList();
    if (roles.isEmpty()) {
      return new UserRepositoryPort.Page<>(List.of(), page, size, 0, 0);
    }

    String placeholders = String.join(", ", Collections.nCopies(roles.size(), "?"));
    List<String> predicates = new ArrayList<>();
    predicates.add(
        """
            EXISTS (
                SELECT 1
                FROM auth_users_roles ur
                JOIN auth_roles r ON r.id = ur.role_id
                WHERE ur.user_id = u.id AND r.name IN (%s)
            )
            """
            .formatted(placeholders));
    predicates.add(
        """
            NOT EXISTS (
                SELECT 1
                FROM auth_users_roles ur
                JOIN auth_roles r ON r.id = ur.role_id
                WHERE ur.user_id = u.id AND r.name NOT IN (%s)
            )
            """
            .formatted(placeholders));

    UserFilters userFilters = userFilters(emailSearch, deleted, active, verified, role);
    predicates.addAll(userFilters.predicates());

    String countSql = "SELECT COUNT(*) FROM auth_users u " + whereClause(predicates);
    String orderBy = orderByClause(sortBy, sortDirection);
    String pageSql =
        """
            SELECT u.id, u.email, u.password_hash, u.is_active, u.is_verified,
                   u.last_login, u.created_at, u.updated_at, u.deleted_at
            FROM auth_users u
            %s
            ORDER BY %s
            LIMIT ? OFFSET ?
            """
            .formatted(whereClause(predicates), orderBy);

    List<Object> countArgs = visibleRoleArgs(roles);
    countArgs.addAll(userFilters.args());
    Long total = jdbcTemplate.queryForObject(countSql, Long.class, countArgs.toArray());

    List<Object> pageArgs = new ArrayList<>(countArgs);
    pageArgs.add(size);
    pageArgs.add(offset(page, size));
    List<UserRow> rows = jdbcTemplate.query(pageSql, this::mapUserRow, pageArgs.toArray());

    return toPage(rows, page, size, total == null ? 0 : total);
  }

  /**
   * Upserts the user row and replaces its role join rows in one transaction.
   *
   * <p>The user row is keyed by {@code id}; when it already exists, all persisted scalar fields are
   * overwritten with the aggregate state supplied by the application layer. Role membership is
   * rewritten by deleting current join rows and inserting the distinct role identifiers from the
   * aggregate, keeping the database representation aligned with the in-memory aggregate snapshot.
   *
   * @param user aggregate snapshot to persist, including roles and timestamps
   * @return the same aggregate instance supplied by the caller
   */
  @Override
  @Transactional
  public User save(User user) {
    jdbcTemplate.update(
        SQL_UPSERT_USER,
        user.id().value(),
        user.email().value(),
        user.passwordHash().value(),
        user.isActive(),
        user.isVerified(),
        toTimestamp(user.lastLogin()),
        toTimestamp(user.createdAt()),
        toTimestamp(user.updatedAt()),
        toTimestamp(user.deletedAt()));

    // Replace join rows with the aggregate snapshot so removed roles disappear atomically.
    jdbcTemplate.update(SQL_DELETE_USER_ROLES, user.id().value());

    var roleIds = user.roles().stream().map(role -> role.id().value()).collect(Collectors.toSet());
    for (UUID roleId : roleIds) {
      jdbcTemplate.update(SQL_INSERT_USER_ROLE, user.id().value(), roleId);
    }

    return user;
  }

  /**
   * Marks a user row as verified.
   *
   * @param id domain user identifier mapped to {@code auth_users.id}
   * @param now timestamp written to {@code updated_at}
   */
  @Override
  public void setVerified(UserId id, Instant now) {
    jdbcTemplate.update(SQL_SET_VERIFIED, Timestamp.from(now), id.value());
  }

  /**
   * Deactivates and anonymizes sensitive scalar fields without deleting the user row or roles.
   *
   * @param id domain user identifier mapped to {@code auth_users.id}
   * @param anonymizedEmail replacement email value
   * @param unusablePasswordHash replacement password hash value
   * @param now timestamp written to {@code deleted_at} and {@code updated_at}
   */
  @Override
  public void softDeleteAndAnonymize(
      UserId id, Email anonymizedEmail, PasswordHash unusablePasswordHash, Instant now) {
    Timestamp deletionTimestamp = Timestamp.from(now);
    jdbcTemplate.update(
        SQL_SOFT_DELETE_AND_ANONYMIZE,
        anonymizedEmail.value(),
        unusablePasswordHash.value(),
        deletionTimestamp,
        deletionTimestamp,
        id.value());
  }

  /**
   * Restores the user row and then loads role membership through the join table.
   *
   * <p>{@code last_login} is mapped as nullable, while {@code created_at} and {@code updated_at}
   * are treated as required database values before calling the domain restore factory.
   */
  private Optional<User> findUser(String sql, Object idOrEmail) {
    Optional<UserRow> userRow =
        jdbcTemplate.query(sql, this::mapUserRow, idOrEmail).stream().findFirst();

    return userRow.map(this::restoreUser);
  }

  private UserRepositoryPort.Page<User> toPage(
      List<UserRow> rows, int page, int size, long totalElements) {
    return new UserRepositoryPort.Page<>(
        rows.stream().map(this::restoreUser).toList(),
        page,
        size,
        totalElements,
        totalPages(totalElements, size));
  }

  private User restoreUser(UserRow row) {
    List<Role> roles =
        jdbcTemplate.query(
            SQL_FIND_ROLES_BY_USER_ID,
            (rs, rowNum) ->
                Role.of(new RoleId(rs.getObject("id", UUID.class)), rs.getString("name")),
            row.id().value());

    return User.restore(
        row.id(),
        Email.of(row.email()),
        new PasswordHash(row.passwordHash()),
        row.isActive(),
        row.isVerified(),
        row.lastLogin(),
        row.createdAt(),
        row.updatedAt(),
        row.deletedAt(),
        new HashSet<>(roles));
  }

  private UserRow mapUserRow(ResultSet rs, int rowNum) throws SQLException {
    return new UserRow(
        new UserId(rs.getObject("id", UUID.class)),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getBoolean("is_active"),
        rs.getBoolean("is_verified"),
        toInstant(rs.getTimestamp("last_login")),
        Objects.requireNonNull(toInstant(rs.getTimestamp("created_at"))),
        Objects.requireNonNull(toInstant(rs.getTimestamp("updated_at"))),
        toInstant(rs.getTimestamp("deleted_at")));
  }

  private List<Object> visibleRoleArgs(List<String> roleNames) {
    List<Object> args = new ArrayList<>(roleNames.size() * 2);
    args.addAll(roleNames);
    args.addAll(roleNames);
    return args;
  }

  private UserFilters userFilters(
      String emailSearch, DeletedFilter deleted, Boolean active, Boolean verified, String role) {
    List<String> predicates = new ArrayList<>();
    List<Object> args = new ArrayList<>();

    String emailPattern = emailSearchPattern(emailSearch);
    if (emailPattern != null) {
      predicates.add("u.email ILIKE ? ESCAPE '\\'");
      args.add(emailPattern);
    }

    switch (deleted == null ? DeletedFilter.EXCLUDE : deleted) {
      case EXCLUDE -> predicates.add("u.deleted_at IS NULL");
      case ONLY -> predicates.add("u.deleted_at IS NOT NULL");
      case ALL -> {
        // No deleted_at predicate.
      }
    }

    if (active != null) {
      predicates.add("u.is_active = ?");
      args.add(active);
    }
    if (verified != null) {
      predicates.add("u.is_verified = ?");
      args.add(verified);
    }
    String roleName = normalizeRoleFilter(role);
    if (roleName != null) {
      predicates.add(
          """
              EXISTS (
                  SELECT 1
                  FROM auth_users_roles ur
                  JOIN auth_roles r ON r.id = ur.role_id
                  WHERE ur.user_id = u.id AND r.name = ?
              )
              """);
      args.add(roleName);
    }

    return new UserFilters(predicates, args);
  }

  private String whereClause(List<String> predicates) {
    if (predicates.isEmpty()) {
      return "";
    }
    return "WHERE " + String.join("\nAND ", predicates) + "\n";
  }

  private String emailSearchPattern(String emailSearch) {
    if (emailSearch == null) {
      return null;
    }
    String normalized = emailSearch.trim();
    if (normalized.isBlank()) {
      return null;
    }
    return "%" + escapeLikeLiteral(normalized) + "%";
  }

  private String normalizeRoleFilter(String role) {
    if (role == null) {
      return null;
    }
    String normalized = role.trim().toUpperCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
  }

  private String orderByClause(SortBy sortBy, SortDirection sortDirection) {
    SortBy field = sortBy == null ? SortBy.CREATED_AT : sortBy;
    SortDirection direction = sortDirection == null ? SortDirection.DESC : sortDirection;
    String expression =
        switch (field) {
          case EMAIL -> "u.email";
          case CREATED_AT -> "u.created_at";
          case UPDATED_AT -> "u.updated_at";
          case LAST_LOGIN_AT -> "u.last_login";
          case DELETED_AT -> "u.deleted_at";
          case ROLE -> rolePriorityExpression();
        };
    String nullsLast =
        field == SortBy.LAST_LOGIN_AT || field == SortBy.DELETED_AT ? " NULLS LAST" : "";
    return expression + " " + direction.name() + nullsLast + ", u.id ASC";
  }

  private String rolePriorityExpression() {
    return """
        CASE
          WHEN EXISTS (
              SELECT 1
              FROM auth_users_roles ur
              JOIN auth_roles r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.name = 'SUPERADMIN'
          ) THEN 1
          WHEN EXISTS (
              SELECT 1
              FROM auth_users_roles ur
              JOIN auth_roles r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.name = 'ADMIN'
          ) THEN 2
          WHEN EXISTS (
              SELECT 1
              FROM auth_users_roles ur
              JOIN auth_roles r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.name = 'USER'
          ) THEN 3
          ELSE 4
        END
        """
        .strip();
  }

  private String escapeLikeLiteral(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private long offset(int page, int size) {
    return (long) page * size;
  }

  private int totalPages(long totalElements, int size) {
    if (totalElements == 0) {
      return 0;
    }
    return Math.toIntExact((totalElements + size - 1) / size);
  }

  /**
   * Converts nullable domain instants to JDBC timestamps for optional columns such as login time.
   */
  private Timestamp toTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  /** Converts nullable JDBC timestamps to domain instants without inventing default values. */
  private Instant toInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  /**
   * Internal row DTO for scalar user columns before role rows are loaded and the domain aggregate
   * is restored.
   */
  private record UserRow(
      UserId id,
      String email,
      String passwordHash,
      boolean isActive,
      boolean isVerified,
      Instant lastLogin,
      Instant createdAt,
      Instant updatedAt,
      Instant deletedAt) {}

  private record UserFilters(List<String> predicates, List<Object> args) {}
}
