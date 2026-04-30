package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank String code) {

}