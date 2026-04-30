package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
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
class GetUserServiceTest {

  @Mock
  private UserRepositoryPort userRepository;

  private GetUserService service;

  @BeforeEach
  void setUp() {
    service = new GetUserService(userRepository);
  }

  @Test
  void getById_shouldReturnUser_whenRepositoryFindsMatch() {
    var userId = new UserId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    var user = restoredUser(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    var result = service.getById(userId);

    assertEquals(Optional.of(user), result);
    assertSame(user, result.orElseThrow());
    verify(userRepository).findById(userId);
  }

  @Test
  void getById_shouldReturnEmpty_whenRepositoryDoesNotFindUser() {
    var userId = new UserId(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    var result = service.getById(userId);

    assertEquals(Optional.empty(), result);
    verify(userRepository).findById(userId);
  }

  @Test
  void getById_shouldRejectNullId() {
    var ex = assertThrows(NullPointerException.class, () -> service.getById(null));

    assertEquals("id cannot be null", ex.getMessage());
  }

  private static User restoredUser(UserId id) {
    return User.restore(
        id,
        new Email("user.get@example.com"),
        new PasswordHash("hash"),
        true,
        true,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"),
        Set.of());
  }
}
