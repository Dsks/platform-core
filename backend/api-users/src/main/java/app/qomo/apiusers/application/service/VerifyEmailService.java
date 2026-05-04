package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.port.in.VerifyEmailUseCase;
import app.qomo.apiusers.domain.model.VerificationToken;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.port.out.VerificationTokenRepositoryPort;
import app.qomo.apiusers.infrastructure.util.TokenHasher;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

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

  @Override
  @Transactional
  public boolean verify(Command command) {
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
      verificationTokenRepository.incrementAttempts(verificationToken.id().value(), now);
      return false;
    }

    String codeHash = tokenHasher.sha256Hex(command.code());
    if (!verificationToken.tokenHash().equals(codeHash)) {
      verificationTokenRepository.incrementAttempts(verificationToken.id().value(), now);
      return false;
    }

    userRepository.setVerified(verificationToken.userId(), now);
    verificationTokenRepository.markConsumed(verificationToken.id().value(), now);
    return true;
  }
}
