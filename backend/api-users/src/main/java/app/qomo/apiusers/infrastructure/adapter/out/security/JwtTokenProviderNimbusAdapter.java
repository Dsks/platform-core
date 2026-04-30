package app.qomo.apiusers.infrastructure.adapter.out.security;

import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.port.out.JwtTokenProviderPort;
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

public class JwtTokenProviderNimbusAdapter implements JwtTokenProviderPort {

  private final byte[] sharedSecret;
  private final long expirationMs;

  public JwtTokenProviderNimbusAdapter(String secret, long expirationMs) {
    this.sharedSecret = Objects.requireNonNull(secret, "secret cannot be null")
        .getBytes(StandardCharsets.UTF_8);
    this.expirationMs = expirationMs;
  }

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

  @Override
  public String subject(String token) {
    try {
      return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
    } catch (ParseException e) {
      throw new IllegalArgumentException("Invalid JWT", e);
    }
  }

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