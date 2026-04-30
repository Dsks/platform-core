package app.qomo.apiusers.infrastructure.adapter.out.persistence;

import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.RoleId;
import app.qomo.apiusers.domain.port.out.RoleRepositoryPort;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresRoleRepositoryAdapter implements RoleRepositoryPort {

  private static final String SQL_FIND_BY_NAME = "SELECT id, name FROM auth_roles WHERE name = ?";

  private final JdbcTemplate jdbcTemplate;

  public PostgresRoleRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
  }

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
