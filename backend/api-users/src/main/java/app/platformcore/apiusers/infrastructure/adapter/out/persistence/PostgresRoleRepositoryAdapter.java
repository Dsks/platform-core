package app.platformcore.apiusers.infrastructure.adapter.out.persistence;

import app.platformcore.apiusers.application.port.out.RoleRepositoryPort;
import app.platformcore.apiusers.domain.model.Role;
import app.platformcore.apiusers.domain.model.RoleId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Outbound adapter for {@link RoleRepositoryPort} backed by the PostgreSQL {@code auth_roles}
 * table.
 *
 * <p>It offers application services a role lookup by business name while keeping SQL and
 * normalization details in infrastructure. The adapter performs read-only SQL access and maps rows
 * to the domain {@link Role} aggregate value.
 */
public class PostgresRoleRepositoryAdapter implements RoleRepositoryPort {

  private static final String SQL_FIND_BY_NAME = "SELECT id, name FROM auth_roles WHERE name = ?";

  private final JdbcTemplate jdbcTemplate;

  /**
   * Creates the repository adapter using Spring's JDBC abstraction.
   *
   * @param jdbcTemplate JDBC client used for PostgreSQL role lookups
   * @throws NullPointerException if {@code jdbcTemplate} is {@code null}
   */
  public PostgresRoleRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
  }

  /**
   * Looks up a role by normalized name.
   *
   * <p>The supplied name is trimmed and upper-cased before the SQL lookup, matching the storage
   * convention used by {@code auth_roles}.
   *
   * @param name role business name supplied by the application layer
   * @return matching role, or {@link Optional#empty()} when no row exists
   * @throws NullPointerException if {@code name} is {@code null}
   */
  @Override
  public Optional<Role> findByName(String name) {
    String normalized =
        Objects.requireNonNull(name, "name cannot be null").trim().toUpperCase(Locale.ROOT);
    return jdbcTemplate
        .query(
            SQL_FIND_BY_NAME,
            (rs, rowNum) ->
                Role.of(new RoleId(rs.getObject("id", java.util.UUID.class)), rs.getString("name")),
            normalized)
        .stream()
        .findFirst();
  }
}
