package app.qomo.apiusers.domain.model;

import java.util.Objects;

public record Role(RoleId id, String name) {

  public Role {
    Objects.requireNonNull(id, "Role id cannot be null");
    Objects.requireNonNull(name, "Role name cannot be null");
    name = name.trim().toUpperCase();
    if (name.isBlank()) {
      throw new IllegalArgumentException("Role name cannot be blank");
    }
  }

  public static Role user(RoleId id) {
    return new Role(id, "USER");
  }

  public static Role admin(RoleId id) {
    return new Role(id, "ADMIN");
  }

  public static Role of(RoleId id, String name) {
    return new Role(id, name);
  }

  @Override
  public String toString() {
    return name;
  }
}
