package app.qomo.apiusers.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.JwtTokenProviderPort;
import app.qomo.apiusers.infrastructure.adapter.in.web.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtCookieAuthFilterTest {

  @Mock private JwtTokenProviderPort jwtTokenProviderPort;
  @Mock private ClockPort clockPort;
  @Mock private FilterChain filterChain;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doesNotAuthenticateWhenCookieIsMissing() throws ServletException, IOException {
    JwtCookieAuthFilter filter = new JwtCookieAuthFilter(jwtTokenProviderPort, clockPort);

    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(jwtTokenProviderPort, never()).validate(any(), any());
  }

  @Test
  void doesNotAuthenticateWhenCookieIsPresentButInvalid() throws ServletException, IOException {
    JwtCookieAuthFilter filter = new JwtCookieAuthFilter(jwtTokenProviderPort, clockPort);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(AuthController.AUTH_COOKIE_NAME, "broken-token"));
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T12:00:00Z"));
    when(jwtTokenProviderPort.validate(eq("broken-token"), any(Instant.class))).thenReturn(false);

    filter.doFilter(request, new MockHttpServletResponse(), filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(jwtTokenProviderPort, never()).subject(any());
    verify(jwtTokenProviderPort, never()).roles(any());
  }

  @Test
  void authenticatesWhenJwtCookieIsValidAndMapsRolesToSpringAuthorities()
      throws ServletException, IOException {
    JwtCookieAuthFilter filter = new JwtCookieAuthFilter(jwtTokenProviderPort, clockPort);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("OTHER_COOKIE", "ignored"),
        new Cookie(AuthController.AUTH_COOKIE_NAME, "good-token"));
    when(clockPort.now()).thenReturn(Instant.parse("2026-03-26T12:00:00Z"));
    when(jwtTokenProviderPort.validate(eq("good-token"), any(Instant.class))).thenReturn(true);
    when(jwtTokenProviderPort.subject("good-token")).thenReturn("user-123");
    when(jwtTokenProviderPort.roles("good-token")).thenReturn(Set.of("ADMIN", "USER"));

    filter.doFilter(request, new MockHttpServletResponse(), filterChain);

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getPrincipal()).isEqualTo("user-123");
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
  }
}
