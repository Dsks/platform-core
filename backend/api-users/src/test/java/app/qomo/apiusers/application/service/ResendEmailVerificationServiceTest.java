package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.PasswordHash;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.time.Instant;
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
class ResendEmailVerificationServiceTest {

  @Mock private UserRepositoryPort userRepository;

  @Mock private IssueEmailVerificationService issueEmailVerificationService;

  private ResendEmailVerificationService service;

  @BeforeEach
  void setUp() {
    service = new ResendEmailVerificationService(userRepository, issueEmailVerificationService);
  }

  @Test
  void resend_shouldRejectNullCommand_andSkipDependencies() {
    var ex = assertThrows(InvalidCommandException.class, () -> service.resend(null));

    assertEquals("INVALID_COMMAND", ex.code());
    assertEquals("email", ex.params().get("field"));
    assertEquals("missing", ex.params().get("reason"));
    verify(userRepository, never()).findByEmail(any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  @Test
  void resend_shouldRejectCommandWithoutEmail_andSkipDependencies() {
    var command = new ResendEmailVerificationUseCase.Command(null);

    var ex = assertThrows(InvalidCommandException.class, () -> service.resend(command));

    assertEquals("INVALID_COMMAND", ex.code());
    assertEquals("email", ex.params().get("field"));
    assertEquals("missing", ex.params().get("reason"));
    verify(userRepository, never()).findByEmail(any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  @Test
  void resend_shouldReturnNoop_whenEmailFormatIsInvalid() {
    var command = new ResendEmailVerificationUseCase.Command("not-an-email");

    var result = service.resend(command);

    assertNull(result.verificationSessionId());
    assertEquals(0, result.verificationTtlSeconds());
    verify(userRepository, never()).findByEmail(any());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  @Test
  void resend_shouldReturnNoop_whenUserDoesNotExist() {
    var command = new ResendEmailVerificationUseCase.Command("Missing.User@Example.com");
    when(userRepository.findByEmail("missing.user@example.com")).thenReturn(Optional.empty());

    var result = service.resend(command);

    assertNull(result.verificationSessionId());
    assertEquals(0, result.verificationTtlSeconds());
    verify(userRepository).findByEmail("missing.user@example.com");
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  @Test
  void resend_shouldReturnNoop_whenUserIsAlreadyVerified() {
    var command = new ResendEmailVerificationUseCase.Command("verified@example.com");
    var verifiedUser = restoredUser(true);
    when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verifiedUser));

    var result = service.resend(command);

    assertNull(result.verificationSessionId());
    assertEquals(0, result.verificationTtlSeconds());
    verify(issueEmailVerificationService, never()).issue(any(), any(), any(), any());
  }

  @Test
  void resend_shouldInvokeIssueServiceAndReturnIssuedPayload_whenUserIsUnverified() {
    var command = new ResendEmailVerificationUseCase.Command("Not.Verified@Example.com");
    var unverifiedUser = restoredUser(false);
    var sessionId = UUID.fromString("12345678-1234-4234-8234-123456789abc");

    when(userRepository.findByEmail("not.verified@example.com"))
        .thenReturn(Optional.of(unverifiedUser));
    when(issueEmailVerificationService.issue(
            eq(unverifiedUser.id()),
            eq(new Email("not.verified@example.com")),
            eq("RESEND_ENDPOINT"),
            any()))
        .thenReturn(new IssueEmailVerificationService.IssueResult(sessionId, 600, true));

    var result = service.resend(command);

    assertEquals(sessionId, result.verificationSessionId());
    assertEquals(600, result.verificationTtlSeconds());

    var correlationCaptor = ArgumentCaptor.forClass(String.class);
    verify(issueEmailVerificationService)
        .issue(
            eq(unverifiedUser.id()),
            eq(new Email("not.verified@example.com")),
            eq("RESEND_ENDPOINT"),
            correlationCaptor.capture());
    assertEquals(36, correlationCaptor.getValue().length());
  }

  private static User restoredUser(boolean verified) {
    return User.restore(
        new UserId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")),
        new Email("not.verified@example.com"),
        new PasswordHash("hash"),
        true,
        verified,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"),
        Set.of());
  }
}
