package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted to create a browser authentication session.
 *
 * @param email account email used for credential lookup; must be non-blank and syntactically valid
 * @param password raw password from the request body; must be non-blank and is never echoed
 */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
