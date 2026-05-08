package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Acknowledgement returned by registration-style HTTP flows.
 *
 * @param status stable client-visible registration outcome
 */
@Schema(
    description =
        "Acknowledgement for registration-style flows. VERIFICATION_REQUIRED does not distinguish"
            + " new accounts from existing unverified accounts.")
public record RegistrationAcceptedResponse(
    @Schema(
            description =
                "Registration outcome. VERIFICATION_REQUIRED covers both new accounts and existing"
                    + " unverified accounts.",
            allowableValues = {"VERIFICATION_REQUIRED", "ALREADY_REGISTERED"},
            example = "VERIFICATION_REQUIRED")
        Status status) {

  public enum Status {
    VERIFICATION_REQUIRED,
    ALREADY_REGISTERED
  }
}
