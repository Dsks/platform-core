package app.qomo.apiusers.infrastructure.config;

import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.JwtTokenProviderPort;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final boolean csrfCookieSecure;
  private final String csrfCookieSameSite;

  public SecurityConfig(
      @Value("${qomo.security.csrf.cookie.secure:true}") boolean csrfCookieSecure,
      @Value("${qomo.security.csrf.cookie.same-site:Lax}") String csrfCookieSameSite) {
    this.csrfCookieSecure = csrfCookieSecure;
    this.csrfCookieSameSite = csrfCookieSameSite;
  }

  @Bean
  public JwtCookieAuthFilter jwtCookieAuthFilter(
      JwtTokenProviderPort jwtTokenProviderPort, ClockPort clockPort) {
    return new JwtCookieAuthFilter(jwtTokenProviderPort, clockPort);
  }

  @Bean
  public CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(
        cookie ->
            cookie.path("/")
                .secure(csrfCookieSecure)
                .sameSite(csrfCookieSameSite));
    return repository;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtCookieAuthFilter jwtFilter, CsrfTokenRepository csrfTokenRepository)
      throws Exception {
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
