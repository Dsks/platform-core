package app.qomo.apiusers.domain.model;

import java.util.Objects;

public record PasswordHash(String value) {

  public PasswordHash {
    Objects.requireNonNull(value);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Password hash cannot be blank");
    }
  }

  @Override
  public String toString() {
    return "[PROTECTED]";
  }
}
