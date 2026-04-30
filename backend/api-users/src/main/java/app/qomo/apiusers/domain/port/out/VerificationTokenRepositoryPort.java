package app.qomo.apiusers.domain.port.out;

import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepositoryPort {

  void save(VerificationToken token);

  void saveNewToken(VerificationToken token);

  void deleteByUserAndType(UserId userId, VerificationToken.Type type);

  Optional<VerificationToken> findLatestByUserAndType(UserId userId, VerificationToken.Type type);

  Optional<VerificationToken> findLatestActiveByUserId(
      UserId userId, VerificationToken.Type type, Instant now);

  void invalidateActiveTokens(UserId userId, VerificationToken.Type type, Instant now);

  Optional<VerificationToken> findActiveBySessionAndType(
      UUID sessionId, VerificationToken.Type type, Instant now);

  void markConsumed(UUID tokenId, Instant consumedAt);

  void incrementAttempts(UUID tokenId, Instant attemptedAt);
}
