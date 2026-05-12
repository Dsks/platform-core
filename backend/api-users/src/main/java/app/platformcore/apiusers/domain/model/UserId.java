package app.platformcore.apiusers.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object that uniquely identifies a user aggregate. */
public record UserId(UUID value) {

  public UserId {
    Objects.requireNonNull(value, "UserId value cannot be null");
  }

  /** Generates a new random user identifier. */
  public static UserId newId() {
    return new UserId(UUID.randomUUID());
  }

  /** Parses an existing user identifier from its UUID string form. */
  public static UserId of(String raw) {
    Objects.requireNonNull(raw, "UserId raw cannot be null");
    return new UserId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
