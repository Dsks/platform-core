package app.qomo.apiusers.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

  private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public Email {
    Objects.requireNonNull(value, "Email cannot be null");
    var normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Email cannot be blank");
    }
    if (!SIMPLE_EMAIL.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid email format");
    }
    value = normalized;
  }

  public static Email of(String raw) {
    return new Email(raw);
  }

  @Override
  public String toString() {
    return value;
  }
}
