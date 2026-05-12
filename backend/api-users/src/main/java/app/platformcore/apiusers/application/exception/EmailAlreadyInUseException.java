package app.platformcore.apiusers.application.exception;

import java.util.Map;

/**
 * Indicates that a registration or email-change use case cannot proceed because the email address
 * conflicts with an existing account.
 *
 * <p>This is an expected application-layer uniqueness failure. HTTP adapters should generally map
 * it to {@code 409 Conflict} when the product flow intentionally exposes uniqueness feedback. In
 * anti-enumeration flows, adapters should prefer a generic response that does not confirm whether
 * an account exists. The email value is personal data and may be carried in diagnostics by this
 * contract; presentation and logging boundaries should redact it when appropriate and must not add
 * passwords, tokens, verification codes, or secrets to the message.
 */
public final class EmailAlreadyInUseException extends ApplicationException {

  /**
   * Records the conflicting email address for controlled diagnostics.
   *
   * @param email the email address that failed the uniqueness check; treat it as PII at adapters
   *     and log sinks
   */
  public EmailAlreadyInUseException(String email) {
    super("USER_EMAIL_ALREADY_IN_USE", "Email already in use", Map.of("email", email));
  }
}
