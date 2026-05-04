package app.qomo.apiusers.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.exception.RoleNotFoundException;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.PasswordHasherPort;
import app.qomo.apiusers.application.port.out.RoleRepositoryPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.RoleId;
import app.qomo.apiusers.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapTest {

  private static final Instant NOW = Instant.parse("2026-04-09T12:00:00Z");
  private static final String SUPERADMIN_EMAIL = "superadmin@qomo.app";

  @Mock private UserRepositoryPort userRepository;

  @Mock private RoleRepositoryPort roleRepository;

  @Mock private PasswordHasherPort passwordHasher;

  private ClockPort clock;

  @BeforeEach
  void setUp() {
    clock = () -> NOW;
  }

  @Test
  void run_shouldCreateAndSaveVerifiedSuperAdmin_whenMissingAndPasswordConfigured()
      throws Exception {
    var superAdminRole = Role.of(RoleId.of("1018864f-6121-4c4c-88cd-7125f5be7a8a"), "SUPERADMIN");

    when(userRepository.existsByEmail(SUPERADMIN_EMAIL)).thenReturn(false);
    when(passwordHasher.hash("bootstrap-secret")).thenReturn(new PasswordHash("bcrypt-hash"));
    when(roleRepository.findByName("SUPERADMIN")).thenReturn(Optional.of(superAdminRole));

    bootstrap("bootstrap-secret").run(new DefaultApplicationArguments(new String[0]));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());

    User saved = userCaptor.getValue();
    assertThat(saved.id().toString()).isEqualTo("00000000-0000-0000-0000-000000000010");
    assertThat(saved.email().value()).isEqualTo(SUPERADMIN_EMAIL);
    assertThat(saved.passwordHash().value()).isEqualTo("bcrypt-hash");
    assertThat(saved.roles()).containsExactly(superAdminRole);
    assertThat(saved.isVerified()).isTrue();
    assertThat(saved.createdAt()).isEqualTo(NOW);
    assertThat(saved.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void run_shouldSkipBootstrap_whenInitialPasswordIsBlank() throws Exception {
    bootstrap("   ").run(new DefaultApplicationArguments(new String[0]));

    verify(userRepository, never()).existsByEmail(any());
    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void run_shouldSkipBootstrap_whenUserAlreadyExists() throws Exception {
    when(userRepository.existsByEmail(SUPERADMIN_EMAIL)).thenReturn(true);

    bootstrap("bootstrap-secret").run(new DefaultApplicationArguments(new String[0]));

    verify(userRepository).existsByEmail(SUPERADMIN_EMAIL);
    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void run_shouldFailWithRoleNotFound_whenSuperAdminRoleIsMissing() {
    when(userRepository.existsByEmail(SUPERADMIN_EMAIL)).thenReturn(false);
    when(passwordHasher.hash("bootstrap-secret")).thenReturn(new PasswordHash("bcrypt-hash"));
    when(roleRepository.findByName("SUPERADMIN")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> bootstrap("bootstrap-secret").run(new DefaultApplicationArguments(new String[0])))
        .isInstanceOf(RoleNotFoundException.class)
        .hasMessageContaining("Role not found");

    verify(userRepository, never()).save(any());
  }

  private SuperAdminBootstrap bootstrap(String initialPassword) {
    return new SuperAdminBootstrap(
        userRepository, roleRepository, passwordHasher, clock, initialPassword, SUPERADMIN_EMAIL);
  }
}
