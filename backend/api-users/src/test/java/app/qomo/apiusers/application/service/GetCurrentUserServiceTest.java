package app.qomo.apiusers.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.exception.InvalidCredentialsException;
import app.qomo.apiusers.application.exception.UserInactiveException;
import app.qomo.apiusers.application.exception.UserNotFoundException;
import app.qomo.apiusers.application.port.out.CurrentUserPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.constant.SystemRoleIds;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserServiceTest {

  private static final UserId USER_ID =
      new UserId(UUID.fromString("11111111-1111-4111-8111-111111111111"));

  @Mock private CurrentUserPort currentUser;
  @Mock private UserRepositoryPort userRepository;

  private GetCurrentUserService service;

  @BeforeEach
  void setUp() {
    service = new GetCurrentUserService(currentUser, userRepository);
  }

  @Test
  void getCurrentUser_shouldReturnSafeProjection_whenPrincipalUserExistsAndIsActive() {
    var user = restoredUser(true);
    when(currentUser.userId()).thenReturn(Optional.of(USER_ID.toString()));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    var result = service.getCurrentUser();

    assertEquals(USER_ID.toString(), result.id());
    assertEquals("current.user@example.com", result.email());
    assertThat(result.active()).isTrue();
    assertThat(result.emailVerified()).isTrue();
    assertThat(result.roles()).containsExactly("USER");
    verify(userRepository).findById(USER_ID);
  }

  @Test
  void getCurrentUser_shouldThrowInvalidCredentials_whenPrincipalIsMissing() {
    when(currentUser.userId()).thenReturn(Optional.empty());

    assertThrows(InvalidCredentialsException.class, () -> service.getCurrentUser());

    verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void getCurrentUser_shouldThrowInvalidCredentials_whenPrincipalIsNotAUserId() {
    when(currentUser.userId()).thenReturn(Optional.of("not-a-uuid"));

    assertThrows(InvalidCredentialsException.class, () -> service.getCurrentUser());

    verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void getCurrentUser_shouldThrowUserNotFound_whenPrincipalUserNoLongerExists() {
    when(currentUser.userId()).thenReturn(Optional.of(USER_ID.toString()));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    var ex = assertThrows(UserNotFoundException.class, () -> service.getCurrentUser());

    assertEquals("USER_NOT_FOUND", ex.code());
    assertEquals(USER_ID.toString(), ex.params().get("userId"));
  }

  @Test
  void getCurrentUser_shouldThrowUserInactive_whenPrincipalUserIsInactive() {
    when(currentUser.userId()).thenReturn(Optional.of(USER_ID.toString()));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(restoredUser(false)));

    var ex = assertThrows(UserInactiveException.class, () -> service.getCurrentUser());

    assertEquals("USER_INACTIVE", ex.code());
  }

  private static User restoredUser(boolean active) {
    return User.restore(
        USER_ID,
        new Email("current.user@example.com"),
        new PasswordHash("hash"),
        active,
        true,
        Instant.parse("2026-03-26T10:15:30Z"),
        Instant.parse("2026-03-01T00:00:00Z"),
        Instant.parse("2026-03-26T10:15:30Z"),
        Set.of(Role.user(SystemRoleIds.USER)));
  }
}
