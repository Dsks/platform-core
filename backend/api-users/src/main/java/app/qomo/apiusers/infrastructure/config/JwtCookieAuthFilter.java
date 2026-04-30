package app.qomo.apiusers.infrastructure.config;

import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.JwtTokenProviderPort;
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

public class JwtCookieAuthFilter extends OncePerRequestFilter {

  private final JwtTokenProviderPort jwtTokenProvider;
  private final ClockPort clock;

  public JwtCookieAuthFilter(JwtTokenProviderPort jwtTokenProvider, ClockPort clock) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String jwt = extractJwtFromCookie(request);

    if (jwt != null && jwtTokenProvider.validate(jwt, clock.now())) {
      String subject = jwtTokenProvider.subject(jwt);
      var authorities =
          jwtTokenProvider.roles(jwt).stream()
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
              .collect(Collectors.toSet());

      var auth = new UsernamePasswordAuthenticationToken(subject, null, authorities);
      SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
  }

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