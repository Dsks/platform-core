package app.qomo.apiusers.infrastructure.adapter.in.web;

import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.JwtTokenProviderPort;
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

/**
 * HTTP boundary for browser authentication and public registration under {@code /v1/auth}.
 *
 * <p>The controller delegates credential and registration decisions to {@link LoginUseCase} and
 * {@link RegisterUserUseCase}; it only translates application results into HTTP responses and
 * browser cookies. Successful login issues the JWT as an HttpOnly cookie, while registration or an
 * unverified login can issue a separate verification-session cookie. Registration responses remain
 * generic so clients cannot use this boundary to confirm whether an email already belongs to an
 * account.
 */
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
      @Value("${qomo.security.verification.cookie.same-site:Lax}")
          String verificationCookieSameSite) {
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

  /**
   * Authenticates a browser session from an email/password request body.
   *
   * <p>On success, returns {@code 204 No Content} and writes {@value #AUTH_COOKIE_NAME} as an
   * HttpOnly cookie containing the generated JWT. The cookie is scoped to {@code /}, uses the
   * configured Secure and SameSite policies, and receives the configured JWT max-age. When
   * credentials belong to an unverified user, the endpoint returns {@code 403 Forbidden} with an
   * {@code EMAIL_NOT_VERIFIED} problem response and, when the application provides a verification
   * session, writes the verification cookie so the client can continue the email-verification flow.
   * Invalid credentials, inactive users, malformed JSON, and validation failures are surfaced by
   * the global exception handler.
   *
   * @param request validated login credentials from the JSON request body
   * @return a no-content response with the auth cookie, or a problem response for an unverified
   *     account
   */
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

  /**
   * Accepts a public registration request without exposing account-existence details.
   *
   * <p>The endpoint delegates account creation and verification-session creation to {@link
   * RegisterUserUseCase}. It returns {@code 202 Accepted} with a generic {@link
   * RegistrationAcceptedResponse}; if the application created a verification session, the response
   * also writes the verification cookie. That cookie is HttpOnly, scoped to {@code /}, uses the
   * configured Secure and SameSite policies, and expires with the application-provided session TTL.
   * Duplicate-email handling is delegated to {@link ApiExceptionHandler}, which preserves the same
   * generic response shape for this public route. Validation and malformed-body errors are handled
   * globally.
   *
   * @param request validated email and password from the JSON request body
   * @return a generic accepted response, optionally with a verification-session cookie
   */
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

  /**
   * Returns the CSRF token material expected by Spring Security for subsequent state-changing
   * calls.
   *
   * <p>The response is {@code 200 OK} with the header name, parameter name, and token value
   * supplied by the framework. This method does not manually read or write cookies.
   *
   * @param csrfToken framework-provided token for the current request
   * @return token metadata and value for the client
   */
  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
    return ResponseEntity.ok(
        Map.of(
            "headerName", csrfToken.getHeaderName(),
            "parameterName", csrfToken.getParameterName(),
            "token", csrfToken.getToken()));
  }

  /**
   * Builds cookies that carry browser security state without exposing values to client-side
   * scripts.
   *
   * <p>All cookies created here are HttpOnly, scoped to {@code /}, use the configured Secure and
   * SameSite policy, and receive the caller-provided max-age so login and verification sessions can
   * expire independently.
   */
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
