package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Acknowledgement returned by registration-style HTTP flows.
 *
 * @param requestId opaque request identifier for client-side correlation; not a user identifier
 * @param status stable client-visible registration outcome
 * @param message client-facing message for the outcome
 */
@Schema(
    description =
        "Acknowledgement for registration-style flows. VERIFICATION_REQUIRED does not distinguish"
            + " new accounts from existing unverified accounts.")
public record RegistrationAcceptedResponse(
    @Schema(
            description =
                "Opaque request identifier for client-side correlation. This is not a user"
                    + " identifier.",
            example = "req-123")
        String requestId,
    @Schema(
            description =
                "Registration outcome. VERIFICATION_REQUIRED covers both new accounts and existing"
                    + " unverified accounts.",
            allowableValues = {"VERIFICATION_REQUIRED", "ALREADY_REGISTERED"},
            example = "VERIFICATION_REQUIRED")
        Status status,
    @Schema(
            description = "Client-facing message for the registration outcome.",
            example = "If the email is valid, you'll receive next steps.")
        String message) {

  public enum Status {
    VERIFICATION_REQUIRED,
    ALREADY_REGISTERED
  }
}
