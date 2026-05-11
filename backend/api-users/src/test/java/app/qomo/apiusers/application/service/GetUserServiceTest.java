package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.exception.ForbiddenOperationException;
import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.UserNotFoundException;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.application.port.in.GetUserUseCase.DeletedFilter;
import app.qomo.apiusers.application.port.in.GetUserUseCase.SortBy;
import app.qomo.apiusers.application.port.in.GetUserUseCase.SortDirection;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.RoleId;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetUserServiceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-03-25T12:30:00Z");
  private static final Instant NOW = Instant.parse("2026-03-26T12:30:00Z");
  private static final UserId TARGET_ID = UserId.of("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

  @Mock private UserRepositoryPort userRepository;

  private GetUserService service;

  @BeforeEach
  void setUp() {
    ClockPort clock = () -> NOW;
    service = new GetUserService(userRepository, clock);
  }

  @Test
  void listForAdmin_shouldKeepCompatibleQueryConstructorWithoutSearch() {
    when(userRepository.findAllPage(
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(new GetUserUseCase.Query(0, 20), Set.of("SUPERADMIN"));

    verify(userRepository)
        .findAllPage(
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldTrimSearchForSuperAdmin() {
    when(userRepository.findAllPage(
            "foo",
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(new GetUserUseCase.Query(0, 20, "  foo  "), Set.of("SUPERADMIN"));

    verify(userRepository)
        .findAllPage(
            "foo",
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldTreatBlankSearchAsNoSearchForAdmin() {
    when(userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(new GetUserUseCase.Query(0, 20, "   "), Set.of("ADMIN"));

    verify(userRepository)
        .findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldConstrainSearchToAdminVisibleRoles() {
    when(userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            "foo",
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            1,
            10))
        .thenReturn(emptyPage(1, 10));

    service.listForAdmin(new GetUserUseCase.Query(1, 10, "foo"), Set.of("ADMIN"));

    verify(userRepository)
        .findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            "foo",
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            1,
            10);
  }

  @Test
  void listForAdmin_shouldPassFiltersForSuperAdmin() {
    when(userRepository.findAllPage(
            "foo",
            DeletedFilter.ONLY,
            false,
            true,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(
        new GetUserUseCase.Query(0, 20, "foo", DeletedFilter.ONLY, false, true),
        Set.of("SUPERADMIN"));

    verify(userRepository)
        .findAllPage(
            "foo",
            DeletedFilter.ONLY,
            false,
            true,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldKeepAdminVisibilityWhenFiltersRequestAllDeletedUsers() {
    when(userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.ALL,
            true,
            false,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(
        new GetUserUseCase.Query(0, 20, null, DeletedFilter.ALL, true, false), Set.of("ADMIN"));

    verify(userRepository)
        .findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.ALL,
            true,
            false,
            null,
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldPassRoleFilterForSuperAdmin() {
    when(userRepository.findAllPage(
            "foo",
            DeletedFilter.ONLY,
            false,
            true,
            "SUPERADMIN",
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(
        new GetUserUseCase.Query(0, 20, "foo", DeletedFilter.ONLY, false, true, "superadmin"),
        Set.of("SUPERADMIN"));

    verify(userRepository)
        .findAllPage(
            "foo",
            DeletedFilter.ONLY,
            false,
            true,
            "SUPERADMIN",
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldPassRoleFilterForAdminVisibleUsers() {
    when(userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            "USER",
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(
        new GetUserUseCase.Query(0, 20, null, DeletedFilter.EXCLUDE, null, null, "USER"),
        Set.of("ADMIN"));

    verify(userRepository)
        .findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            "USER",
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldKeepAdminVisibilityWhenRoleFilterRequestsSuperAdmin() {
    when(userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            "SUPERADMIN",
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    var page =
        service.listForAdmin(
            new GetUserUseCase.Query(0, 20, null, DeletedFilter.EXCLUDE, null, null, "SUPERADMIN"),
            Set.of("ADMIN"));

    assertEquals(0, page.totalElements());
    verify(userRepository)
        .findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            "SUPERADMIN",
            SortBy.CREATED_AT,
            SortDirection.DESC,
            0,
            20);
  }

  @Test
  void listForAdmin_shouldPassSortForSuperAdmin() {
    when(userRepository.findAllPage(
            null, DeletedFilter.EXCLUDE, null, null, null, SortBy.EMAIL, SortDirection.ASC, 0, 20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(
        new GetUserUseCase.Query(
            0, 20, null, DeletedFilter.EXCLUDE, null, null, null, SortBy.EMAIL, SortDirection.ASC),
        Set.of("SUPERADMIN"));

    verify(userRepository)
        .findAllPage(
            null, DeletedFilter.EXCLUDE, null, null, null, SortBy.EMAIL, SortDirection.ASC, 0, 20);
  }

  @Test
  void listForAdmin_shouldPassSortForAdminVisibleUsers() {
    when(userRepository.findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.UPDATED_AT,
            SortDirection.ASC,
            0,
            20))
        .thenReturn(emptyPage(0, 20));

    service.listForAdmin(
        new GetUserUseCase.Query(
            0,
            20,
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.UPDATED_AT,
            SortDirection.ASC),
        Set.of("ADMIN"));

    verify(userRepository)
        .findPageByVisibleRoles(
            Set.of("USER", "ADMIN"),
            null,
            DeletedFilter.EXCLUDE,
            null,
            null,
            null,
            SortBy.UPDATED_AT,
            SortDirection.ASC,
            0,
            20);
  }

  @Test
  void update_shouldAllowAdminToDeactivateUser() {
    User target = userWithRoles(Role.user(roleId("11111111-1111-4111-8111-111111111111")));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User updated = service.update(command(false, Set.of("ADMIN"), "actor-admin-id"));

    assertFalse(updated.isActive());
    assertEquals(NOW, updated.updatedAt());
    verify(userRepository).save(updated);
  }

  @Test
  void update_shouldRejectAdminEditingAdmin() {
    User target = userWithRoles(Role.admin(roleId("22222222-2222-4222-8222-222222222222")));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    var ex =
        assertThrows(
            ForbiddenOperationException.class,
            () -> service.update(command(false, Set.of("ADMIN"), "actor-admin-id")));

    assertEquals("FORBIDDEN_OPERATION", ex.code());
    verify(userRepository, never()).save(any());
  }

  @Test
  void update_shouldRejectAdminEditingSuperAdmin() {
    User target =
        userWithRoles(Role.of(roleId("33333333-3333-4333-8333-333333333333"), "SUPERADMIN"));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    assertThrows(
        ForbiddenOperationException.class,
        () -> service.update(command(false, Set.of("ADMIN"), "actor-admin-id")));

    verify(userRepository, never()).save(any());
  }

  @Test
  void update_shouldAllowSuperAdminToDeactivateUser() {
    User target = userWithRoles(Role.user(roleId("44444444-4444-4444-8444-444444444444")));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User updated = service.update(command(false, Set.of("SUPERADMIN"), "actor-superadmin-id"));

    assertFalse(updated.isActive());
    verify(userRepository).save(updated);
  }

  @Test
  void update_shouldAllowSuperAdminToDeactivateAdmin() {
    User target = userWithRoles(Role.admin(roleId("55555555-5555-4555-8555-555555555555")));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User updated = service.update(command(false, Set.of("SUPERADMIN"), "actor-superadmin-id"));

    assertFalse(updated.isActive());
    verify(userRepository).save(updated);
  }

  @Test
  void update_shouldRejectSuperAdminEditingSuperAdminInThisBlock() {
    User target =
        userWithRoles(Role.of(roleId("66666666-6666-4666-8666-666666666666"), "SUPERADMIN"));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    var ex =
        assertThrows(
            ForbiddenOperationException.class,
            () -> service.update(command(false, Set.of("SUPERADMIN"), "actor-superadmin-id")));

    assertEquals("SUPERADMIN users cannot be updated in this block", ex.params().get("reason"));
    verify(userRepository, never()).save(any());
  }

  @Test
  void update_shouldReturnNotFoundWhenTargetDoesNotExist() {
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

    assertThrows(
        UserNotFoundException.class,
        () -> service.update(command(false, Set.of("SUPERADMIN"), "actor-superadmin-id")));

    verify(userRepository, never()).save(any());
  }

  @Test
  void update_shouldRejectMissingEditableFields() {
    var command =
        new GetUserUseCase.UpdateCommand(TARGET_ID, null, Set.of("ADMIN"), "actor-admin-id");

    var ex = assertThrows(InvalidCommandException.class, () -> service.update(command));

    assertEquals("active", ex.params().get("field"));
    verify(userRepository, never()).findById(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void update_shouldRejectSelfDeactivationWhenActorMatchesTarget() {
    User target = userWithRoles(Role.user(roleId("77777777-7777-4777-8777-777777777777")));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

    assertThrows(
        ForbiddenOperationException.class,
        () -> service.update(command(false, Set.of("ADMIN"), TARGET_ID.toString())));

    verify(userRepository, never()).save(any());
  }

  @Test
  void update_shouldLeaveSameFinalStateWhenSameBodyIsSentTwice() {
    User target = userWithRoles(Role.user(roleId("88888888-8888-4888-8888-888888888888")));
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.update(command(false, Set.of("ADMIN"), "actor-admin-id"));
    service.update(command(false, Set.of("ADMIN"), "actor-admin-id"));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository, times(2)).save(userCaptor.capture());
    assertFalse(userCaptor.getAllValues().get(0).isActive());
    assertFalse(userCaptor.getAllValues().get(1).isActive());
  }

  private GetUserUseCase.UpdateCommand command(
      Boolean active, Set<String> actorRoles, String actorUserId) {
    return new GetUserUseCase.UpdateCommand(TARGET_ID, active, actorRoles, actorUserId);
  }

  private UserRepositoryPort.Page<User> emptyPage(int page, int size) {
    return new UserRepositoryPort.Page<>(List.of(), page, size, 0, 0);
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

  private RoleId roleId(String raw) {
    return new RoleId(UUID.fromString(raw));
  }
}
