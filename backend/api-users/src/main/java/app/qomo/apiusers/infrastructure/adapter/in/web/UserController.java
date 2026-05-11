package app.qomo.apiusers.infrastructure.adapter.in.web;

import app.qomo.apiusers.application.exception.ForbiddenOperationException;
import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.application.port.in.DeleteUserUseCase;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.CreateUserRequest;
import app.qomo.apiusers.infrastructure.adapter.in.web.dto.RegistrationAcceptedResponse;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for user creation, lookup, and administrative listing.
 *
 * <p>The controller delegates writes to {@link CreateUserUseCase} and {@link DeleteUserUseCase},
 * and reads to {@link GetUserUseCase}. It does not issue or consume authentication cookies itself;
 * validation and application exceptions are translated by {@link ApiExceptionHandler}.
 */
@RestController
@Validated
@Tag(name = "Users", description = "Authenticated user management and lookup endpoints.")
@SecurityRequirement(name = "qomoAuthCookie")
public class UserController {

  private static final String UUID_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

  private final CreateUserUseCase createUser;
  private final GetUserUseCase getUser;
  private final DeleteUserUseCase deleteUser;

  public UserController(
      CreateUserUseCase createUser, GetUserUseCase getUser, DeleteUserUseCase deleteUser) {
    this.createUser = createUser;
    this.getUser = getUser;
    this.deleteUser = deleteUser;
  }

  /**
   * Accepts a user-creation request and delegates account creation to the application layer.
   *
   * <p>The endpoint expects a JSON body with email, password, and caller-supplied role names, then
   * returns {@code 202 Accepted} with a generic acknowledgement after the create command returns
   * successfully. Existing-email conflicts, validation failures, and malformed JSON are delegated
   * to the global exception handler. No cookies are read or written.
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
  @PostMapping("/v1/users")
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

    return ResponseEntity.accepted().build();
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
  @GetMapping("/v1/users/{id}")
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
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Partially updates supported editable fields on a user resource.
   *
   * <p>The current block supports {@code active} only and applies it idempotently.
   */
  @Operation(
      summary = "Partially update a user",
      description =
          "Updates editable fields on a user resource from an authenticated administrative context. "
              + "This initial block supports only the active flag and applies it idempotently. "
              + "The endpoint requires ADMIN or SUPERADMIN plus a valid CSRF token.",
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description =
                "CSRF token header for this protected state-changing request. Obtain the token"
                    + " from the CSRF bootstrap endpoint and do not log or expose its value.",
            schema = @Schema(type = "string"))
      })
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "User updated.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "The request body is malformed, empty, or has no editable fields.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "The authenticated actor cannot update the target user or CSRF fails.",
        content = @Content()),
    @ApiResponse(responseCode = "404", description = "User not found.", content = @Content())
  })
  @PatchMapping("/v1/users/{id}")
  public ResponseEntity<UserResponse> update(
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
          String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Partial user update data. Only active is supported in this block.",
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = UpdateUserRequest.class)))
          @Valid
          @RequestBody
          UpdateUserRequest request,
      Authentication authentication) {
    if (request == null || !request.hasEditableFields()) {
      throw InvalidCommandException.missing("active");
    }

    User updated =
        getUser.update(
            new GetUserUseCase.UpdateCommand(
                UserId.of(id),
                request.active(),
                roleNames(authentication),
                authenticatedUserId(authentication)));

    return ResponseEntity.ok(toResponse(updated));
  }

  /**
   * Soft deletes and anonymizes a user from an administrative context.
   *
   * <p>The endpoint requires ADMIN or SUPERADMIN plus a valid CSRF token. It delegates role and
   * self-delete rules to the application use case and returns {@code 204 No Content} when the
   * anonymization completes.
   */
  @Operation(
      summary = "Delete a user administratively",
      description =
          "Soft deletes a user by deactivating and anonymizing sensitive account data. The endpoint"
              + " requires ADMIN or SUPERADMIN plus a valid CSRF token and never physically deletes"
              + " the user row.",
      parameters = {
        @Parameter(
            name = "X-XSRF-TOKEN",
            in = ParameterIn.HEADER,
            required = true,
            description =
                "CSRF token header for this protected state-changing request. Obtain the token"
                    + " from the CSRF bootstrap endpoint and do not log or expose its value.",
            schema = @Schema(type = "string"))
      })
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "User soft deleted.", content = @Content()),
    @ApiResponse(
        responseCode = "400",
        description = "The user id path variable is not a valid UUID.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Authentication requirements are not satisfied.",
        content = @Content()),
    @ApiResponse(
        responseCode = "403",
        description =
            "The authenticated actor cannot delete the target user or CSRF validation fails.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "User not found.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @DeleteMapping("/v1/users/{id}")
  public ResponseEntity<Void> delete(
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
          String id,
      Authentication authentication) {
    deleteUser.delete(
        new DeleteUserUseCase.Command(
            UserId.of(id), authenticatedActorUserId(authentication), roleNames(authentication)));

    return ResponseEntity.noContent().build();
  }

  /** Lists users visible to ADMIN or SUPERADMIN actors. */
  @Operation(
      summary = "List users for administration",
      description =
          "Returns a safe paginated user list. SUPERADMIN can see all users; ADMIN can see ADMIN"
              + " and USER accounts but not SUPERADMIN accounts.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Users visible to the authenticated administrative actor.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GetUserUseCase.PageResult.class))),
    @ApiResponse(
        responseCode = "400",
        description = "The pagination or filter query parameters fail validation.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Authentication requirements are not satisfied.",
        content = @Content()),
    @ApiResponse(
        responseCode = "403",
        description = "The authenticated user is not ADMIN or SUPERADMIN.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @GetMapping("/v1/users")
  public ResponseEntity<GetUserUseCase.PageResult> listForAdmin(
      @Parameter(description = "Zero-based page index.", example = "0")
          @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Page size. Maximum value is 100.", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size,
      @Parameter(
              description = "Optional case-insensitive email substring search.",
              example = "alice")
          @RequestParam(required = false)
          String search,
      @Parameter(
              description =
                  "Soft-deletion filter. Use false for non-deleted users, true for"
                      + " deleted users, or all for both.",
              example = "false")
          @RequestParam(defaultValue = "false")
          @Pattern(regexp = "false|true|all", message = "must be false, true, or all")
          String deleted,
      @Parameter(description = "Optional active-state filter.", example = "true")
          @RequestParam(required = false)
          Boolean active,
      @Parameter(description = "Optional email-verification filter.", example = "false")
          @RequestParam(required = false)
          Boolean verified,
      @Parameter(
              description = "Optional role filter. Accepted values are USER, ADMIN, SUPERADMIN.",
              example = "ADMIN",
              schema =
                  @Schema(
                      type = "string",
                      allowableValues = {"USER", "ADMIN", "SUPERADMIN"}))
          @RequestParam(required = false)
          @Pattern(
              regexp = "\\s*|\\s*(?i:USER|ADMIN|SUPERADMIN)\\s*",
              message = "must be USER, ADMIN, or SUPERADMIN")
          String role,
      @Parameter(
              description = "Sort field.",
              example = "createdAt",
              schema =
                  @Schema(
                      type = "string",
                      allowableValues = {
                        "email",
                        "createdAt",
                        "updatedAt",
                        "lastLoginAt",
                        "deletedAt",
                        "role"
                      }))
          @RequestParam(defaultValue = "createdAt")
          @Pattern(
              regexp = "(?i:email|createdAt|updatedAt|lastLoginAt|deletedAt|role)",
              message = "must be email, createdAt, updatedAt, lastLoginAt, deletedAt, or role")
          String sortBy,
      @Parameter(
              description = "Sort direction.",
              example = "desc",
              schema =
                  @Schema(
                      type = "string",
                      allowableValues = {"asc", "desc"}))
          @RequestParam(defaultValue = "desc")
          @Pattern(regexp = "(?i:asc|desc)", message = "must be asc or desc")
          String sortDirection,
      Authentication authentication) {
    return ResponseEntity.ok(
        getUser.listForAdmin(
            new GetUserUseCase.Query(
                page,
                size,
                search,
                deletedFilter(deleted),
                active,
                verified,
                roleFilter(role),
                sortBy(sortBy),
                sortDirection(sortDirection)),
            roleNames(authentication)));
  }

  private GetUserUseCase.DeletedFilter deletedFilter(String deleted) {
    if ("true".equals(deleted)) {
      return GetUserUseCase.DeletedFilter.ONLY;
    }
    if ("all".equals(deleted)) {
      return GetUserUseCase.DeletedFilter.ALL;
    }
    return GetUserUseCase.DeletedFilter.EXCLUDE;
  }

  private String roleFilter(String role) {
    if (role == null) {
      return null;
    }
    String normalized = role.trim();
    return normalized.isBlank() ? null : normalized.toUpperCase(Locale.ROOT);
  }

  private GetUserUseCase.SortBy sortBy(String sortBy) {
    String normalized = sortBy == null ? "createdAt" : sortBy;
    return switch (normalized.toLowerCase(Locale.ROOT)) {
      case "email" -> GetUserUseCase.SortBy.EMAIL;
      case "updatedat" -> GetUserUseCase.SortBy.UPDATED_AT;
      case "lastloginat" -> GetUserUseCase.SortBy.LAST_LOGIN_AT;
      case "deletedat" -> GetUserUseCase.SortBy.DELETED_AT;
      case "role" -> GetUserUseCase.SortBy.ROLE;
      default -> GetUserUseCase.SortBy.CREATED_AT;
    };
  }

  private GetUserUseCase.SortDirection sortDirection(String sortDirection) {
    String normalized = sortDirection == null ? "desc" : sortDirection;
    return "asc".equalsIgnoreCase(normalized)
        ? GetUserUseCase.SortDirection.ASC
        : GetUserUseCase.SortDirection.DESC;
  }

  private Set<String> roleNames(Authentication authentication) {
    if (authentication == null || authentication.getAuthorities() == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream()
        .map(authority -> authority.getAuthority())
        .filter(authority -> authority != null && authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .map(role -> role.toUpperCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  private String authenticatedUserId(Authentication authentication) {
    return authentication == null ? null : authentication.getName();
  }

  private UserId authenticatedActorUserId(Authentication authentication) {
    String subject = authenticatedUserId(authentication);
    if (subject == null) {
      throw new ForbiddenOperationException("Authenticated user is required");
    }
    try {
      return UserId.of(subject);
    } catch (IllegalArgumentException ex) {
      throw new ForbiddenOperationException("Authenticated user is invalid");
    }
  }

  private UserResponse toResponse(User user) {
    // Map explicitly so credential material stays outside the HTTP representation.
    return new UserResponse(
        user.id().toString(),
        user.email().value(),
        user.isActive(),
        user.isVerified(),
        user.lastLogin(),
        user.createdAt(),
        user.updatedAt(),
        user.roles().stream().map(Role::name).collect(Collectors.toSet()));
  }

  /** Request body for partial user updates; currently only the active flag is editable. */
  @Schema(description = "Partial user update request.")
  public record UpdateUserRequest(
      @Schema(
              description = "Whether the account should be active after the update.",
              nullable = true,
              example = "false")
          Boolean active) {
    boolean hasEditableFields() {
      return active != null;
    }
  }
}
