package app.qomo.apiusers.infrastructure.config;

import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.JwtTokenProviderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Centralizes HTTP security wiring for the users API.
 *
 * <p>The configuration connects the JWT cookie filter to Spring Security, exposes the CSRF token
 * repository used by browser clients, and defines which user-management routes are public,
 * role-gated, or authenticated. It consumes CSRF cookie attributes from external properties and
 * leaves token creation, password hashing, and application use-case wiring to {@link
 * UsersBeansConfig}.
 *
 * <p>The chain is intentionally stateless: authenticated requests are reconstructed from the JWT
 * cookie on each call instead of relying on server-side sessions. Public authentication and email
 * verification endpoints are excluded from CSRF checks because they need to be callable before a
 * client has an authenticated session context.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final boolean csrfCookieSecure;
  private final String csrfCookieSameSite;

  /**
   * Captures runtime CSRF cookie attributes applied by {@link #csrfTokenRepository()}.
   *
   * @param csrfCookieSecure value of {@code qomo.security.csrf.cookie.secure}; defaults to {@code
   *     true}
   * @param csrfCookieSameSite value of {@code qomo.security.csrf.cookie.same-site}; defaults to
   *     {@code Lax}
   */
  public SecurityConfig(
      @Value("${qomo.security.csrf.cookie.secure:true}") boolean csrfCookieSecure,
      @Value("${qomo.security.csrf.cookie.same-site:Lax}") String csrfCookieSameSite) {
    this.csrfCookieSecure = csrfCookieSecure;
    this.csrfCookieSameSite = csrfCookieSameSite;
  }

  /**
   * Exposes the filter that authenticates requests from the JWT auth cookie.
   *
   * @param jwtTokenProviderPort outbound security port used to validate and read JWT claims
   * @param clockPort shared time port used during token validation
   */
  @Bean
  public JwtCookieAuthFilter jwtCookieAuthFilter(
      JwtTokenProviderPort jwtTokenProviderPort, ClockPort clockPort) {
    return new JwtCookieAuthFilter(jwtTokenProviderPort, clockPort);
  }

  /**
   * Stores CSRF tokens in a browser-readable cookie for clients that submit the token on protected
   * state-changing requests.
   *
   * <p>The cookie path is application-wide, while {@code Secure} and {@code SameSite} are
   * controlled by the constructor properties so deployments can tune browser behavior without
   * changing the filter chain.
   */
  @Bean
  public CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(
        cookie -> cookie.path("/").secure(csrfCookieSecure).sameSite(csrfCookieSameSite));
    return repository;
  }

  /**
   * Builds the HTTP authorization chain for the users API.
   *
   * <p>Login, registration, CSRF token retrieval, email verification, verification resend, health
   * checks, and OpenAPI/Swagger assets are public. Current-user lookup is authenticated, user
   * creation is limited to ADMIN and SUPERADMIN roles, other users routes require authentication,
   * and every remaining route is protected by default. The JWT cookie filter runs before
   * username/password authentication so the security context is populated from the signed cookie
   * before authorization rules are evaluated.
   *
   * @param http Spring Security builder supplied by the framework
   * @param jwtFilter filter that extracts and validates the JWT auth cookie
   * @param csrfTokenRepository CSRF repository with runtime cookie attributes
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtCookieAuthFilter jwtFilter, CsrfTokenRepository csrfTokenRepository)
      throws Exception {
    // CSRF stays active for protected browser writes; only pre-auth flows are exempted.
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .ignoringRequestMatchers(
                        "/v1/auth/login",
                        "/v1/auth/register",
                        "/v1/users/verify-email",
                        "/v1/users/verification/resend"))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/v1/auth/register")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/v1/users/verify-email")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/v1/users/verification/resend")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/health")
                    .permitAll()
                    .requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/auth/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/v1/users")
                    .hasAnyRole("ADMIN", "SUPERADMIN")
                    .requestMatchers("/v1/users/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
