package app.qomo.apiusers.infrastructure.adapter.out.security;

import app.qomo.apiusers.application.port.out.JwtTokenProviderPort;
import app.qomo.apiusers.domain.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Outbound security adapter for {@link JwtTokenProviderPort} implemented with Nimbus JOSE/JWT.
 *
 * <p>It signs access tokens with an HS256 MAC using the configured shared secret, writes the user
 * identifier as the subject, and stores role names in the {@code roles} claim. The adapter handles
 * sensitive token material and signing secrets; secrets, raw tokens, and full JWT payloads must not
 * be logged in clear text.
 */
public class JwtTokenProviderNimbusAdapter implements JwtTokenProviderPort {

  private final byte[] sharedSecret;
  private final long expirationMs;

  /**
   * Creates a JWT provider using a shared secret and a fixed token lifetime.
   *
   * @param secret signing and verification secret encoded as UTF-8 bytes for the MAC operation
   * @param expirationMs token lifetime in milliseconds, added to the issue time during generation
   * @throws NullPointerException if {@code secret} is {@code null}
   */
  public JwtTokenProviderNimbusAdapter(String secret, long expirationMs) {
    this.sharedSecret =
        Objects.requireNonNull(secret, "secret cannot be null").getBytes(StandardCharsets.UTF_8);
    this.expirationMs = expirationMs;
  }

  /**
   * Generates a signed JWT for the supplied user.
   *
   * <p>The token contains the user's identifier as {@code sub}, the supplied {@code now} as issue
   * time, an expiration derived from {@code expirationMs}, and the user's domain role names in the
   * {@code roles} claim.
   *
   * @param user authenticated user aggregate to encode into token claims
   * @param now issue time used for {@code iat} and expiration calculation
   * @return compact serialized signed JWT
   * @throws IllegalStateException if Nimbus cannot sign the token
   */
  @Override
  public String generate(User user, Instant now) {
    try {
      Instant expiresAt = now.plusMillis(expirationMs);

      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(user.id().toString())
              .issueTime(Date.from(now))
              .expirationTime(Date.from(expiresAt))
              .claim("roles", user.roles().stream().map(role -> role.name()).toList())
              .build();

      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      signedJWT.sign(new MACSigner(sharedSecret));
      return signedJWT.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed generating JWT", e);
    }
  }

  /**
   * Validates token syntax, HS256 signature, and expiration.
   *
   * @param token compact serialized JWT supplied by a client; sensitive and not safe to log
   * @param now clock value used to reject expired tokens
   * @return {@code true} only when the token parses, the MAC signature is valid, and the expiration
   *     claim is present and later than {@code now}
   */
  @Override
  public boolean validate(String token, Instant now) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);
      boolean signatureValid = signedJWT.verify(new MACVerifier(sharedSecret));
      if (!signatureValid) {
        return false;
      }

      Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
      return expiration != null && expiration.toInstant().isAfter(now);
    } catch (ParseException | JOSEException e) {
      return false;
    }
  }

  /**
   * Extracts the subject claim from a parsed JWT.
   *
   * <p>This method parses claims but does not verify signature or expiration; callers should use it
   * only after the token has been validated through {@link #validate(String, Instant)}.
   *
   * @param token compact serialized JWT supplied by a client; sensitive and not safe to log
   * @return subject claim, expected to contain the user identifier
   * @throws IllegalArgumentException if the token cannot be parsed
   */
  @Override
  public String subject(String token) {
    try {
      return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
    } catch (ParseException e) {
      throw new IllegalArgumentException("Invalid JWT", e);
    }
  }

  /**
   * Extracts role names from the {@code roles} claim.
   *
   * <p>This method parses claims but does not verify signature or expiration; callers should use it
   * only after the token has been validated through {@link #validate(String, Instant)}. Missing
   * role claims are mapped to an empty set.
   *
   * @param token compact serialized JWT supplied by a client; sensitive and not safe to log
   * @return role names contained in the token, or an empty set when the claim is absent
   * @throws IllegalArgumentException if the token cannot be parsed
   */
  @Override
  public Set<String> roles(String token) {
    try {
      List<String> roles = SignedJWT.parse(token).getJWTClaimsSet().getStringListClaim("roles");
      if (roles == null) {
        return Set.of();
      }
      return new HashSet<>(roles);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Invalid JWT", e);
    }
  }
}
