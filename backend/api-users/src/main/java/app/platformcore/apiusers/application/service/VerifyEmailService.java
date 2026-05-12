package app.platformcore.apiusers.application.service;

import app.platformcore.apiusers.application.port.in.VerifyEmailUseCase;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.UserRepositoryPort;
import app.platformcore.apiusers.application.port.out.VerificationTokenRepositoryPort;
import app.platformcore.apiusers.domain.model.VerificationToken;
import app.platformcore.apiusers.infrastructure.util.TokenHasher;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes the email-verification use case for an existing verification session.
 *
 * <p>The service coordinates the token repository, user repository, clock, and token hasher to
 * validate an active email-verification OTP without exposing which verification condition failed.
 * It protects the invariant that a user is marked verified only when a currently active session
 * presents the expected six-digit code, and it consumes the token in the same transaction as the
 * user-state change.
 *
 * <p>Submitted verification codes are treated as credential-like secrets: only their hash is
 * compared with stored data, and neither codes nor session identifiers should be logged in clear
 * text by callers.
 */
public class VerifyEmailService implements VerifyEmailUseCase {

  private final VerificationTokenRepositoryPort verificationTokenRepository;
  private final UserRepositoryPort userRepository;
  private final ClockPort clock;
  private final TokenHasher tokenHasher;
  private final int maxAttempts;

  public VerifyEmailService(
      VerificationTokenRepositoryPort verificationTokenRepository,
      UserRepositoryPort userRepository,
      ClockPort clock,
      TokenHasher tokenHasher,
      int maxAttempts) {
    this.verificationTokenRepository =
        Objects.requireNonNull(
            verificationTokenRepository, "verificationTokenRepository cannot be null");
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher cannot be null");
    this.maxAttempts = maxAttempts;
  }

  /**
   * Attempts to verify the email address associated with the submitted verification session.
   *
   * <p>A malformed command, missing or expired active session, exceeded attempt limit, or wrong
   * code all produce {@code false} so callers can keep failure responses generic. Wrong codes and
   * attempts made after the configured limit are recorded through the token repository; a matching
   * code marks the user as verified and consumes the token.
   *
   * @param command session identifier and six-digit code received from the adapter
   * @return {@code true} only after the user has been marked verified and the token consumed;
   *     {@code false} for every non-success outcome
   */
  @Override
  @Transactional
  public boolean verify(Command command) {
    // Verification failures stay generic so callers cannot distinguish malformed secrets.
    if (command == null
        || command.sessionId() == null
        || command.code() == null
        || !command.code().matches("\\d{6}")) {
      return false;
    }

    var now = clock.now();
    var token =
        verificationTokenRepository.findActiveBySessionAndType(
            command.sessionId(), VerificationToken.Type.EMAIL_VERIFICATION, now);

    if (token.isEmpty()) {
      return false;
    }

    var verificationToken = token.get();

    if (maxAttempts > 0 && verificationToken.attempts() >= maxAttempts) {
      // Keep counting post-limit attempts for audit while preserving the same caller response.
      verificationTokenRepository.incrementAttempts(verificationToken.id().value(), now);
      return false;
    }

    String codeHash = tokenHasher.sha256Hex(command.code());
    if (!verificationToken.tokenHash().equals(codeHash)) {
      verificationTokenRepository.incrementAttempts(verificationToken.id().value(), now);
      return false;
    }

    // Marking the user verified and consuming the token together prevents OTP replay.
    userRepository.setVerified(verificationToken.userId(), now);
    verificationTokenRepository.markConsumed(verificationToken.id().value(), now);
    return true;
  }
}
