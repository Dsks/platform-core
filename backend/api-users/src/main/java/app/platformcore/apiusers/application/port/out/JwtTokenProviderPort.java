package app.platformcore.apiusers.application.port.out;

import app.platformcore.apiusers.domain.model.User;
import java.time.Instant;
import java.util.Set;

/**
 * Abstracts JWT creation and inspection for authenticated user sessions.
 *
 * <p>The application expects implementations to sign generated tokens, validate token integrity and
 * expiration using the supplied application time, and expose only the claims required by callers.
 * JWTs and signing secrets are credentials and must not be logged or included in errors.
 *
 * <p>This contract does not decide cookie handling, HTTP authentication challenges, or
 * authorization policy beyond exposing subject and role claims.
 */
public interface JwtTokenProviderPort {

  /**
   * Generates a token representing the supplied user at the supplied issue time.
   *
   * @param user authenticated user whose identity and roles should be represented in the token
   * @param now application time used as the issue-time reference
   * @return serialized JWT to return to the caller through an appropriate secure channel
   */
  String generate(User user, Instant now);

  /**
   * Validates token integrity and expiration at the supplied application time.
   *
   * @param token serialized JWT presented by a caller
   * @param now application time used to evaluate expiration
   * @return {@code true} when the token is structurally valid, trusted, and not expired
   */
  boolean validate(String token, Instant now);

  /**
   * Extracts the subject claim from a token.
   *
   * @param token serialized JWT to inspect
   * @return subject claim used by the application as the authenticated principal identifier
   * @throws IllegalArgumentException when the token cannot be decoded by the implementation
   */
  String subject(String token);

  /**
   * Extracts application role claims from a token.
   *
   * @param token serialized JWT to inspect
   * @return role names represented by the token, or an empty set when no roles are present
   * @throws IllegalArgumentException when the token cannot be decoded by the implementation
   */
  Set<String> roles(String token);
}
