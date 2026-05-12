package app.platformcore.apiusers.infrastructure.adapter.in.web;

import app.platformcore.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.platformcore.apiusers.application.port.in.VerifyEmailUseCase;
import app.platformcore.apiusers.infrastructure.adapter.in.web.dto.ResendVerificationRequest;
import app.platformcore.apiusers.infrastructure.adapter.in.web.dto.VerifyEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for email-verification completion and resend flows under {@code /v1/users}.
 *
 * <p>The browser verification session is carried in a dedicated HttpOnly cookie rather than in the
 * request body. The controller delegates verification decisions to {@link VerifyEmailUseCase} and
 * resend requests to {@link ResendEmailVerificationUseCase}. Responses for missing, malformed, or
 * unusable verification state are intentionally generic to avoid account or token enumeration.
 * Successful verification clears the cookie; resend can replace it with a fresh session.
 */
@RestController
@RequestMapping("/v1/users")
@Tag(
    name = "Email Verification",
    description = "Verification code confirmation and resend flow endpoints.")
public class VerifyEmailController {

  private static final Map<String, String> GENERIC_RESPONSE = Map.of("message", "Code Not Found");
  private static final Map<String, String> GENERIC_RESPONSE_RESEND =
      Map.of("message", "Forwarded Code");

  private final VerifyEmailUseCase verifyEmailUseCase;
  private final ResendEmailVerificationUseCase resendEmailVerificationUseCase;
  private final String verificationCookieName;
  private final boolean verificationCookieSecure;
  private final String verificationCookieSameSite;

  public VerifyEmailController(
      VerifyEmailUseCase verifyEmailUseCase,
      ResendEmailVerificationUseCase resendEmailVerificationUseCase,
      @Value("${platformcore.security.verification.cookie.name:PLATFORMCORE_VERIF}")
          String verificationCookieName,
      @Value("${platformcore.security.verification.cookie.secure:false}")
          boolean verificationCookieSecure,
      @Value("${platformcore.security.verification.cookie.same-site:Lax}")
          String verificationCookieSameSite) {
    this.verifyEmailUseCase = verifyEmailUseCase;
    this.resendEmailVerificationUseCase = resendEmailVerificationUseCase;
    this.verificationCookieName = verificationCookieName;
    this.verificationCookieSecure = verificationCookieSecure;
    this.verificationCookieSameSite = verificationCookieSameSite;
  }

  /**
   * Attempts to complete email verification using the code in the body and the session id cookie.
   *
   * <p>The endpoint expects {@link VerifyEmailRequest}; the verification session id is read from
   * the configured cookie. Missing cookies, non-UUID cookie values, invalid codes, and
   * application-level rejections all return {@code 202 Accepted} with the same generic body. A
   * successful verification returns {@code 204 No Content} and clears the verification cookie by
   * setting an empty HttpOnly cookie on path {@code /} with the configured Secure and SameSite
   * policies and {@code Max-Age=0}. Validation and malformed-body errors are delegated to the
   * global exception handler.
   *
   * @param servletRequest request used only to read the verification-session cookie
   * @param request validated verification code from the JSON request body
   * @return generic accepted response for non-successful attempts, or no content after success
   */
  @Operation(
      summary = "Confirm an email verification code",
      description =
          "Attempts to complete email verification using a one-time code and the PLATFORMCORE_VERIF"
              + " verification cookie. Successful verification returns no body and clears the"
              + " verification cookie. Non-successful verification attempts return the same generic"
              + " accepted response to avoid revealing account or verification state.",
      parameters =
          @Parameter(
              name = "PLATFORMCORE_VERIF",
              in = ParameterIn.COOKIE,
              required = false,
              description =
                  "Verification-session cookie used to continue the email-verification flow. The"
                      + " cookie value is sensitive and is not documented.",
              schema = @Schema(type = "string")))
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Email verification completed. The verification cookie is cleared.",
        headers =
            @Header(
                name = "Set-Cookie",
                description =
                    "Clears the PLATFORMCORE_VERIF verification cookie. The cookie value is not"
                        + " documented.",
                schema = @Schema(type = "string")),
        content = @Content()),
    @ApiResponse(
        responseCode = "202",
        description =
            "Generic accepted response when verification cannot be completed without revealing the"
                + " cause.",
        content =
            @Content(
                mediaType = "application/json",
                schema =
                    @Schema(
                        implementation = Map.class,
                        description = "Generic response containing a message field."))),
    @ApiResponse(
        responseCode = "400",
        description = "The request body is malformed or fails validation.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping("/verify-email")
  public ResponseEntity<?> verifyEmail(
      @Parameter(hidden = true) HttpServletRequest servletRequest,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "One-time email verification code submitted by the browser client.",
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = VerifyEmailRequest.class)))
          @Valid
          @RequestBody
          VerifyEmailRequest request) {
    String sessionId = readCookie(servletRequest, verificationCookieName);
    if (sessionId == null) {
      return ResponseEntity.accepted().body(GENERIC_RESPONSE);
    }

    UUID parsedSessionId;
    try {
      parsedSessionId = UUID.fromString(sessionId);
    } catch (IllegalArgumentException ex) {
      // Malformed session cookies get the same response as absent or expired verification state.
      return ResponseEntity.accepted().body(GENERIC_RESPONSE);
    }

    var ok =
        verifyEmailUseCase.verify(new VerifyEmailUseCase.Command(parsedSessionId, request.code()));
    if (!ok) {
      return ResponseEntity.accepted().body(GENERIC_RESPONSE);
    }

    // Reuse the original attributes so the browser expires the exact verification cookie.
    ResponseCookie clearCookie =
        ResponseCookie.from(verificationCookieName, "")
            .httpOnly(true)
            .secure(verificationCookieSecure)
            .path("/")
            .sameSite(verificationCookieSameSite)
            .maxAge(Duration.ZERO)
            .build();

    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
        .build();
  }

  /**
   * Requests another verification code without exposing whether the email maps to a verifiable
   * user.
   *
   * <p>The endpoint expects an email body and delegates the decision to {@link
   * ResendEmailVerificationUseCase}. It always returns {@code 202 Accepted} with the same generic
   * message when the request is syntactically valid. When the application creates a new
   * verification session, the response writes or replaces the verification cookie as HttpOnly,
   * scoped to {@code /}, using the configured Secure and SameSite policies and the
   * application-provided session TTL. Validation and malformed-body errors are handled globally.
   *
   * @param request validated email address from the JSON request body
   * @return generic accepted response, optionally with a refreshed verification-session cookie
   */
  @Operation(
      summary = "Request another email verification code",
      description =
          "Accepts a verification resend request and returns a generic accepted response. The"
              + " response intentionally does not reveal whether the email exists, whether a user"
              + " can be verified, or whether a code was sent. When applicable, the response may"
              + " set or refresh the PLATFORMCORE_VERIF verification cookie.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Resend request accepted with a generic anti-enumeration response.",
        headers =
            @Header(
                name = "Set-Cookie",
                description =
                    "May set or refresh the PLATFORMCORE_VERIF verification cookie. The cookie value is"
                        + " sensitive and is not documented.",
                schema = @Schema(type = "string")),
        content =
            @Content(
                mediaType = "application/json",
                schema =
                    @Schema(
                        implementation = Map.class,
                        description = "Generic response containing a message field."))),
    @ApiResponse(
        responseCode = "400",
        description = "The request body is malformed or fails validation.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping("/verification/resend")
  public ResponseEntity<?> resendVerification(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Email address submitted for a generic verification resend request.",
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = ResendVerificationRequest.class)))
          @Valid
          @RequestBody
          ResendVerificationRequest request) {
    var result =
        resendEmailVerificationUseCase.resend(
            new ResendEmailVerificationUseCase.Command(request.email()));
    if (result.verificationSessionId() == null) {
      return ResponseEntity.accepted().body(GENERIC_RESPONSE_RESEND);
    }

    ResponseCookie cookie =
        ResponseCookie.from(verificationCookieName, result.verificationSessionId().toString())
            .httpOnly(true)
            .secure(verificationCookieSecure)
            .path("/")
            .sameSite(verificationCookieSameSite)
            .maxAge(Duration.ofSeconds(result.verificationTtlSeconds()))
            .build();

    return ResponseEntity.accepted()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(GENERIC_RESPONSE_RESEND);
  }

  /**
   * Reads a cookie by exact name and returns {@code null} for absent cookie arrays so callers can
   * keep privacy-preserving response shapes.
   */
  private String readCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
