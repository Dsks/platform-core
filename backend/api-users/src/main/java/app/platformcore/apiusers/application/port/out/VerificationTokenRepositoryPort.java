package app.platformcore.apiusers.application.port.out;

import app.platformcore.apiusers.domain.model.UserId;
import app.platformcore.apiusers.domain.model.VerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstracts durable storage for verification tokens used by application security flows.
 *
 * <p>The application expects implementations to preserve token lifecycle metadata consistently
 * across creation, lookup, consumption, invalidation, and failed attempts. Stored token values are
 * hashes or otherwise sensitive security material and must not be exposed through logs, metrics, or
 * exception messages.
 *
 * <p>This contract is about token persistence only. Decisions such as when to issue a token, how
 * many attempts are allowed, and how verification outcomes map to HTTP responses belong to the
 * application or inbound layers.
 */
public interface VerificationTokenRepositoryPort {

  /**
   * Persists a verification token with its current lifecycle state.
   *
   * @param token token aggregate to persist, including its hash, type, session, expiration, and
   *     attempt metadata
   */
  void save(VerificationToken token);

  /**
   * Persists a freshly issued token as a new active candidate for its user and type.
   *
   * @param token newly issued token whose secret value must already be represented safely, for
   *     example as a hash
   */
  void saveNewToken(VerificationToken token);

  /**
   * Removes all tokens for a user and token purpose.
   *
   * @param userId owner of the tokens to remove
   * @param type functional token purpose to remove
   */
  void deleteByUserAndType(UserId userId, VerificationToken.Type type);

  /**
   * Loads the newest token for a user and token purpose, regardless of expiration or consumption
   * state.
   *
   * @param userId owner of the token
   * @param type functional token purpose to search for
   * @return the latest matching token when one exists
   */
  Optional<VerificationToken> findLatestByUserAndType(UserId userId, VerificationToken.Type type);

  /**
   * Loads the newest usable token for a user and token purpose at the supplied instant.
   *
   * <p>"Active" means the token is not consumed and has not expired relative to {@code now}.
   *
   * @param userId owner of the token
   * @param type functional token purpose to search for
   * @param now application time used to evaluate expiration
   * @return the latest active token when one exists
   */
  Optional<VerificationToken> findLatestActiveByUserId(
      UserId userId, VerificationToken.Type type, Instant now);

  /**
   * Marks all currently active tokens for a user and token purpose as no longer usable.
   *
   * @param userId owner of the tokens to invalidate
   * @param type functional token purpose to invalidate
   * @param now timestamp to record as the invalidation or consumption time
   */
  void invalidateActiveTokens(UserId userId, VerificationToken.Type type, Instant now);

  /**
   * Loads the newest usable token for a verification session and token purpose at the supplied
   * instant.
   *
   * <p>"Active" means the token is not consumed and has not expired relative to {@code now}.
   *
   * @param sessionId verification session that identifies the token exchange
   * @param type functional token purpose to search for
   * @param now application time used to evaluate expiration
   * @return the latest active token for the session when one exists
   */
  Optional<VerificationToken> findActiveBySessionAndType(
      UUID sessionId, VerificationToken.Type type, Instant now);

  /**
   * Records that a token has been successfully consumed.
   *
   * @param tokenId identifier of the token to update
   * @param consumedAt application time to store as the consumption timestamp
   */
  void markConsumed(UUID tokenId, Instant consumedAt);

  /**
   * Records a failed or blocked verification attempt for a token.
   *
   * @param tokenId identifier of the token to update
   * @param attemptedAt application time to store as the latest attempt timestamp
   */
  void incrementAttempts(UUID tokenId, Instant attemptedAt);
}
