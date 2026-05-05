package app.qomo.apiusers.infrastructure.adapter.out.security;

import app.qomo.apiusers.application.port.out.CurrentUserPort;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Outbound adapter for {@link CurrentUserPort} backed by Spring Security's current {@link
 * SecurityContextHolder}.
 *
 * <p>It hides Spring Security types from the application layer and exposes the authenticated
 * principal name plus normalized role names. The adapter only reads the thread-bound security
 * context; it does not mutate authentication state.
 */
public class SecurityCurrentUserAdapter implements CurrentUserPort {

  /**
   * Reads the authenticated principal name from the current security context.
   *
   * @return principal name wrapped in an {@link Optional}, or empty when no authentication is
   *     available
   */
  @Override
  public Optional<String> userId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      return Optional.empty();
    }
    return Optional.of(authentication.getName());
  }

  /**
   * Reads role authorities from the current security context.
   *
   * <p>Only authorities with the Spring {@code ROLE_} prefix are exposed. The prefix is stripped
   * and role names are upper-cased before returning an immutable set to the application layer.
   *
   * @return normalized role names, or an empty set when no authentication or authorities exist
   */
  @Override
  public Set<String> roles() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
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
}
