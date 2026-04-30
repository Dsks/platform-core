package app.qomo.apiusers.infrastructure.adapter.in.web;

import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.JwtTokenProviderPort;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.LoginRequest;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.RegisterRequest;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

  public static final String AUTH_COOKIE_NAME = "QOMO_AUTH";
  private static final String REGISTRATION_ACCEPTED_MESSAGE =
      "If the email is valid, you'll receive next steps.";

  private final LoginUseCase loginUseCase;
  private final RegisterUserUseCase registerUserUseCase;
  private final JwtTokenProviderPort jwtTokenProvider;
  private final ClockPort clock;
  private final long expirationMs;
  private final boolean cookieSecure;
  private final String sameSite;
  private final String verificationCookieName;
  private final boolean verificationCookieSecure;
  private final String verificationCookieSameSite;

  public AuthController(
      LoginUseCase loginUseCase,
      RegisterUserUseCase registerUserUseCase,
      JwtTokenProviderPort jwtTokenProvider,
      ClockPort clock,
      @Value("${qomo.security.jwt.expiration-ms:86400000}") long expirationMs,
      @Value("${qomo.security.cookie.secure:false}") boolean cookieSecure,
      @Value("${qomo.security.cookie.same-site:Lax}") String sameSite,
      @Value("${qomo.security.verification.cookie.name:QOMO_VERIF}") String verificationCookieName,
      @Value("${qomo.security.verification.cookie.secure:false}") boolean verificationCookieSecure,
      @Value("${qomo.security.verification.cookie.same-site:Lax}") String verificationCookieSameSite) {
    this.loginUseCase = loginUseCase;
    this.registerUserUseCase = registerUserUseCase;
    this.jwtTokenProvider = jwtTokenProvider;
    this.clock = clock;
    this.expirationMs = expirationMs;
    this.cookieSecure = cookieSecure;
    this.sameSite = sameSite;
    this.verificationCookieName = verificationCookieName;
    this.verificationCookieSecure = verificationCookieSecure;
    this.verificationCookieSameSite = verificationCookieSameSite;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
    var result = loginUseCase.login(new LoginUseCase.Command(request.email(), request.password()));

    if (result.emailNotVerified()) {
      var pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Login not completed");
      pd.setTitle("EMAIL_NOT_VERIFIED");
      pd.setType(URI.create("https://qomo.app/problems/EMAIL_NOT_VERIFIED"));
      if (result.verificationSessionId() == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
      }

      ResponseCookie verificationCookie =
          buildHttpOnlyCookie(
              verificationCookieName,
              result.verificationSessionId().toString(),
              verificationCookieSecure,
              verificationCookieSameSite,
              Duration.ofSeconds(result.verificationTtlSeconds()));

      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .header(HttpHeaders.SET_COOKIE, verificationCookie.toString())
          .body(pd);
    }

    var now = clock.now();
    String token = jwtTokenProvider.generate(result.user(), now);

    ResponseCookie cookie =
        buildHttpOnlyCookie(
            AUTH_COOKIE_NAME, token, cookieSecure, sameSite, Duration.ofMillis(expirationMs));

    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
  }

  @PostMapping("/register")
  public ResponseEntity<RegistrationAcceptedResponse> register(
      @Valid @RequestBody RegisterRequest request) {

    var result =
        registerUserUseCase.register(
            new RegisterUserUseCase.Command(request.email(), request.password()));

    var body = new RegistrationAcceptedResponse(result.requestId(), REGISTRATION_ACCEPTED_MESSAGE);
    if (result.verificationSessionId() == null) {
      return ResponseEntity.accepted().body(body);
    }

    ResponseCookie cookie =
        buildHttpOnlyCookie(
            verificationCookieName,
            result.verificationSessionId().toString(),
            verificationCookieSecure,
            verificationCookieSameSite,
            Duration.ofSeconds(result.verificationTtlSeconds()));

    return ResponseEntity.accepted().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
  }

  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
    return ResponseEntity.ok(
        Map.of(
            "headerName", csrfToken.getHeaderName(),
            "parameterName", csrfToken.getParameterName(),
            "token", csrfToken.getToken()));
  }

  private ResponseCookie buildHttpOnlyCookie(
      String name, String value, boolean secure, String sameSitePolicy, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secure)
        .path("/")
        .sameSite(sameSitePolicy)
        .maxAge(maxAge)
        .build();
  }
}