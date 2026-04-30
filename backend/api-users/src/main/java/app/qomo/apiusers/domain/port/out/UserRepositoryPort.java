package app.qomo.apiusers.domain.port.out;

import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;

public interface UserRepositoryPort {

  User save(User user);

  Optional<User> findById(UserId id);

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  void setVerified(UserId id, Instant now);
}
