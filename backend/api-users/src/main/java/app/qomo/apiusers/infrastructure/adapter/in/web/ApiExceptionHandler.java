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

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
  private static final String MALFORMED_REQUEST_CODE = "MALFORMED_REQUEST";

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

  private ProblemDetail buildProblem(HttpStatus status, String code, String detail) {
    var pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(code);
    pd.setType(URI.create("https://qomo.app/problems/" + code));
    return pd;
  }

  private boolean isPublicRegistration(HttpServletRequest req) {
    return "POST".equalsIgnoreCase(req.getMethod())
        && "/v1/auth/register".equals(req.getRequestURI());
  }

  private Object safeClientParams(String code, Map<String, Object> original) {
    if ("USER_EMAIL_ALREADY_IN_USE".equals(code)) {
      return Map.of();
    }
    return original;
  }
}
