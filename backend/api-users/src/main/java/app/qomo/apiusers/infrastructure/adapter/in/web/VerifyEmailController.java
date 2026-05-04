package app.qomo.apiusers.infrastructure.adapter.in.web;

import app.qomo.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.qomo.apiusers.application.port.in.VerifyEmailUseCase;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.ResendVerificationRequest;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.VerifyEmailRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
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
      @Value("${qomo.security.verification.cookie.name:QOMO_VERIF}") String verificationCookieName,
      @Value("${qomo.security.verification.cookie.secure:false}") boolean verificationCookieSecure,
      @Value("${qomo.security.verification.cookie.same-site:Lax}")
          String verificationCookieSameSite) {
    this.verifyEmailUseCase = verifyEmailUseCase;
    this.resendEmailVerificationUseCase = resendEmailVerificationUseCase;
    this.verificationCookieName = verificationCookieName;
    this.verificationCookieSecure = verificationCookieSecure;
    this.verificationCookieSameSite = verificationCookieSameSite;
  }

  @PostMapping("/verify-email")
  public ResponseEntity<?> verifyEmail(
      HttpServletRequest servletRequest, @Valid @RequestBody VerifyEmailRequest request) {
    String sessionId = readCookie(servletRequest, verificationCookieName);
    if (sessionId == null) {
      return ResponseEntity.accepted().body(GENERIC_RESPONSE);
    }

    UUID parsedSessionId;
    try {
      parsedSessionId = UUID.fromString(sessionId);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.accepted().body(GENERIC_RESPONSE);
    }

    var ok =
        verifyEmailUseCase.verify(new VerifyEmailUseCase.Command(parsedSessionId, request.code()));
    if (!ok) {
      return ResponseEntity.accepted().body(GENERIC_RESPONSE);
    }

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

  @PostMapping("/verification/resend")
  public ResponseEntity<?> resendVerification(
      @Valid @RequestBody ResendVerificationRequest request) {
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
