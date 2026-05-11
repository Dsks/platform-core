package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.RoleNotFoundException;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase.RegistrationStatus;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.PasswordHasherPort;
import app.qomo.apiusers.application.port.out.RoleRepositoryPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.RoleId;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.time.Instant;
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
class RegisterUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-20T08:00:00Z");

  @Mock private UserRepositoryPort userRepository;

  @Mock private RoleRepositoryPort roleRepository;

  @Mock private PasswordHasherPort passwordHasher;

  @Mock private IssueEmailVerificationService issueEmailVerificationService;

  private RegisterUserService service;

  @BeforeEach
  void setUp() {
    ClockPort clock = () -> NOW;
    service =
        new RegisterUserService(
            userRepository, roleRepository, passwordHasher, clock, issueEmailVerificationService);
  }

  @Test
  void register_shouldCreateUserAssignDefaultRoleAndIssueVerification_whenEmailDoesNotExist() {
    var command = new RegisterUserUseCase.Command("New.User@Example.com", "s3cr3t");
    var userRole = Role.user(new RoleId(UUID.fromString("99999999-9999-4999-8999-999999999999")));
    var hash = new PasswordHash("hashed-value");
    var issuedSessionId = UUID.fromString("77777777-7777-4777-8777-777777777777");

    when(userRepository.findByEmail(eq("new.user@example.com"))).thenReturn(Optional.empty());
    when(passwordHasher.hash("s3cr3t")).thenReturn(hash);
    when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
    when(issueEmailVerificationService.issue(any(), any(), any(), any()))
        .thenReturn(new IssueEmailVerificationService.IssueResult(issuedSessionId, 900, true));

    var result = service.register(command);

    assertNotNull(result.requestId());
    assertEquals(RegistrationStatus.VERIFICATION_REQUIRED, result.status());
    assertEquals(issuedSessionId, result.verificationSessionId());
    assertEquals(900, result.verificationTtlSeconds());

    var userCaptor = ArgumentCaptor.forClass(User.class);
    var requestIdCaptor = ArgumentCaptor.forClass(String.class);

    InOrder ordered = inOrder(userRepository, roleRepository, issueEmailVerificationService);
    ordered.verify(userRepository).findByEmail("new.user@example.com");
    ordered.verify(roleRepository).findByName("USER");
    ordered.verify(userRepository).save(userCaptor.capture());
    ordered
        .verify(issueEmailVerificationService)
        .issue(
            eq(userCaptor.getValue().id()),
            eq(new Email("new.user@example.com")),
            eq("REGISTER_NEW"),
            requestIdCaptor.capture());

    var savedUser = userCaptor.getValue();
    assertEquals("new.user@example.com", savedUser.email().value());
    assertEquals(hash, savedUser.passwordHash());
    assertEquals(NOW, savedUser.createdAt());
    assertEquals(NOW, savedUser.updatedAt());
    assertEquals(Set.of(userRole), savedUser.roles());

    assertEquals(result.requestId(), requestIdCaptor.getValue());
  }

  @Test
  void register_shouldReturnAlreadyRegisteredAndSkipWrites_whenExistingUserIsVerified() {
    var verifiedUser = existingUser(true);
    var command = new RegisterUserUseCase.Command("existing@example.com", "irrelevant");

    when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(verifiedUser));

    var result = service.register(command);

    assertNotNull(result.requestId());
    assertEquals(RegistrationStatus.ALREADY_REGISTERED, result.status());
    assertNull(result.verificationSessionId());
    assertEquals(0, result.verificationTtlSeconds());

    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  @Test
  void register_shouldReissueVerificationForExistingUnverifiedUser_withoutCreatingNewUser() {
    var unverifiedUser = existingUser(false);
    var command = new RegisterUserUseCase.Command("existing@example.com", "still-irrelevant");
    var issuedSessionId = UUID.fromString("55555555-5555-4555-8555-555555555555");

    when(userRepository.findByEmail("existing@example.com"))
        .thenReturn(Optional.of(unverifiedUser));
    when(issueEmailVerificationService.issue(
            eq(unverifiedUser.id()),
            eq(new Email("existing@example.com")),
            eq("REGISTER_EXISTING_UNVERIFIED"),
            any()))
        .thenReturn(new IssueEmailVerificationService.IssueResult(issuedSessionId, 300, true));

    var result = service.register(command);

    assertNotNull(result.requestId());
    assertEquals(RegistrationStatus.VERIFICATION_REQUIRED, result.status());
    assertEquals(issuedSessionId, result.verificationSessionId());
    assertEquals(300, result.verificationTtlSeconds());

    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
    verify(issueEmailVerificationService)
        .issue(
            eq(unverifiedUser.id()),
            eq(new Email("existing@example.com")),
            eq("REGISTER_EXISTING_UNVERIFIED"),
            eq(result.requestId()));
  }

  @Test
  void register_shouldThrowRoleNotFound_whenDefaultRoleDoesNotExist() {
    var command = new RegisterUserUseCase.Command("new@example.com", "s3cr3t");

    when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(passwordHasher.hash("s3cr3t")).thenReturn(new PasswordHash("hashed-value"));
    when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

    var ex = assertThrows(RoleNotFoundException.class, () -> service.register(command));

    assertEquals("ROLE_NOT_FOUND", ex.code());
    assertEquals("USER", ex.params().get("roleName"));
    verify(userRepository, never()).save(any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  @ParameterizedTest
  @MethodSource("invalidCommands")
  void register_shouldRejectInvalidCommands_andSkipAllSideEffects(
      RegisterUserUseCase.Command command, String expectedField, String expectedReason) {

    var ex = assertThrows(InvalidCommandException.class, () -> service.register(command));

    assertEquals("INVALID_COMMAND", ex.code());
    assertEquals(expectedField, ex.params().get("field"));
    assertEquals(expectedReason, ex.params().get("reason"));

    verify(userRepository, never()).findByEmail(any());
    verify(passwordHasher, never()).hash(any());
    verify(roleRepository, never()).findByName(any());
    verify(userRepository, never()).save(any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  private static java.util.stream.Stream<Arguments> invalidCommands() {
    return java.util.stream.Stream.of(
        Arguments.of(null, "command", "missing"),
        Arguments.of(new RegisterUserUseCase.Command(null, "secret"), "email", "missing"),
        Arguments.of(
            new RegisterUserUseCase.Command("user@example.com", null), "rawPassword", "missing"),
        Arguments.of(
            new RegisterUserUseCase.Command("user@example.com", "   "), "rawPassword", "blank"),
        Arguments.of(
            new RegisterUserUseCase.Command("invalid-email", "secret"), "email", "invalid"));
  }

  private static User existingUser(boolean verified) {
    return User.restore(
        new UserId(UUID.fromString("11111111-1111-4111-8111-111111111111")),
        new Email("existing@example.com"),
        new PasswordHash("existing-hash"),
        true,
        verified,
        null,
        Instant.parse("2026-03-01T00:00:00Z"),
        Instant.parse("2026-03-10T00:00:00Z"),
        null,
        Set.of(Role.user(new RoleId(UUID.fromString("22222222-2222-4222-8222-222222222222")))));
  }
}
