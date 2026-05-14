package app.platformcore.apiusers.application.port.in;

import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Exposes user lookup by application-level user identity.
 *
 * <p>This port is intended for inbound adapters such as web controllers, internal jobs, consumers,
 * or application tests that need to read an already identified user. Implementations are
 * responsible for retrieving the aggregate through the application boundary and enforcing
 * administrative read visibility where the method contract includes actor roles. Transport parsing,
 * response DTO mapping, and concrete persistence details remain outside the port.
 *
 * <p>The returned domain object can contain personal data such as email and account state. Callers
 * should only project fields required by their adapter contract and should avoid logging the full
 * aggregate.
 */
public interface GetUserUseCase {

  /**
   * Looks up a user by id from an administrative read context.
   *
   * @param id typed user identifier; adapters are responsible for parsing external UUID strings
   *     before invoking the port
   * @param actorRoles normalized or raw role names from the authenticated actor
   * @return the matching visible user when present, or {@link Optional#empty()} when no user exists
   *     for the supplied identifier
   */
  Optional<User> getByIdForAdmin(UserId id, Set<String> actorRoles);

  /**
   * Partially updates a user with administrative authorization and idempotent supported fields.
   *
   * @param command update command with target id, editable fields, and actor context
   * @return updated user aggregate
   */
  User update(UpdateCommand command);

  /**
   * Lists users visible to an administrative actor.
   *
   * @param query zero-based pagination request
   * @param actorRoles normalized or raw role names from the authenticated actor
   * @return paginated safe user summaries
   */
  PageResult listForAdmin(Query query, Set<String> actorRoles);

  /** Soft-deletion visibility filter for administrative user listings. */
  enum DeletedFilter {
    EXCLUDE,
    ONLY,
    ALL
  }

  /** Sortable fields for administrative user listings. */
  enum SortBy {
    EMAIL,
    CREATED_AT,
    UPDATED_AT,
    LAST_LOGIN_AT,
    DELETED_AT,
    ROLE
  }

  /** Sort direction for administrative user listings. */
  enum SortDirection {
    ASC,
    DESC
  }

  /** Pagination and filtering request for user listing. */
  record Query(
      int page,
      int size,
      String search,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      String role,
      SortBy sortBy,
      SortDirection sortDirection) {

    public Query(int page, int size) {
      this(page, size, null);
    }

    public Query(int page, int size, String search) {
      this(page, size, search, DeletedFilter.EXCLUDE, null, null);
    }

    public Query(
        int page,
        int size,
        String search,
        DeletedFilter deleted,
        Boolean active,
        Boolean verified) {
      this(page, size, search, deleted, active, verified, null);
    }

    public Query(
        int page,
        int size,
        String search,
        DeletedFilter deleted,
        Boolean active,
        Boolean verified,
        String role) {
      this(
          page,
          size,
          search,
          deleted,
          active,
          verified,
          role,
          SortBy.CREATED_AT,
          SortDirection.DESC);
    }

    public Query {
      deleted = deleted == null ? DeletedFilter.EXCLUDE : deleted;
      sortBy = sortBy == null ? SortBy.CREATED_AT : sortBy;
      sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
    }
  }

  /** Partial user update command for fields supported by the administrative endpoint. */
  record UpdateCommand(UserId id, Boolean active, Set<String> actorRoles, String actorUserId) {

    public UpdateCommand {
      Objects.requireNonNull(id, "id cannot be null");
      actorRoles =
          actorRoles == null
              ? Set.of()
              : actorRoles.stream()
                  .filter(Objects::nonNull)
                  .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
  }

  /** Safe user summary for administrative listing. */
  record UserSummary(
      String id,
      String email,
      boolean active,
      boolean emailVerified,
      Set<String> roles,
      Instant lastLogin,
      Instant createdAt,
      Instant updatedAt,
      Instant deletedAt) {

    public UserSummary {
      Objects.requireNonNull(id, "id cannot be null");
      Objects.requireNonNull(email, "email cannot be null");
      roles = Set.copyOf(Objects.requireNonNull(roles, "roles cannot be null"));
    }
  }

  /** Minimal paginated result shape for user listing. */
  record PageResult(
      List<UserSummary> content, int page, int size, long totalElements, int totalPages) {

    public PageResult {
      content = List.copyOf(Objects.requireNonNull(content, "content cannot be null"));
    }
  }
}
