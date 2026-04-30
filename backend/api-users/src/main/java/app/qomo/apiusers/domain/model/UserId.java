package app.qomo.apiusers.domain.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

  public UserId {
    Objects.requireNonNull(value, "UserId value cannot be null");
  }

  public static UserId newId() {
    return new UserId(UUID.randomUUID());
  }

  public static UserId of(String raw) {
    Objects.requireNonNull(raw, "UserId raw cannot be null");
    return new UserId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
