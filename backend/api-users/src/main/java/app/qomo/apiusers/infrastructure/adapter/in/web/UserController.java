package app.qomo.apiusers.infrastructure.adapter.in.web;

import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.CreateUserRequest;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse.Status;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Users", description = "Authenticated user management and lookup endpoints.")
@SecurityRequirement(name = "qomoAuthCookie")
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
  @Operation(
      summary = "Create a user account",
      description =
          "Creates a user account from an authenticated administrative context. The endpoint"
              + " requires the QOMO_AUTH cookie, a valid CSRF token header, and an ADMIN or"
              + " SUPERADMIN role. It returns a generic accepted acknowledgement and does not"
              + " expose credential material or persistence details.",
      parameters =
          @Parameter(
              name = "X-XSRF-TOKEN",
              in = ParameterIn.HEADER,
              required = true,
              description =
                  "CSRF token header for this protected state-changing request. Obtain the token"
                      + " from the CSRF bootstrap endpoint and do not log or expose its value.",
              schema = @Schema(type = "string")))
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "User creation request accepted.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = RegistrationAcceptedResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "The request body is malformed or fails validation.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "Forbidden when the authenticated user lacks the required role or when CSRF validation"
                + " fails. The response body is not guaranteed because the rejection may happen"
                + " before the request reaches the controller.",
        content = @Content()),
    @ApiResponse(
        responseCode = "409",
        description = "The submitted email conflicts with an existing account.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @PostMapping
  public ResponseEntity<?> create(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "User creation data submitted by an administrative client.",
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = CreateUserRequest.class)))
          @Valid
          @RequestBody
          CreateUserRequest request) {

    createUser.create(
        new CreateUserUseCase.Command(request.email(), request.password(), request.roles()));

    var body =
        new RegistrationAcceptedResponse(
            java.util.UUID.randomUUID().toString(),
            Status.VERIFICATION_REQUIRED,
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
  @Operation(
      summary = "Get a user by id",
      description =
          "Returns a safe user representation by UUID for an authenticated request. The response"
              + " excludes password hashes, tokens, verification codes, and other credential"
              + " material. CSRF is not required for this read-only endpoint.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "User found.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "The user id path variable is not a valid UUID.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Authentication requirements are not satisfied.",
        content = @Content()),
    @ApiResponse(responseCode = "404", description = "User not found.", content = @Content())
  })
  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getById(
      @Parameter(
              description = "User resource identifier.",
              required = true,
              schema =
                  @Schema(
                      type = "string",
                      format = "uuid",
                      example = "2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec"))
          @PathVariable
          @Pattern(regexp = UUID_PATTERN, message = "must be a valid UUID")
          String id) {
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
