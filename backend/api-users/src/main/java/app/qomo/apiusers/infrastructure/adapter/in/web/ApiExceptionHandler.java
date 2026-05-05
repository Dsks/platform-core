package app.qomo.apiusers.infrastructure.adapter.in.web;

import app.qomo.apiusers.application.exception.ApplicationException;
import app.qomo.apiusers.application.exception.EmailAlreadyInUseException;
import app.qomo.apiusers.application.observability.PiiUtil;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Translates application and request-binding failures into HTTP problem responses for the web API.
 *
 * <p>The handler owns the edge contract for validation errors, malformed JSON, and selected
 * application error codes. It also preserves registration anti-enumeration by converting
 * duplicate-email failures on the public registration route into the same generic accepted
 * response.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
  private static final String MALFORMED_REQUEST_CODE = "MALFORMED_REQUEST";

  /**
   * Maps application exception codes to stable HTTP statuses and problem details.
   *
   * <p>Client-visible params are filtered for codes that could disclose account state; unknown
   * application codes fall back to {@code 400 Bad Request}.
   *
   * @param ex application exception raised by a use case
   * @param req current servlet request for structured logging
   * @return problem detail with Qomo problem type and sanitized params where required
   */
  @ExceptionHandler(ApplicationException.class)
  public ProblemDetail handleApplication(ApplicationException ex, HttpServletRequest req) {
    var params = new HashMap<String, Object>(ex.params());

    log.warn(
        "application_error code={} method={} path={} params={}",
        ex.code(),
        req.getMethod(),
        req.getRequestURI(),
        params);

    HttpStatus status =
        switch (ex.code()) {
          case "INVALID_COMMAND" -> HttpStatus.BAD_REQUEST;
          case "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "USER_EMAIL_ALREADY_IN_USE" -> HttpStatus.CONFLICT;
          case "INVALID_CREDENTIALS" -> HttpStatus.UNAUTHORIZED;
          case "FORBIDDEN_OPERATION" -> HttpStatus.FORBIDDEN;
          case "USER_INACTIVE", "USER_NOT_VERIFIED" -> HttpStatus.FORBIDDEN;
          default -> HttpStatus.BAD_REQUEST;
        };

    var pd = buildProblem(status, ex.code(), ex.getMessage());
    pd.setProperty("params", safeClientParams(ex.code(), ex.params()));
    return pd;
  }

  /**
   * Handles duplicate-email failures with route-specific privacy behavior.
   *
   * <p>For {@code POST /v1/auth/register}, this returns {@code 202 Accepted} with the same generic
   * body as a successful public registration so callers cannot distinguish an existing account.
   * Other routes receive {@code 409 Conflict} with empty params. The logged email value is
   * fingerprinted rather than written in raw form.
   *
   * @param ex duplicate-email exception raised by the application layer
   * @param req current servlet request used to choose the public-registration contract
   * @return generic accepted response for public registration, or a conflict problem elsewhere
   */
  @ExceptionHandler(EmailAlreadyInUseException.class)
  public ResponseEntity<?> handleEmailAlreadyInUse(
      EmailAlreadyInUseException ex, HttpServletRequest req) {
    var email_fp = PiiUtil.emailFingerprint(String.valueOf(ex.params().get("email")));
    log.info(
        "email_already_in_use method={} path={} email={}",
        req.getMethod(),
        req.getRequestURI(),
        email_fp);

    if (isPublicRegistration(req)) {
      var body =
          new RegistrationAcceptedResponse(
              java.util.UUID.randomUUID().toString(),
              "If the email is valid, you'll receive next steps.");
      return ResponseEntity.accepted().body(body);
    }

    var pd = buildProblem(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
    pd.setProperty("params", Map.of());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
  }

  /**
   * Converts {@code @Valid} request-body failures into a {@code 400 Bad Request} problem response.
   *
   * @param ex binding exception containing field-level validation failures
   * @param req current servlet request for structured logging
   * @return validation problem with client-readable field errors
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {

    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream().map(this::formatFieldError).toList();

    log.warn(
        "validation_error method={} path={} errors={}",
        req.getMethod(),
        req.getRequestURI(),
        errors);

    var pd =
        buildProblem(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_CODE, "Request validation failed");
    pd.setProperty("errors", errors);
    return pd;
  }

  /**
   * Converts path, query, and method-level validation failures into a validation problem response.
   *
   * @param ex validation exception raised before the endpoint body runs
   * @param req current servlet request for structured logging
   * @return {@code 400 Bad Request} problem with validation details
   */
  @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
  public ProblemDetail handleConstraintViolation(Exception ex, HttpServletRequest req) {
    List<String> errors =
        switch (ex) {
          case ConstraintViolationException cve ->
              cve.getConstraintViolations().stream()
                  .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                  .toList();
          case HandlerMethodValidationException hmve -> {
            hmve.getMessage();
            yield List.of(hmve.getMessage());
          }
          default -> List.of("Request validation failed");
        };

    log.warn(
        "constraint_validation_error method={} path={} errors={}",
        req.getMethod(),
        req.getRequestURI(),
        errors);

    var pd =
        buildProblem(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_CODE, "Request validation failed");
    pd.setProperty("errors", errors);
    return pd;
  }

  /**
   * Converts unreadable or malformed JSON bodies into a stable malformed-request problem response.
   *
   * @param ex converter exception raised while reading the request body
   * @param req current servlet request for structured logging
   * @return {@code 400 Bad Request} problem without echoing request-body content
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleMalformedRequest(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    log.warn(
        "malformed_request method={} path={} error={}",
        req.getMethod(),
        req.getRequestURI(),
        ex.getMostSpecificCause().getMessage());

    return buildProblem(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST_CODE, "Malformed request body");
  }

  private String formatFieldError(FieldError error) {
    return error.getField() + ": " + error.getDefaultMessage();
  }

  /** Creates the shared problem-detail shape with the Qomo problem URI used by web clients. */
  private ProblemDetail buildProblem(HttpStatus status, String code, String detail) {
    var pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(code);
    pd.setType(URI.create("https://qomo.app/problems/" + code));
    return pd;
  }

  /** Identifies the public registration route that must keep duplicate-email responses generic. */
  private boolean isPublicRegistration(HttpServletRequest req) {
    return "POST".equalsIgnoreCase(req.getMethod())
        && "/v1/auth/register".equals(req.getRequestURI());
  }

  /** Removes client-visible params for errors whose raw details could leak account existence. */
  private Object safeClientParams(String code, Map<String, Object> original) {
    if ("USER_EMAIL_ALREADY_IN_USE".equals(code)) {
      // Client-visible params must not confirm which email is already registered.
      return Map.of();
    }
    return original;
  }
}
