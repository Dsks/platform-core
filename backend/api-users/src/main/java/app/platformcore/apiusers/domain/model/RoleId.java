package app.platformcore.apiusers.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object that uniquely identifies a role in the users domain. */
public record RoleId(UUID value) {

  public RoleId {
    Objects.requireNonNull(value, "RoleId cannot be null");
  }

  /** Generates a new random role identifier. */
  public static RoleId newId() {
    return new RoleId(UUID.randomUUID());
  }

  /** Parses an existing role identifier from its UUID string form. */
  public static RoleId of(String raw) {
    Objects.requireNonNull(raw, "RoleId raw cannot be null");
    return new RoleId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
