package app.qomo.apiusers.domain.port.out;

import app.qomo.apiusers.domain.model.Role;
import java.util.Optional;

public interface RoleRepositoryPort {

  Optional<Role> findByName(String name);
}
