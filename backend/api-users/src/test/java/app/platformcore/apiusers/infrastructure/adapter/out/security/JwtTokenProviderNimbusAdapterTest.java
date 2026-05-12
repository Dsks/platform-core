package app.platformcore.apiusers.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.PasswordHash;
import app.platformcore.apiusers.domain.model.Role;
import app.platformcore.apiusers.domain.model.RoleId;
import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtTokenProviderNimbusAdapterTest {

  private static final String SECRET = "01234567890123456789012345678901";

  @Test
  void generatesAndValidatesTokenAndExtractsClaims() {
    JwtTokenProviderNimbusAdapter provider = new JwtTokenProviderNimbusAdapter(SECRET, 60_000);
    Instant now = Instant.parse("2026-03-26T12:00:00Z");
    User user =
        User.createNew(
            UserId.of("2f5c4278-c6de-47a8-a0ad-58f0075ca278"),
            Email.of("security@platformcore.app"),
            new PasswordHash("bcrypt"),
            now);
    user.addRole(Role.of(RoleId.of("1018864f-6121-4c4c-88cd-7125f5be7a8a"), "admin"), now);

    String token = provider.generate(user, now);

    assertThat(provider.validate(token, now.plusSeconds(1))).isTrue();
    assertThat(provider.subject(token)).isEqualTo("2f5c4278-c6de-47a8-a0ad-58f0075ca278");
    assertThat(provider.roles(token)).containsExactly("ADMIN");
  }

  @Test
  void rejectsExpiredToken() {
    JwtTokenProviderNimbusAdapter provider = new JwtTokenProviderNimbusAdapter(SECRET, 1_000);
    Instant now = Instant.parse("2026-03-26T12:00:00Z");
    User user =
        User.createNew(
            UserId.of("abbff6fa-bcb8-4864-ad89-aec5fa8ea0da"),
            Email.of("expired@platformcore.app"),
            new PasswordHash("bcrypt"),
            now);

    String token = provider.generate(user, now);

    assertThat(provider.validate(token, now.plusMillis(1_001))).isFalse();
  }

  @Test
  void rejectsInvalidTokenAndThrowsOnClaimExtraction() {
    JwtTokenProviderNimbusAdapter provider = new JwtTokenProviderNimbusAdapter(SECRET, 60_000);

    assertThat(provider.validate("not-a-jwt", Instant.parse("2026-03-26T12:00:00Z"))).isFalse();
    assertThatThrownBy(() -> provider.subject("not-a-jwt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid JWT");
    assertThatThrownBy(() -> provider.roles("not-a-jwt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid JWT");
  }
}
