package app.platformcore.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.platformcore.apiusers.application.exception.ForbiddenOperationException;
import app.platformcore.apiusers.application.port.in.DeleteUserUseCase;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.UserRepositoryPort;
import app.platformcore.apiusers.domain.constant.SystemRoleIds;
import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.PasswordHash;
import app.platformcore.apiusers.domain.model.Role;
import app.platformcore.apiusers.domain.model.User;
import app.platformcore.apiusers.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteUserServiceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-03-25T12:30:00Z");
  private static final Instant NOW = Instant.parse("2026-03-26T12:30:00Z");
  private static final UserId TARGET_ID = UserId.of("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
  private static final UserId ACTOR_ID = UserId.of("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

  @Mock private UserRepositoryPort userRepository;

  private DeleteUserService service;

  @BeforeEach
  void setUp() {
    ClockPort clock = () -> NOW;
    service = new DeleteUserService(userRepository, clock);
  }

  @Test
  void delete_shouldAllowAdminToDeleteUser() {
    User target = userWithRoles(Role.user(SystemRoleIds.USER));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    service.delete(command(Set.of("ADMIN"), ACTOR_ID));

    verify(userRepository)
        .softDeleteAndAnonymize(
            eq(TARGET_ID),
            eq(Email.of("deleted-" + TARGET_ID + "@deleted.app")),
            eq(new PasswordHash("deleted:" + TARGET_ID)),
            eq(NOW));
  }

  @Test
  void delete_shouldRejectAdminDeletingAdmin() {
    User target = userWithRoles(Role.admin(SystemRoleIds.ADMIN));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    var ex =
        assertThrows(
            ForbiddenOperationException.class,
            () -> service.delete(command(Set.of("ADMIN"), ACTOR_ID)));

    assertEquals("FORBIDDEN_OPERATION", ex.code());
    verify(userRepository, never()).softDeleteAndAnonymize(any(), any(), any(), any());
  }

  @Test
  void delete_shouldAllowSuperAdminToDeleteAdmin() {
    User target = userWithRoles(Role.admin(SystemRoleIds.ADMIN));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    service.delete(command(Set.of("SUPERADMIN"), ACTOR_ID));

    verify(userRepository)
        .softDeleteAndAnonymize(
            eq(TARGET_ID),
            eq(Email.of("deleted-" + TARGET_ID + "@deleted.app")),
            eq(new PasswordHash("deleted:" + TARGET_ID)),
            eq(NOW));
  }

  @Test
  void delete_shouldRejectSuperAdminDeletingSuperAdmin() {
    User target = userWithRoles(Role.of(SystemRoleIds.SUPERADMIN, "SUPERADMIN"));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    var ex =
        assertThrows(
            ForbiddenOperationException.class,
            () -> service.delete(command(Set.of("SUPERADMIN"), ACTOR_ID)));

    assertEquals("FORBIDDEN_OPERATION", ex.code());
    verify(userRepository, never()).softDeleteAndAnonymize(any(), any(), any(), any());
  }

  @Test
  void delete_shouldRejectSelfDelete() {
    User target = userWithRoles(Role.user(SystemRoleIds.USER));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    var ex =
        assertThrows(
            ForbiddenOperationException.class,
            () -> service.delete(command(Set.of("ADMIN"), TARGET_ID)));

    assertEquals("FORBIDDEN_OPERATION", ex.code());
    verify(userRepository, never()).softDeleteAndAnonymize(any(), any(), any(), any());
  }

  private DeleteUserUseCase.Command command(Set<String> actorRoles, UserId actorUserId) {
    return new DeleteUserUseCase.Command(TARGET_ID, actorUserId, actorRoles);
  }

  private User userWithRoles(Role... roles) {
    return User.restore(
        TARGET_ID,
        Email.of("target@example.com"),
        new PasswordHash("hashed-password"),
        true,
        true,
        null,
        CREATED_AT,
        CREATED_AT,
        null,
        Set.of(roles));
  }
}
