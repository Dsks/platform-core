package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for completing email verification.
 *
 * <p>The verification session is intentionally taken from the browser cookie, not from this body.
 *
 * @param code one-time verification code supplied by the user; must not be blank
 */
public record VerifyEmailRequest(@NotBlank String code) {}
