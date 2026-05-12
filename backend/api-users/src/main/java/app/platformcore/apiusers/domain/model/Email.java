package app.platformcore.apiusers.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a canonical user email.
 *
 * <p>The value is normalized to lowercase and trimmed to guarantee consistent lookups and
 * uniqueness checks across the domain.
 */
public record Email(String value) {

  private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  /**
   * Creates a normalized email value.
   *
   * <p>Validation enforces non-blank content and a simple structural email format suitable for
   * domain-level guards.
   */
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

  /** Creates an {@link Email} from a raw external input value. */
  public static Email of(String raw) {
    return new Email(raw);
  }

  @Override
  public String toString() {
    return value;
  }
}
