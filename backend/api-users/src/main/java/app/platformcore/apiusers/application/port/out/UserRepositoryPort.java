package app.platformcore.apiusers.application.port.out;

import app.platformcore.apiusers.application.port.in.GetUserUseCase.DeletedFilter;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.SortBy;
import app.platformcore.apiusers.application.port.in.GetUserUseCase.SortDirection;
import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.PasswordHash;
import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Abstracts persistence for user aggregates and their assigned roles.
 *
 * <p>The application expects implementations to keep user identity, email, password hash,
 * activation, verification, login timestamps, and role assignments consistent when saving and
 * reading a user. Email addresses and password hashes are sensitive and must not be logged or
 * surfaced in clear text through infrastructure errors.
 *
 * <p>This contract does not decide registration, login, verification, or authorization rules; it
 * only provides the storage operations those use cases require.
 */
public interface UserRepositoryPort {

  /**
   * Persists the complete user aggregate and returns the stored representation.
   *
   * <p>Implementations should persist role assignments together with the user state so callers do
   * not observe a partially updated aggregate.
   *
   * @param user user aggregate to create or update
   * @return the persisted user representation
   */
  User save(User user);

  /**
   * Finds a user aggregate by its stable identifier.
   *
   * @param id user identifier assigned by the domain model
   * @return the user with its roles when present
   */
  Optional<User> findById(UserId id);

  /**
   * Finds a user aggregate by normalized email.
   *
   * @param email email value used by the application for identity lookup
   * @return the matching user with its roles when present
   */
  Optional<User> findByEmail(String email);

  /** Lists users with stable pagination ordered by creation time and id tie-breaker. */
  default Page<User> findAllPage(int page, int size) {
    return findAllPage(null, page, size);
  }

  /**
   * Lists users with optional case-insensitive email substring search and stable pagination.
   *
   * @param emailSearch normalized email substring search, or {@code null} for no search
   */
  default Page<User> findAllPage(String emailSearch, int page, int size) {
    return findAllPage(emailSearch, DeletedFilter.EXCLUDE, null, null, page, size);
  }

  /**
   * Lists users with optional case-insensitive email substring search, account-state filters, and
   * stable pagination.
   *
   * @param emailSearch normalized email substring search, or {@code null} for no search
   * @param deleted soft-deletion visibility filter
   * @param active optional active-state filter, or {@code null} for no filter
   * @param verified optional email-verification filter, or {@code null} for no filter
   */
  default Page<User> findAllPage(
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      int page,
      int size) {
    return findAllPage(emailSearch, deleted, active, verified, null, page, size);
  }

  /**
   * Lists users with optional case-insensitive email substring search, account-state filters, role
   * filter, and stable pagination.
   *
   * @param emailSearch normalized email substring search, or {@code null} for no search
   * @param deleted soft-deletion visibility filter
   * @param active optional active-state filter, or {@code null} for no filter
   * @param verified optional email-verification filter, or {@code null} for no filter
   * @param role optional role name filter, or {@code null} for no filter
   */
  default Page<User> findAllPage(
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      String role,
      int page,
      int size) {
    return findAllPage(
        emailSearch,
        deleted,
        active,
        verified,
        role,
        SortBy.CREATED_AT,
        SortDirection.DESC,
        page,
        size);
  }

  /**
   * Lists users with filtering and explicit safe sort options.
   *
   * @param sortBy allowed sort field
   * @param sortDirection allowed sort direction
   */
  Page<User> findAllPage(
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      String role,
      SortBy sortBy,
      SortDirection sortDirection,
      int page,
      int size);

  /**
   * Lists users whose full role set is visible to the caller, excluding accounts with any other
   * role.
   */
  default Page<User> findPageByVisibleRoles(Set<String> visibleRoleNames, int page, int size) {
    return findPageByVisibleRoles(visibleRoleNames, null, page, size);
  }

  /**
   * Lists visible users with optional case-insensitive email substring search and stable
   * pagination.
   *
   * @param visibleRoleNames complete set of role names visible to the caller
   * @param emailSearch normalized email substring search, or {@code null} for no search
   */
  default Page<User> findPageByVisibleRoles(
      Set<String> visibleRoleNames, String emailSearch, int page, int size) {
    return findPageByVisibleRoles(
        visibleRoleNames, emailSearch, DeletedFilter.EXCLUDE, null, null, page, size);
  }

  /**
   * Lists visible users with optional case-insensitive email substring search, account-state
   * filters, and stable pagination.
   *
   * @param visibleRoleNames complete set of role names visible to the caller
   * @param emailSearch normalized email substring search, or {@code null} for no search
   * @param deleted soft-deletion visibility filter
   * @param active optional active-state filter, or {@code null} for no filter
   * @param verified optional email-verification filter, or {@code null} for no filter
   */
  default Page<User> findPageByVisibleRoles(
      Set<String> visibleRoleNames,
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      int page,
      int size) {
    return findPageByVisibleRoles(
        visibleRoleNames, emailSearch, deleted, active, verified, null, page, size);
  }

  /**
   * Lists visible users with optional case-insensitive email substring search, account-state
   * filters, role filter, and stable pagination.
   *
   * @param visibleRoleNames complete set of role names visible to the caller
   * @param emailSearch normalized email substring search, or {@code null} for no search
   * @param deleted soft-deletion visibility filter
   * @param active optional active-state filter, or {@code null} for no filter
   * @param verified optional email-verification filter, or {@code null} for no filter
   * @param role optional role name filter, or {@code null} for no filter
   */
  default Page<User> findPageByVisibleRoles(
      Set<String> visibleRoleNames,
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      String role,
      int page,
      int size) {
    return findPageByVisibleRoles(
        visibleRoleNames,
        emailSearch,
        deleted,
        active,
        verified,
        role,
        SortBy.CREATED_AT,
        SortDirection.DESC,
        page,
        size);
  }

  /**
   * Lists visible users with filtering and explicit safe sort options.
   *
   * @param sortBy allowed sort field
   * @param sortDirection allowed sort direction
   */
  Page<User> findPageByVisibleRoles(
      Set<String> visibleRoleNames,
      String emailSearch,
      DeletedFilter deleted,
      Boolean active,
      Boolean verified,
      String role,
      SortBy sortBy,
      SortDirection sortDirection,
      int page,
      int size);

  /**
   * Checks whether an email is already assigned to a user.
   *
   * @param email email value to check
   * @return {@code true} when any user currently owns the email
   */
  boolean existsByEmail(String email);

  /**
   * Marks a user as email-verified at the supplied application time.
   *
   * @param id user to update
   * @param now timestamp to record as the verification update time
   */
  void setVerified(UserId id, Instant now);

  /**
   * Soft deletes a user by deactivating and anonymizing sensitive account fields.
   *
   * @param id user to anonymize
   * @param anonymizedEmail replacement email unique to the deleted account
   * @param unusablePasswordHash replacement password hash that cannot be used for login
   * @param now timestamp to record as the deletion update time
   */
  void softDeleteAndAnonymize(
      UserId id, Email anonymizedEmail, PasswordHash unusablePasswordHash, Instant now);

  /** Minimal immutable pagination container for repository read models. */
  record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public Page {
      content = List.copyOf(Objects.requireNonNull(content, "content cannot be null"));
    }
  }
}
