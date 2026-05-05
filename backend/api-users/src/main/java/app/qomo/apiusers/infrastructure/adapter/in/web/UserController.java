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

/**
 * HTTP boundary for user creation and user lookup under {@code /v1/users}.
 *
 * <p>The controller delegates writes to {@link CreateUserUseCase} and reads to {@link
 * GetUserUseCase}. It does not issue or consume authentication cookies itself; validation and
 * application exceptions are translated by {@link ApiExceptionHandler}.
 */
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

  /**
   * Accepts a user-creation request and delegates account creation to the application layer.
   *
   * <p>The endpoint expects a JSON body with email, password, and caller-supplied role names, then
   * returns {@code 202 Accepted} with a generic acknowledgement after the create command returns
   * successfully. The generated request id is not a user id. Existing-email conflicts, validation
   * failures, and malformed JSON are delegated to the global exception handler. No cookies are read
   * or written.
   *
   * @param request validated user-creation body
   * @return generic accepted response after the create command succeeds
   */
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

  /**
   * Reads a user representation by UUID path segment.
   *
   * <p>The path variable is validated at the HTTP edge before conversion to {@link UserId}. A found
   * user returns {@code 200 OK} with {@link UserResponse}; an absent user returns {@code 404 Not
   * Found}; an invalid UUID format is handled as a {@code 400 Bad Request} validation error by
   * {@link ApiExceptionHandler}. No cookies are read or written.
   *
   * @param id UUID string identifying the user resource
   * @return user representation or an empty not-found response
   */
  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getById(
      @PathVariable @Pattern(regexp = UUID_PATTERN, message = "must be a valid UUID") String id) {
    return getUser
        .getById(UserId.of(id))
        // Map explicitly so credential material stays outside the HTTP representation.
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
