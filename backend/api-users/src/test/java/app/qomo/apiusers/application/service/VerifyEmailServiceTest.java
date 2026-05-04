package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.port.in.VerifyEmailUseCase;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
import app.qomo.apiusers.domain.model.VerificationTokenId;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.port.out.VerificationTokenRepositoryPort;
import app.qomo.apiusers.infrastructure.util.TokenHasher;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyEmailServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-01T10:15:30Z");
  private static final UUID SESSION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID TOKEN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  @Mock private VerificationTokenRepositoryPort verificationTokenRepository;

  @Mock private UserRepositoryPort userRepository;

  private VerifyEmailService service;
  private TokenHasher tokenHasher;

  @BeforeEach
  void setUp() {
    ClockPort fixedClock = () -> NOW;
    tokenHasher = new TokenHasher();
    service =
        new VerifyEmailService(
            verificationTokenRepository, userRepository, fixedClock, tokenHasher, 5);
  }

  @Test
  void verify_shouldSucceedAndConsumeToken_whenCodeIsValid() {
    var command = new VerifyEmailUseCase.Command(SESSION_ID, "123456");
    var token = activeTokenWithCode("123456", 0);

    when(verificationTokenRepository.findActiveBySessionAndType(
            eq(SESSION_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.of(token));

    boolean result = service.verify(command);

    assertTrue(result);
    verify(userRepository).setVerified(eq(token.userId()), eq(NOW));
    verify(verificationTokenRepository).markConsumed(eq(token.id().value()), eq(NOW));
    verify(verificationTokenRepository, never()).incrementAttempts(any(), any());
  }

  @ParameterizedTest
  @MethodSource("invalidCommands")
  void verify_shouldReturnFalseAndSkipSideEffects_whenCommandIsInvalid(
      VerifyEmailUseCase.Command command) {

    boolean result = service.verify(command);

    assertFalse(result);
    verify(verificationTokenRepository, never()).findActiveBySessionAndType(any(), any(), any());
    verify(userRepository, never()).setVerified(any(), any());
    verify(verificationTokenRepository, never()).markConsumed(any(), any());
    verify(verificationTokenRepository, never()).incrementAttempts(any(), any());
  }

  static VerifyEmailUseCase.Command[] invalidCommands() {
    return new VerifyEmailUseCase.Command[] {
      null,
      new VerifyEmailUseCase.Command(null, "123456"),
      new VerifyEmailUseCase.Command(SESSION_ID, null),
      new VerifyEmailUseCase.Command(SESSION_ID, "12345"),
      new VerifyEmailUseCase.Command(SESSION_ID, "1234567"),
      new VerifyEmailUseCase.Command(SESSION_ID, "12ab56"),
      new VerifyEmailUseCase.Command(SESSION_ID, "      ")
    };
  }

  @Test
  void verify_shouldReturnFalseAndSkipSideEffects_whenNoActiveTokenExists() {
    when(verificationTokenRepository.findActiveBySessionAndType(
            eq(SESSION_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.empty());

    boolean result = service.verify(new VerifyEmailUseCase.Command(SESSION_ID, "123456"));

    assertFalse(result);
    verify(userRepository, never()).setVerified(any(), any());
    verify(verificationTokenRepository, never()).markConsumed(any(), any());
    verify(verificationTokenRepository, never()).incrementAttempts(any(), any());
  }

  @Test
  void verify_shouldIncrementAttemptsAndReturnFalse_whenMaxAttemptsReached() {
    service =
        new VerifyEmailService(
            verificationTokenRepository, userRepository, () -> NOW, tokenHasher, 3);

    var token = activeTokenWithCode("123456", 3);

    when(verificationTokenRepository.findActiveBySessionAndType(
            eq(SESSION_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.of(token));

    boolean result = service.verify(new VerifyEmailUseCase.Command(SESSION_ID, "123456"));

    assertFalse(result);
    verify(verificationTokenRepository).incrementAttempts(eq(TOKEN_ID), eq(NOW));
    verify(userRepository, never()).setVerified(any(), any());
    verify(verificationTokenRepository, never()).markConsumed(any(), any());
  }

  @Test
  void verify_shouldIncrementAttemptsAndReturnFalse_whenCodeDoesNotMatch() {
    var token = activeTokenWithCode("654321", 1);

    when(verificationTokenRepository.findActiveBySessionAndType(
            eq(SESSION_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.of(token));

    boolean result = service.verify(new VerifyEmailUseCase.Command(SESSION_ID, "123456"));

    assertFalse(result);
    verify(verificationTokenRepository).incrementAttempts(eq(TOKEN_ID), eq(NOW));
    verify(userRepository, never()).setVerified(any(), any());
    verify(verificationTokenRepository, never()).markConsumed(any(), any());
  }

  @Test
  void verify_shouldIgnoreAttemptLimit_whenMaxAttemptsIsZero() {
    service =
        new VerifyEmailService(
            verificationTokenRepository, userRepository, () -> NOW, tokenHasher, 0);

    var token = activeTokenWithCode("123456", 99);

    when(verificationTokenRepository.findActiveBySessionAndType(
            eq(SESSION_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.of(token));

    boolean result = service.verify(new VerifyEmailUseCase.Command(SESSION_ID, "123456"));

    assertTrue(result);
    verify(userRepository).setVerified(eq(new UserId(USER_ID)), eq(NOW));
    verify(verificationTokenRepository).markConsumed(eq(TOKEN_ID), eq(NOW));
    verify(verificationTokenRepository, never()).incrementAttempts(any(), any());
  }

  private VerificationToken activeTokenWithCode(String code, int attempts) {
    return new VerificationToken(
        new VerificationTokenId(TOKEN_ID),
        new UserId(USER_ID),
        tokenHasher.sha256Hex(code),
        VerificationToken.Type.EMAIL_VERIFICATION,
        SESSION_ID,
        NOW.plusSeconds(300),
        NOW.minusSeconds(120),
        null,
        attempts,
        null,
        NOW.minusSeconds(10));
  }
}
