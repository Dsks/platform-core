package app.qomo.apiusers.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityCurrentUserAdapterTest {

  private final SecurityCurrentUserAdapter adapter = new SecurityCurrentUserAdapter();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void userId_shouldReturnAuthenticationName_whenAuthenticationIsPresent() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user-123", "n/a", "ROLE_USER"));

    assertThat(adapter.userId()).contains("user-123");
  }

  @Test
  void userId_shouldReturnEmpty_whenAuthenticationIsMissing() {
    SecurityContextHolder.clearContext();

    assertThat(adapter.userId()).isEmpty();
  }

  @Test
  void roles_shouldNormalizeAndDeduplicateRoleAuthorities() {
    var auth =
        new TestingAuthenticationToken(
            "actor",
            "n/a",
            List.of(
                () -> "ROLE_admin",
                () -> "ROLE_USER",
                () -> "ROLE_admin",
                () -> "SCOPE_read",
                () -> null));
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(adapter.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
  }

  @Test
  void roles_shouldReturnEmpty_whenAuthenticationHasNullAuthorities() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getAuthorities()).thenReturn(null);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    assertThat(adapter.roles()).isEmpty();
  }
}
