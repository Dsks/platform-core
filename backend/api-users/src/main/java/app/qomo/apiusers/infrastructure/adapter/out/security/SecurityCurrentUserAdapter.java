package app.qomo.apiusers.infrastructure.adapter.out.security;

import app.qomo.apiusers.domain.port.out.CurrentUserPort;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityCurrentUserAdapter implements CurrentUserPort {

  @Override
  public Optional<String> userId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      return Optional.empty();
    }
    return Optional.of(authentication.getName());
  }

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
