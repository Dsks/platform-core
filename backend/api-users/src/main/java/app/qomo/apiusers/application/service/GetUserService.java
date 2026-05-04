package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.util.Objects;
import java.util.Optional;

public final class GetUserService implements GetUserUseCase {

  private final UserRepositoryPort userRepository;

  public GetUserService(UserRepositoryPort userRepository) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
  }

  @Override
  public Optional<User> getById(UserId id) {
    Objects.requireNonNull(id, "id cannot be null");
    return userRepository.findById(id);
  }
}
