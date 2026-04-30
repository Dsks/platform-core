package app.qomo.apiusers.infrastructure.adapter.in.web;

import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.CreateUserRequest;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@Validated
public class UserController {

  private static final String UUID_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

  private final CreateUserUseCase createUser;
  private final GetUserUseCase getUser;

  public UserController(CreateUserUseCase createUser, GetUserUseCase getUser) {
    this.createUser = createUser;
    this.getUser = getUser;
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest request) {

    createUser.create(
        new CreateUserUseCase.Command(request.email(), request.password(), request.roles()));

    var body =
        new RegistrationAcceptedResponse(
            java.util.UUID.randomUUID().toString(),
            "If the email is valid, you'll receive next steps.");

    return ResponseEntity.accepted().body(body);
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getById(
      @PathVariable @Pattern(regexp = UUID_PATTERN, message = "must be a valid UUID") String id) {
    return getUser
        .getById(UserId.of(id))
        .map(
            u ->
                new UserResponse(
                    u.id().toString(),
                    u.email().value(),
                    u.isActive(),
                    u.isVerified(),
                    u.lastLogin(),
                    u.createdAt(),
                    u.updatedAt(),
                    u.roles().stream().map(Role::name).collect(Collectors.toSet())))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
