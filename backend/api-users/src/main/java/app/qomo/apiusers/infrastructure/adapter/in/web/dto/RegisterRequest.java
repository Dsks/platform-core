package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Public registration request body accepted by the authentication API.
 *
 * @param email address to register; must be non-blank and syntactically valid
 * @param password raw password from the request body; must be non-blank and is never returned by
 *     web responses
 */
public record RegisterRequest(@Email @NotBlank String email, @NotBlank String password) {}
