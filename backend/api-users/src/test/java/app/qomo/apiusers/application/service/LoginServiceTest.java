package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.InvalidCredentialsException;
import app.qomo.apiusers.application.exception.UserInactiveException;
import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.PasswordVerifierPort;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-24T09:00:00Z");
  private static final String RAW_EMAIL = "User.Login@Example.com";
  private static final String NORMALIZED_EMAIL = "user.login@example.com";
  private static final String RAW_PASSWORD = "plain-secret";

  @Mock private UserRepositoryPort userRepository;

  @Mock private PasswordVerifierPort passwordVerifier;

  @Mock private IssueEmailVerificationService issueEmailVerificationService;

  private LoginService service;

  @BeforeEach
  void setUp() {
    ClockPort clock = () -> NOW;
    service =
        new LoginService(userRepository, passwordVerifier, clock, issueEmailVerificationService);
  }

  @ParameterizedTest
  @MethodSource("invalidCommands")
  void login_shouldRejectInvalidCommands_andSkipAllSideEffects(
      LoginUseCase.Command command, String expectedField, String expectedReason) {

    var ex = assertThrows(InvalidCommandException.class, () -> service.login(command));

    assertEquals("INVALID_COMMAND", ex.code());
    assertEquals(expectedField, ex.params().get("field"));
    assertEquals(expectedReason, ex.params().get("reason"));

    verify(userRepository, never()).findByEmail(any());
    verify(passwordVerifier, never()).matches(any(), any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void login_shouldThrowInvalidCredentials_whenUserDoesNotExist() {
    var command = new LoginUseCase.Command(RAW_EMAIL, RAW_PASSWORD);
    when(userRepository.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

    var ex = assertThrows(InvalidCredentialsException.class, () -> service.login(command));

    assertEquals("INVALID_CREDENTIALS", ex.code());
    verify(passwordVerifier, never()).matches(any(), any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void login_shouldThrowInvalidCredentials_whenPasswordDoesNotMatch() {
    var user = restoredUser(true, true, null, Instant.parse("2026-03-01T00:00:00Z"), null);
    var command = new LoginUseCase.Command(RAW_EMAIL, RAW_PASSWORD);
    when(userRepository.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(user));
    when(passwordVerifier.matches(RAW_PASSWORD, user.passwordHash())).thenReturn(false);

    var ex = assertThrows(InvalidCredentialsException.class, () -> service.login(command));

    assertEquals("INVALID_CREDENTIALS", ex.code());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void login_shouldThrowUserInactive_whenCredentialsAreValidButAccountIsInactive() {
    var inactiveUser = restoredUser(false, true, null, Instant.parse("2026-03-15T00:00:00Z"), null);
    var command = new LoginUseCase.Command(RAW_EMAIL, RAW_PASSWORD);
    when(userRepository.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(inactiveUser));
    when(passwordVerifier.matches(RAW_PASSWORD, inactiveUser.passwordHash())).thenReturn(true);

    var ex = assertThrows(UserInactiveException.class, () -> service.login(command));

    assertEquals("USER_INACTIVE", ex.code());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
    verify(userRepository, never()).save(any());
    assertNull(inactiveUser.lastLogin());
  }

  @Test
  void login_shouldTriggerVerificationFlowAndSkipLastLoginUpdate_whenUserIsUnverified() {
    var unverifiedUser =
        restoredUser(true, false, null, Instant.parse("2026-03-05T00:00:00Z"), null);
    var issuedSessionId = UUID.fromString("12345678-1234-4234-8234-123456789abc");
    var command = new LoginUseCase.Command(RAW_EMAIL, RAW_PASSWORD);

    when(userRepository.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(unverifiedUser));
    when(passwordVerifier.matches(RAW_PASSWORD, unverifiedUser.passwordHash())).thenReturn(true);
    when(issueEmailVerificationService.issue(
            eq(unverifiedUser.id()), eq(unverifiedUser.email()), eq("LOGIN_UNVERIFIED"), any()))
        .thenReturn(new IssueEmailVerificationService.IssueResult(issuedSessionId, 900, true));

    var result = service.login(command);

    assertNull(result.user());
    assertTrue(result.emailNotVerified());
    assertEquals(issuedSessionId, result.verificationSessionId());
    assertEquals(900, result.verificationTtlSeconds());

    var correlationCaptor = ArgumentCaptor.forClass(String.class);
    verify(issueEmailVerificationService)
        .issue(
            eq(unverifiedUser.id()),
            eq(unverifiedUser.email()),
            eq("LOGIN_UNVERIFIED"),
            correlationCaptor.capture());
    assertNotNull(correlationCaptor.getValue());
    verify(userRepository, never()).save(any());
    assertNull(unverifiedUser.lastLogin());
  }

  @Test
  void login_shouldReturnVerifiedUserAndUpdateLastLogin_whenCredentialsAreValidAndUserVerified() {
    var previousLogin = Instant.parse("2026-03-10T12:30:00Z");
    var verifiedUser =
        restoredUser(true, true, previousLogin, Instant.parse("2026-03-20T00:00:00Z"), null);
    var command = new LoginUseCase.Command(RAW_EMAIL, RAW_PASSWORD);

    when(userRepository.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(verifiedUser));
    when(passwordVerifier.matches(RAW_PASSWORD, verifiedUser.passwordHash())).thenReturn(true);
    when(userRepository.save(verifiedUser)).thenReturn(verifiedUser);

    var result = service.login(command);

    assertSame(verifiedUser, result.user());
    assertFalse(result.emailNotVerified());
    assertNull(result.verificationSessionId());
    assertEquals(0, result.verificationTtlSeconds());

    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
    verify(userRepository).save(verifiedUser);
    assertEquals(NOW, verifiedUser.lastLogin());
    assertEquals(NOW, verifiedUser.updatedAt());
  }

  @Test
  void login_shouldPropagateRateLimitedVerificationSemantics_whenIssueServiceDoesNotIssue() {
    var unverifiedUser =
        restoredUser(true, false, null, Instant.parse("2026-03-05T00:00:00Z"), null);
    var command = new LoginUseCase.Command(RAW_EMAIL, RAW_PASSWORD);

    when(userRepository.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(unverifiedUser));
    when(passwordVerifier.matches(RAW_PASSWORD, unverifiedUser.passwordHash())).thenReturn(true);
    when(issueEmailVerificationService.issue(any(), any(), any(), any()))
        .thenReturn(new IssueEmailVerificationService.IssueResult(null, 120, false));

    var result = service.login(command);

    assertNull(result.user());
    assertTrue(result.emailNotVerified());
    assertNull(result.verificationSessionId());
    assertEquals(120, result.verificationTtlSeconds());
    verify(userRepository, never()).save(any());
  }

  private static java.util.stream.Stream<Arguments> invalidCommands() {
    return java.util.stream.Stream.of(
        Arguments.of(null, "command", "missing"),
        Arguments.of(new LoginUseCase.Command(null, RAW_PASSWORD), "email", "missing"),
        Arguments.of(new LoginUseCase.Command(RAW_EMAIL, null), "password", "missing"));
  }

  private static User restoredUser(
      boolean active, boolean verified, Instant lastLogin, Instant updatedAt, Instant deletedAt) {
    return User.restore(
        new UserId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")),
        new Email(NORMALIZED_EMAIL),
        new PasswordHash("hashed-pass"),
        active,
        verified,
        lastLogin,
        Instant.parse("2026-02-01T00:00:00Z"),
        updatedAt,
        deletedAt,
        Set.of(Role.user(new RoleId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")))));
  }
}
