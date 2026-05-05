package app.qomo.apiusers.infrastructure.config;

import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.JwtTokenProviderPort;
import app.qomo.apiusers.infrastructure.adapter.in.web.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates Spring Security authentication from the signed JWT stored in the auth cookie.
 *
 * <p>The filter bridges the HTTP adapter that writes {@link AuthController#AUTH_COOKIE_NAME} with
 * the application security port that validates tokens and exposes claims. It does not create,
 * refresh, or clear cookies; those behaviors stay with the authentication web adapter and token
 * provider wiring. Invalid or missing cookies simply leave the request unauthenticated so the
 * configured authorization chain can decide the outcome.
 */
public class JwtCookieAuthFilter extends OncePerRequestFilter {

  private final JwtTokenProviderPort jwtTokenProvider;
  private final ClockPort clock;

  /**
   * Receives the token provider and clock used for per-request validation.
   *
   * @param jwtTokenProvider token port used to validate signatures and read subject and role claims
   * @param clock shared time port used to evaluate token validity at request time
   */
  public JwtCookieAuthFilter(JwtTokenProviderPort jwtTokenProvider, ClockPort clock) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.clock = clock;
  }

  /**
   * Authenticates the request when a valid auth cookie is present.
   *
   * <p>Roles from the token are mapped to Spring Security authorities with the {@code ROLE_}
   * prefix, matching the role checks declared in {@link SecurityConfig}. The filter intentionally
   * leaves the security context untouched when validation fails.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String jwt = extractJwtFromCookie(request);

    if (jwt != null && jwtTokenProvider.validate(jwt, clock.now())) {
      String subject = jwtTokenProvider.subject(jwt);
      // Roles are trusted only after signature and expiry validation.
      var authorities =
          jwtTokenProvider.roles(jwt).stream()
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
              .collect(Collectors.toSet());

      var auth = new UsernamePasswordAuthenticationToken(subject, null, authorities);
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
  }

  /** Reads the JWT value from the auth cookie name owned by the authentication controller. */
  private String extractJwtFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    return Arrays.stream(cookies)
        .filter(cookie -> AuthController.AUTH_COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
