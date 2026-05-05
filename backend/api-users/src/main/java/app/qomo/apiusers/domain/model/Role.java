package app.qomo.apiusers.domain.model;

import java.util.Objects;

/**
 * Value object representing a user role recognized by the domain.
 *
 * <p>Role names are canonicalized to uppercase so role comparisons are case-insensitive at creation
 * time.
 */
public record Role(RoleId id, String name) {

  /** Creates a role with a non-empty canonical name. */
  public Role {
    Objects.requireNonNull(id, "Role id cannot be null");
    Objects.requireNonNull(name, "Role name cannot be null");
    name = name.trim().toUpperCase();
    if (name.isBlank()) {
      throw new IllegalArgumentException("Role name cannot be blank");
    }
  }

  /** Creates the standard USER role. */
  public static Role user(RoleId id) {
    return new Role(id, "USER");
  }

  /** Creates the standard ADMIN role. */
  public static Role admin(RoleId id) {
    return new Role(id, "ADMIN");
  }

  /** Creates a role from a custom role name. */
  public static Role of(RoleId id, String name) {
    return new Role(id, name);
  }

  @Override
  public String toString() {
    return name;
  }
}
