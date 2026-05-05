package app.qomo.apiusers.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object that uniquely identifies a verification token. */
public record VerificationTokenId(UUID value) {

  public VerificationTokenId {
    Objects.requireNonNull(value, "VerificationTokenId cannot be null");
  }

  /** Generates a new random verification token identifier. */
  public static VerificationTokenId newId() {
    return new VerificationTokenId(UUID.randomUUID());
  }

  /** Parses an existing token identifier from its UUID string form. */
  public static VerificationTokenId of(String raw) {
    Objects.requireNonNull(raw, "VerificationTokenId raw cannot be null");
    return new VerificationTokenId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
