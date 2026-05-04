package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.exception.EmailAlreadyInUseException;
import app.qomo.apiusers.application.exception.ForbiddenOperationException;
import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.RoleNotFoundException;
import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.RoleId;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.CurrentUserPort;
import app.qomo.apiusers.domain.port.out.PasswordHasherPort;
import app.qomo.apiusers.domain.port.out.RoleRepositoryPort;
import app.qomo.apiusers.domain.port.out.UserEventPublisherPort;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-25T12:30:00Z");

  @Mock private UserRepositoryPort userRepository;

  @Mock private RoleRepositoryPort roleRepository;

  @Mock private PasswordHasherPort passwordHasher;

  @Mock private UserEventPublisherPort eventPublisher;

  @Mock private CurrentUserPort currentUser;

  private CreateUserService service;

  @BeforeEach
  void setUp() {
    ClockPort clock = () -> NOW;
    service =
        new CreateUserService(
            userRepository, roleRepository, passwordHasher, eventPublisher, clock, currentUser);
  }

  @Test
  void create_shouldPersistAndPublishEvent_whenActorCanCreateAdmin() {
    var command =
        new CreateUserUseCase.Command("Admin.Candidate@Example.com", "secret", Set.of("ADMIN"));
    var hash = new PasswordHash("hashed-password");
    var adminRole = Role.admin(new RoleId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")));

    when(userRepository.existsByEmail("admin.candidate@example.com")).thenReturn(false);
    when(currentUser.roles()).thenReturn(Set.of("superadmin"));
    when(passwordHasher.hash("secret")).thenReturn(hash);
    when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.create(command);

    assertNotNull(result.id());

    var userCaptor = ArgumentCaptor.forClass(User.class);
    InOrder ordered = inOrder(userRepository, currentUser, roleRepository, eventPublisher);
    ordered.verify(userRepository).existsByEmail("admin.candidate@example.com");
    ordered.verify(currentUser).roles();
    ordered.verify(roleRepository).findByName("ADMIN");
    ordered.verify(userRepository).save(userCaptor.capture());
    ordered.verify(eventPublisher).userCreated(userCaptor.getValue());

    var saved = userCaptor.getValue();
    assertEquals("admin.candidate@example.com", saved.email().value());
    assertEquals(hash, saved.passwordHash());
    assertEquals(NOW, saved.createdAt());
    assertEquals(Set.of(adminRole), saved.roles());
  }

  @Test
  void create_shouldNormalizeRolesAndRemoveDuplicates_whenCreationProceeds() {
    var command =
        new CreateUserUseCase.Command(
            "user@example.com",
            "pwd",
            new LinkedHashSet<>(java.util.Arrays.asList(" user ", "USER", " ", null)));
    var hash = new PasswordHash("hash-1");
    var userRole = Role.user(new RoleId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")));

    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(passwordHasher.hash("pwd")).thenReturn(hash);
    when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(command);

    verify(roleRepository).findByName("USER");
    verify(roleRepository, never()).findByName(" user ");
    verify(currentUser, never()).roles();

    var userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertEquals(Set.of(userRole), userCaptor.getValue().roles());
  }

  @Test
  void create_shouldUseDefaultUserRole_whenRolesAreNullOrBlankOnly() {
    var nullRolesCommand = new CreateUserUseCase.Command("one@example.com", "pwd", null);
    var blankRolesCommand =
        new CreateUserUseCase.Command(
            "two@example.com",
            "pwd",
            new LinkedHashSet<>(java.util.Arrays.asList(" ", "\t", null)));
    var hash = new PasswordHash("hash-default");
    var userRole = Role.user(new RoleId(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc")));

    when(userRepository.existsByEmail(any())).thenReturn(false);
    when(passwordHasher.hash("pwd")).thenReturn(hash);
    when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(nullRolesCommand);
    service.create(blankRolesCommand);

    verify(roleRepository, times(2)).findByName("USER");
    verify(userRepository, times(2)).save(any(User.class));
    verify(eventPublisher, times(2)).userCreated(any(User.class));
  }

  @Test
  void create_shouldRejectAdminCreation_whenActorIsNotSuperAdmin() {
    var command = new CreateUserUseCase.Command("candidate@example.com", "pwd", Set.of("ADMIN"));

    when(userRepository.existsByEmail("candidate@example.com")).thenReturn(false);
    when(currentUser.roles()).thenReturn(Set.of("ADMIN"));

    var ex = assertThrows(ForbiddenOperationException.class, () -> service.create(command));

    assertEquals("FORBIDDEN_OPERATION", ex.code());
    assertEquals("Only SUPERADMIN can create ADMIN users", ex.params().get("reason"));
    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).userCreated(any());
  }

  @Test
  void create_shouldRejectTargetRolesOutsideAllowedAdministrativeSet() {
    var command =
        new CreateUserUseCase.Command("candidate@example.com", "pwd", Set.of("SUPERADMIN"));

    when(userRepository.existsByEmail("candidate@example.com")).thenReturn(false);

    var ex = assertThrows(ForbiddenOperationException.class, () -> service.create(command));

    assertEquals("FORBIDDEN_OPERATION", ex.code());
    assertEquals(
        "Target role is not allowed for administrative creation", ex.params().get("reason"));
    verify(currentUser, never()).roles();
    verify(passwordHasher, never()).hash(any());
    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).userCreated(any());
  }

  @Test
  void create_shouldFailBeforePersistence_whenRoleLookupDoesNotFindNormalizedRole() {
    var command = new CreateUserUseCase.Command("person@example.com", "pwd", Set.of(" admin "));

    when(userRepository.existsByEmail("person@example.com")).thenReturn(false);
    when(currentUser.roles()).thenReturn(Set.of("SUPERADMIN"));
    when(passwordHasher.hash("pwd")).thenReturn(new PasswordHash("hash-2"));
    when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

    var ex = assertThrows(RoleNotFoundException.class, () -> service.create(command));

    assertEquals("ROLE_NOT_FOUND", ex.code());
    assertEquals("ADMIN", ex.params().get("roleName"));
    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).userCreated(any());
  }

  @Test
  void create_shouldRejectCreation_whenEmailAlreadyExists() {
    var command = new CreateUserUseCase.Command("existing@example.com", "pwd", Set.of("USER"));

    when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

    var ex = assertThrows(EmailAlreadyInUseException.class, () -> service.create(command));

    assertEquals("USER_EMAIL_ALREADY_IN_USE", ex.code());
    verify(currentUser, never()).roles();
    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).userCreated(any());
  }

  @ParameterizedTest
  @MethodSource("invalidCommands")
  void create_shouldRejectInvalidCommands_andSkipSideEffects(
      CreateUserUseCase.Command command, String expectedField) {

    var ex = assertThrows(InvalidCommandException.class, () -> service.create(command));

    assertEquals("INVALID_COMMAND", ex.code());
    assertEquals(expectedField, ex.params().get("field"));

    verify(userRepository, never()).existsByEmail(any());
    verify(currentUser, never()).roles();
    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).userCreated(any());
  }

  private static java.util.stream.Stream<Arguments> invalidCommands() {
    return java.util.stream.Stream.of(
        Arguments.of(null, "command"),
        Arguments.of(new CreateUserUseCase.Command(null, "pwd", Set.of("USER")), "email"),
        Arguments.of(
            new CreateUserUseCase.Command("person@example.com", null, Set.of("USER")),
            "rawPassword"));
  }
}
