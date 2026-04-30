package app.qomo.apiusers.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) {

  public RoleId {
    Objects.requireNonNull(value, "RoleId cannot be null");
  }

  public static RoleId newId() {
    return new RoleId(UUID.randomUUID());
  }

  public static RoleId of(String raw) {
    Objects.requireNonNull(raw, "RoleId raw cannot be null");
    return new RoleId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
