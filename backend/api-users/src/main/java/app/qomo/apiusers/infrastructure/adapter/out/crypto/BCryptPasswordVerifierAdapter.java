package app.qomo.apiusers.infrastructure.adapter.out.crypto;

import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.port.out.PasswordVerifierPort;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptPasswordVerifierAdapter implements PasswordVerifierPort {

  private final BCryptPasswordEncoder encoder;

  public BCryptPasswordVerifierAdapter(BCryptPasswordEncoder encoder) {
    this.encoder = Objects.requireNonNull(encoder, "encoder cannot be null");
  }

  @Override
  public boolean matches(String rawPassword, PasswordHash passwordHash) {
    return encoder.matches(rawPassword, passwordHash.value());
  }
}