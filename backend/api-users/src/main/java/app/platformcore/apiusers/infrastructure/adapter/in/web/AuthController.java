package app.platformcore.apiusers.infrastructure.adapter.in.web;

import app.platformcore.apiusers.application.port.in.GetCurrentUserUseCase;
import app.platformcore.apiusers.application.port.in.LoginUseCase;
import app.platformcore.apiusers.application.port.in.RegisterUserUseCase;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.JwtTokenProviderPort;
import app.platformcore.apiusers.infrastructure.adapter.in.web.dto.CurrentUserResponse;
import app.platformcore.apiusers.infrastructure.adapter.in.web.dto.LoginRequest;
import app.platformcore.apiusers.infrastructure.adapter.in.web.dto.RegisterRequest;
import app.platformcore.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse;
import app.platformcore.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.web.csrf.CsrfTokenRepository;
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
 * browser cookies. Successful login issues the JWT as an HttpOnly cookie, logout expires that
 * cookie with the same browser attributes, while registration or an unverified login can issue a
 * separate verification-session cookie. Registration keeps new accounts and existing unverified
 * accounts indistinguishable, while existing verified accounts return an explicit
 * already-registered outcome so clients can route legitimate users to login.
 */
@RestController
@RequestMapping("/v1/auth")
@Tag(
    name = "Auth",
    description = "Registration, login, logout, current user, and CSRF bootstrap endpoints.")
public class AuthController {

  public static final String AUTH_COOKIE_NAME = "PLATFORMCORE_AUTH";

  private final LoginUseCase loginUseCase;
  private final RegisterUserUseCase registerUserUseCase;
  private final GetCurrentUserUseCase getCurrentUserUseCase;
  private final JwtTokenProviderPort jwtTokenProvider;
  private final CsrfTokenRepository csrfTokenRepository;
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
      GetCurrentUserUseCase getCurrentUserUseCase,
      JwtTokenProviderPort jwtTokenProvider,
      CsrfTokenRepository csrfTokenRepository,
      ClockPort clock,
      @Value("${platformcore.security.jwt.expiration-ms:86400000}") long expirationMs,
      @Value("${platformcore.security.cookie.secure:false}") boolean cookieSecure,
      @Value("${platformcore.security.cookie.same-site:Lax}") String sameSite,
      @Value("${platformcore.security.verification.cookie.name:PLATFORMCORE_VERIF}")
          String verificationCookieName,
      @Value("${platformcore.security.verification.cookie.secure:false}")
          boolean verificationCookieSecure,
      @Value("${platformcore.security.verification.cookie.same-site:Lax}")
          String verificationCookieSameSite) {
    this.loginUseCase = loginUseCase;
    this.registerUserUseCase = registerUserUseCase;
    this.getCurrentUserUseCase = getCurrentUserUseCase;
    this.jwtTokenProvider = jwtTokenProvider;
    this.csrfTokenRepository = csrfTokenRepository;
    this.clock = clock;
    this.expirationMs = expirationMs;
    this.cookieSecure = cookieSecure;
    this.sameSite = sameSite;
    this.verificationCookieName = verificationCookieName;
    this.verificationCookieSecure = verificationCookieSecure;
    this.verificationCookieSameSite = verificationCookieSameSite;
  }

  /**
   * Returns the authenticated user's current identity.
   *
   * <p>The endpoint relies on the existing JWT cookie authentication filter to populate the
   * principal, then delegates current account resolution to {@link GetCurrentUserUseCase}. It is a
   * read-only endpoint, so Spring Security does not require a CSRF token. The response
   * intentionally exposes only safe user identity and account-state fields needed by browser
   * clients after login.
   *
   * @return current authenticated user representation
   */
  @Operation(
      summary = "Get current authenticated user",
      description =
          "Returns the current authenticated user's safe identity projection using the PLATFORMCORE_AUTH"
              + " cookie. The response excludes JWTs, password hashes, verification codes, and"
              + " internal token material.",
      security = @SecurityRequirement(name = "platformcoreAuthCookie"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Current authenticated user.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CurrentUserResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Authentication requirements are not satisfied.",
        content = @Content()),
    @ApiResponse(
        responseCode = "403",
        description = "The authenticated account cannot access this endpoint in its current state.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "The authenticated user no longer exists.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @GetMapping("/me")
  public ResponseEntity<CurrentUserResponse> me() {
    var currentUser = getCurrentUserUseCase.getCurrentUser();
    return ResponseEntity.ok(
        new CurrentUserResponse(
            currentUser.id(),
            currentUser.email(),
            currentUser.active(),
            currentUser.emailVerified(),
            currentUser.roles()));
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
  @Operation(
      summary = "Create an authentication session",
      description =
          "Authenticates browser credentials. A successful login returns no body and sets the"
              + " PLATFORMCORE_AUTH HTTP cookie. Invalid credentials and accounts that still require email"
              + " verification or cannot complete login are returned as problem details.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Login completed. The PLATFORMCORE_AUTH cookie is set on the response.",
        headers =
            @Header(
                name = "Set-Cookie",
                description =
                    "Sets the PLATFORMCORE_AUTH HTTP-only authentication cookie. The cookie value is"
                        + " sensitive and is not documented.",
                schema = @Schema(type = "string")),
        content = @Content()),
    @ApiResponse(
        responseCode = "400",
        description = "The request body is malformed or fails validation.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Credentials are invalid.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "Login cannot be completed for the account state. When email verification is required,"
                + " a verification-session cookie may be set.",
        headers =
            @Header(
                name = "Set-Cookie",
                description =
                    "May set a verification-session cookie so the browser can continue the"
                        + " email-verification flow. No authentication cookie is issued.",
                schema = @Schema(type = "string")),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Credentials submitted for browser login.",
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = LoginRequest.class)))
          @Valid
          @RequestBody
          LoginRequest request) {
    var result = loginUseCase.login(new LoginUseCase.Command(request.email(), request.password()));

    if (result.emailNotVerified()) {
      // Hold back the auth cookie until verification succeeds; this cookie only resumes OTP flow.
      var pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Login not completed");
      pd.setTitle("EMAIL_NOT_VERIFIED");
      pd.setType(URI.create("https://platformcore.app/problems/EMAIL_NOT_VERIFIED"));
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
   * Ends the current browser authentication session.
   *
   * <p>The endpoint returns {@code 204 No Content} and writes {@value #AUTH_COOKIE_NAME} with the
   * same HttpOnly, Secure, SameSite, and path attributes used by login, but with {@code Max-Age=0}
   * so the browser expires the cookie. The endpoint is authenticated and remains CSRF-protected by
   * the security chain because it changes browser session state.
   *
   * @return a no-content response with an expired auth cookie
   */
  @Operation(
      summary = "End the authentication session",
      description =
          "Expires the PLATFORMCORE_AUTH HTTP-only authentication cookie. The response has no body and uses"
              + " the same cookie attributes as login with Max-Age=0.",
      security = @SecurityRequirement(name = "platformcoreAuthCookie"))
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Logout completed. The PLATFORMCORE_AUTH cookie is expired on the response.",
        headers =
            @Header(
                name = "Set-Cookie",
                description = "Expires the PLATFORMCORE_AUTH HTTP-only authentication cookie.",
                schema = @Schema(type = "string")),
        content = @Content()),
    @ApiResponse(
        responseCode = "401",
        description = "Authentication requirements are not satisfied.",
        content = @Content()),
    @ApiResponse(
        responseCode = "403",
        description = "Authentication or CSRF requirements are not satisfied.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    ResponseCookie cookie =
        buildHttpOnlyCookie(AUTH_COOKIE_NAME, "", cookieSecure, sameSite, Duration.ZERO);

    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
  }

  /**
   * Accepts a public registration request without exposing new-vs-unverified account details.
   *
   * <p>The endpoint delegates account creation and verification-session creation to {@link
   * RegisterUserUseCase}. It returns {@code 202 Accepted} with {@code VERIFICATION_REQUIRED} for
   * new accounts and existing unverified accounts; if the application created a verification
   * session, the response also writes the verification cookie. That cookie is HttpOnly, scoped to
   * {@code /}, uses the configured Secure and SameSite policies, and expires with the
   * application-provided session TTL. Existing verified accounts return {@code 409 Conflict} with
   * {@code ALREADY_REGISTERED}. Validation and malformed-body errors are handled globally.
   *
   * @param request validated email and password from the JSON request body
   * @return a registration outcome response, optionally with a verification-session cookie
   */
  @Operation(
      summary = "Register a user account",
      description =
          "Accepts a public registration request. New accounts and existing unverified accounts"
              + " both return VERIFICATION_REQUIRED so the client cannot distinguish them. Existing"
              + " verified accounts return ALREADY_REGISTERED so the client can route users to"
              + " login.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description =
            "Registration request accepted with VERIFICATION_REQUIRED. This response covers"
                + " both new accounts and existing unverified accounts.",
        headers =
            @Header(
                name = "Set-Cookie",
                description =
                    "May set a verification-session HTTP-only cookie. The cookie value is"
                        + " sensitive and is not documented.",
                schema = @Schema(type = "string")),
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RegistrationAcceptedResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "The request body is malformed or fails validation.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "The submitted email belongs to an already verified account; returns"
                + " ALREADY_REGISTERED.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RegistrationAcceptedResponse.class)))
  })
  @PostMapping("/register")
  public ResponseEntity<RegistrationAcceptedResponse> register(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Registration data submitted by the browser client.",
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = RegisterRequest.class)))
          @Valid
          @RequestBody
          RegisterRequest request) {

    var result =
        registerUserUseCase.register(
            new RegisterUserUseCase.Command(request.email(), request.password()));

    if (result.status() == RegisterUserUseCase.RegistrationStatus.ALREADY_REGISTERED) {
      var body = new RegistrationAcceptedResponse(Status.ALREADY_REGISTERED);
      return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // The body stays generic for new and existing-unverified accounts.
    var body = new RegistrationAcceptedResponse(Status.VERIFICATION_REQUIRED);
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
   * supplied by the configured CSRF token repository.
   *
   * @return token metadata and value for the client
   */
  @Operation(
      summary = "Fetch CSRF token details",
      description =
          "Returns the CSRF token metadata needed for state-changing requests. Clients should send"
              + " the token value in the response-provided header name. Token values are sensitive"
              + " and are not documented as examples.")
  @ApiResponse(
      responseCode = "200",
      description = "CSRF token metadata for browser clients.",
      content =
          @Content(
              mediaType = "application/json",
              schema =
                  @Schema(
                      implementation = Map.class,
                      description =
                          "Object containing headerName, parameterName, and token fields.")))
  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(
      HttpServletRequest request, HttpServletResponse response) {
    CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
    if (csrfToken == null) {
      csrfToken = csrfTokenRepository.generateToken(request);
      csrfTokenRepository.saveToken(csrfToken, request, response);
    }

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
