package app.qomo.apiusers.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class User {

  private final UserId id;
  private final Email email;
  private PasswordHash passwordHash;

  private boolean isActive;
  private boolean isVerified;

  private Instant lastLogin;
  private final Instant createdAt;
  private Instant updatedAt;

  private final Set<Role> roles = new HashSet<>();

  private User(UserId id, Email email, PasswordHash passwordHash, Instant now) {
    this.id = Objects.requireNonNull(id);
    this.email = Objects.requireNonNull(email);
    this.passwordHash = Objects.requireNonNull(passwordHash);

    this.isActive = true;
    this.isVerified = false;

    this.createdAt = now;
    this.updatedAt = now;
  }

  public static User createNew(UserId id, Email email, PasswordHash passwordHash, Instant now) {
    return new User(id, email, passwordHash, now);
  }

  public static User restore(
      UserId id,
      Email email,
      PasswordHash passwordHash,
      boolean isActive,
      boolean isVerified,
      Instant lastLogin,
      Instant createdAt,
      Instant updatedAt,
      Set<Role> roles) {
    var user =
        new User(
            id, email, passwordHash, Objects.requireNonNull(createdAt, "createdAt cannot be null"));
    user.isActive = isActive;
    user.isVerified = isVerified;
    user.lastLogin = lastLogin;
    user.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    user.roles.addAll(Objects.requireNonNull(roles, "roles cannot be null"));
    return user;
  }

  public void addRole(Role role, Instant now) {
    roles.add(Objects.requireNonNull(role, "role cannot be null"));
    touch(now);
  }

  public void verify(Instant now) {
    this.isVerified = true;
    touch(now);
  }

  public void deactivate(Instant now) {
    if (!this.isActive) {
      return;
    }
    this.isActive = false;
    touch(now);
  }

  public void activate(Instant now) {
    if (this.isActive) {
      return;
    }
    this.isActive = true;
    touch(now);
  }

  public void recordLogin(Instant now) {
    this.lastLogin = Objects.requireNonNull(now, "now cannot be null");
    touch(now);
  }

  public void changePassword(PasswordHash newHash, Instant now) {
    this.passwordHash = Objects.requireNonNull(newHash, "newHash cannot be null");
    touch(now);
  }

  private void touch(Instant now) {
    this.updatedAt = Objects.requireNonNull(now, "now cannot be null");
  }
  
  public UserId id() {
    return id;
  }

  public Email email() {
    return email;
  }

  public PasswordHash passwordHash() {
    return passwordHash;
  }

  public boolean isActive() {
    return isActive;
  }

  public boolean isVerified() {
    return isVerified;
  }

  public Instant lastLogin() {
    return lastLogin;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Set<Role> roles() {
    return Set.copyOf(roles);
  }
}
