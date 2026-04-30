package app.qomo.apiusers.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record CreateUserRequest(
    @Email @NotBlank String email, @NotBlank String password, Set<String> roles) {

}
