package app.qomo.apiusers.domain.port.out;

import java.util.Optional;
import java.util.Set;

public interface CurrentUserPort {

  Optional<String> userId();

  Set<String> roles();
}