package app.platformcore.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.Set;

/**
 * Current authenticated user representation returned after login.
 *
 * <p>The response exposes only identity, account state, and role names; it never includes JWTs,
 * password hashes, verification codes, or internal tokens.
 *
 * @param id stable UUID string for the authenticated user
 * @param email email address associated with the authenticated account
 * @param active whether the account is currently usable by application policy
 * @param emailVerified whether the email-verification flow has completed
 * @param roles role names currently assigned to the account
 */
@Schema(description = "Current authenticated user representation.")
public record CurrentUserResponse(
    @Schema(
            description = "Stable user resource identifier.",
            format = "uuid",
            example = "2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec")
        String id,
    @Schema(
            description = "Email address associated with the authenticated account.",
            format = "email",
            example = "user@example.com")
        String email,
    @Schema(description = "Whether the account is currently usable by application policy.")
        boolean active,
    @Schema(description = "Whether the account has completed email verification.")
        boolean emailVerified,
    @Schema(description = "Role names currently assigned to the account.", example = "[\"USER\"]")
        Set<String> roles) {
  public CurrentUserResponse {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(email, "email cannot be null");
    roles = Set.copyOf(Objects.requireNonNull(roles, "roles cannot be null"));
  }
}
