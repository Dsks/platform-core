package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

/**
 * Request body for creating a user through the users HTTP API.
 *
 * @param email address for the new account; must be non-blank and syntactically valid
 * @param password raw password from the request body; must be non-blank and is never returned by
 *     web responses
 * @param roles role names supplied by the caller for application-level validation and assignment
 */
public record CreateUserRequest(
    @Email @NotBlank String email, @NotBlank String password, Set<String> roles) {}
