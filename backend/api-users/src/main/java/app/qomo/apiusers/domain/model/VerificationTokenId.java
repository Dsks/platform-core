package app.qomo.apiusers.domain.model;

import java.util.Objects;
import java.util.UUID;

public record VerificationTokenId(UUID value) {

  public VerificationTokenId {
    Objects.requireNonNull(value, "VerificationTokenId cannot be null");
  }

  public static VerificationTokenId newId() {
    return new VerificationTokenId(UUID.randomUUID());
  }

  public static VerificationTokenId of(String raw) {
    Objects.requireNonNull(raw, "VerificationTokenId raw cannot be null");
    return new VerificationTokenId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
