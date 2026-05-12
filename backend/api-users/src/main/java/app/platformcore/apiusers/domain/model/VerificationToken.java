package app.platformcore.apiusers.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for one-time verification tokens linked to user security flows.
 *
 * <p>Instances capture lifecycle timestamps and attempt metadata used to enforce expiration and
 * abuse controls in upper layers.
 */
public class VerificationToken {

  /** Functional token purpose within the user lifecycle. */
  public enum Type {
    /** Used to confirm ownership of the email and transition users into verified state. */
    EMAIL_VERIFICATION,
    /** Used to authorize password reset operations. */
    PASS_RESET
  }

  private final VerificationTokenId id;
  private final UserId userId;
  private final String tokenHash;
  private final Type type;
  private final UUID sessionId;
  private final Instant expiresAt;
  private final Instant createdAt;
  private final Instant consumedAt;
  private final int attempts;
  private final Instant lastAttemptAt;
  private final Instant lastSentAt;

  /** Rehydrates a verification token from persistence. */
  public VerificationToken(
      VerificationTokenId id,
      UserId userId,
      String tokenHash,
      Type type,
      UUID sessionId,
      Instant expiresAt,
      Instant createdAt,
      Instant consumedAt,
      int attempts,
      Instant lastAttemptAt,
      Instant lastSentAt) {
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.userId = Objects.requireNonNull(userId, "userId cannot be null");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash cannot be null");
    this.type = Objects.requireNonNull(type, "type cannot be null");
    this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    this.consumedAt = consumedAt;
    this.attempts = attempts;
    this.lastAttemptAt = lastAttemptAt;
    this.lastSentAt = lastSentAt;
  }

  /**
   * Creates a new email verification OTP token.
   *
   * <p>The token starts with zero attempts, no consumption timestamp, and an expiration derived
   * from {@code now + ttl}.
   */
  public static VerificationToken emailVerificationOtp(
      UserId userId, String otpHash, UUID sessionId, Instant now, Duration ttl) {
    Objects.requireNonNull(now, "now cannot be null");
    Objects.requireNonNull(ttl, "ttl cannot be null");

    return new VerificationToken(
        VerificationTokenId.newId(),
        userId,
        otpHash,
        Type.EMAIL_VERIFICATION,
        sessionId,
        now.plus(ttl),
        now,
        null,
        0,
        null,
        now);
  }

  /** Returns whether the token can no longer be used at the provided instant. */
  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  public VerificationTokenId id() {
    return id;
  }

  public UserId userId() {
    return userId;
  }

  public String tokenHash() {
    return tokenHash;
  }

  public Type type() {
    return type;
  }

  public UUID sessionId() {
    return sessionId;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant consumedAt() {
    return consumedAt;
  }

  public int attempts() {
    return attempts;
  }

  public Instant lastAttemptAt() {
    return lastAttemptAt;
  }

  public Instant lastSentAt() {
    return lastSentAt;
  }
}
