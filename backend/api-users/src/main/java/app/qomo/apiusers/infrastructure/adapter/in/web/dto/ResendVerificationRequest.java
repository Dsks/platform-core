package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for asking the verification flow to send another code.
 *
 * <p>The endpoint using this DTO responds generically for syntactically valid requests so account
 * existence is not exposed by the web contract.
 *
 * @param email address to evaluate for a possible resend; must be non-blank and email-shaped
 */
public record ResendVerificationRequest(@Email @NotBlank String email) {}
