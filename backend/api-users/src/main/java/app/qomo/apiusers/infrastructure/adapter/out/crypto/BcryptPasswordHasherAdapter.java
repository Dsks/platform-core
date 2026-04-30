package app.qomo.apiusers.infrastructure.adapter.out.crypto;

import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.port.out.PasswordHasherPort;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class BcryptPasswordHasherAdapter implements PasswordHasherPort {

  private final BCryptPasswordEncoder encoder;

  public BcryptPasswordHasherAdapter(BCryptPasswordEncoder encoder) {
    this.encoder = Objects.requireNonNull(encoder);
  }

  @Override
  public PasswordHash hash(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new IllegalArgumentException("rawPassword cannot be blank");
    }
    return new PasswordHash(encoder.encode(rawPassword));
  }
}
