package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.RoleId;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class PostgresUserRepositoryAdapter implements UserRepositoryPort {

  private static final String SQL_EXISTS_BY_EMAIL =
      "SELECT 1 FROM auth_users WHERE email = ? LIMIT 1";
  private static final String SQL_FIND_USER_BY_ID =
      """
          SELECT id, email, password_hash, is_active, is_verified, last_login, created_at, updated_at
          FROM auth_users
          WHERE id = ?
          """;
  private static final String SQL_FIND_USER_BY_EMAIL =
      """
          SELECT id, email, password_hash, is_active, is_verified, last_login, created_at, updated_at
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
          INSERT INTO auth_users (id, email, password_hash, is_active, is_verified, last_login, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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

  private final JdbcTemplate jdbcTemplate;

  public PostgresUserRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
  }

  @Override
  public boolean existsByEmail(String email) {
    Integer exists = jdbcTemplate.query(SQL_EXISTS_BY_EMAIL, rs -> rs.next() ? 1 : null, email);
    return exists != null;
  }

  @Override
  public Optional<User> findById(UserId id) {
    return findUser(SQL_FIND_USER_BY_ID, id.value());
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return findUser(SQL_FIND_USER_BY_EMAIL, email);
  }

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
        toTimestamp(user.updatedAt()));

    jdbcTemplate.update(SQL_DELETE_USER_ROLES, user.id().value());

    var roleIds = user.roles().stream().map(role -> role.id().value()).collect(Collectors.toSet());
    for (UUID roleId : roleIds) {
      jdbcTemplate.update(SQL_INSERT_USER_ROLE, user.id().value(), roleId);
    }

    return user;
  }

  @Override
  public void setVerified(UserId id, Instant now) {
    jdbcTemplate.update(SQL_SET_VERIFIED, Timestamp.from(now), id.value());
  }

  private Optional<User> findUser(String sql, Object idOrEmail) {
    Optional<UserRow> userRow =
        jdbcTemplate
            .query(
                sql,
                (rs, rowNum) ->
                    new UserRow(
                        new UserId(rs.getObject("id", UUID.class)),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getBoolean("is_active"),
                        rs.getBoolean("is_verified"),
                        toInstant(rs.getTimestamp("last_login")),
                        Objects.requireNonNull(toInstant(rs.getTimestamp("created_at"))),
                        Objects.requireNonNull(toInstant(rs.getTimestamp("updated_at")))),
                idOrEmail)
            .stream()
            .findFirst();

    if (userRow.isEmpty()) {
      return Optional.empty();
    }

    List<Role> roles =
        jdbcTemplate.query(
            SQL_FIND_ROLES_BY_USER_ID,
            (rs, rowNum) ->
                Role.of(new RoleId(rs.getObject("id", UUID.class)), rs.getString("name")),
            userRow.get().id().value());

    UserRow row = userRow.get();
    User user =
        User.restore(
            row.id(),
            Email.of(row.email()),
            new PasswordHash(row.passwordHash()),
            row.isActive(),
            row.isVerified(),
            row.lastLogin(),
            row.createdAt(),
            row.updatedAt(),
            new HashSet<>(roles));

    return Optional.of(user);
  }

  private Timestamp toTimestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private Instant toInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private record UserRow(
      UserId id,
      String email,
      String passwordHash,
      boolean isActive,
      boolean isVerified,
      Instant lastLogin,
      Instant createdAt,
      Instant updatedAt) {}
}
