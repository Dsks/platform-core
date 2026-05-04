package app.qomo.apiusers.domain.port.out;

import app.qomo.apiusers.domain.model.PasswordHash;

public interface PasswordVerifierPort {

  boolean matches(String rawPassword, PasswordHash passwordHash);
}
