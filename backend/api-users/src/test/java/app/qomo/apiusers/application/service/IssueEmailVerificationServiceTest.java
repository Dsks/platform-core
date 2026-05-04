package app.qomo.apiusers.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.qomo.apiusers.application.command.EmailVerificationRequestedCommand;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.OutboxEvent;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
import app.qomo.apiusers.domain.model.VerificationTokenId;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.OutboxRepositoryPort;
import app.qomo.apiusers.domain.port.out.VerificationTokenRepositoryPort;
import app.qomo.apiusers.infrastructure.util.OtpGenerator;
import app.qomo.apiusers.infrastructure.util.TokenHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueEmailVerificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-10T15:30:00Z");
  private static final Duration OTP_TTL = Duration.ofMinutes(15);
  private static final Duration RESEND_INTERVAL = Duration.ofMinutes(2);
  private static final UserId USER_ID =
      new UserId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
  private static final Email EMAIL = new Email("user@example.com");
  private static final String EMAIL_COMMANDS_TOPIC = "email-commands";

  @Mock private VerificationTokenRepositoryPort verificationTokenRepository;

  @Mock private OutboxRepositoryPort outboxRepository;

  @Mock private OtpGenerator otpGenerator;

  private ClockPort clock;
  private TokenHasher tokenHasher;
  private ObjectMapper objectMapper;
  private IssueEmailVerificationService service;

  @BeforeEach
  void setUp() {
    clock = () -> NOW;
    tokenHasher = new TokenHasher();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    service =
        new IssueEmailVerificationService(
            verificationTokenRepository,
            outboxRepository,
            clock,
            otpGenerator,
            tokenHasher,
            OTP_TTL,
            RESEND_INTERVAL,
            objectMapper,
            EMAIL_COMMANDS_TOPIC);
  }

  @Test
  void issue_shouldReturnNotIssuedAndSkipWrites_whenResendRateLimitApplies() {
    var activeToken = activeTokenWithLastSentAt(NOW.minusSeconds(30));
    when(verificationTokenRepository.findLatestActiveByUserId(
            eq(USER_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.of(activeToken));

    var result = service.issue(USER_ID, EMAIL, "login", "corr-1");

    assertNull(result.verificationSessionId());
    assertEquals(OTP_TTL.toSeconds(), result.ttlSeconds());
    assertFalse(result.issued());

    verify(verificationTokenRepository, never()).invalidateActiveTokens(any(), any(), any());
    verify(verificationTokenRepository, never()).saveNewToken(any());
    verify(outboxRepository, never()).insertPending(any());
  }

  @Test
  void issue_shouldInvalidatePreviousActiveCreateHashedTokenAndPersistOutbox_whenIssuanceAllowed()
      throws Exception {
    var oldActiveToken = activeTokenWithLastSentAt(NOW.minus(RESEND_INTERVAL).minusSeconds(1));
    when(verificationTokenRepository.findLatestActiveByUserId(
            eq(USER_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.of(oldActiveToken));
    when(otpGenerator.generate6Digits()).thenReturn("123456");

    var result = service.issue(USER_ID, EMAIL, "register", "corr-2");

    assertTrue(result.issued());
    assertNotNull(result.verificationSessionId());
    assertEquals(OTP_TTL.toSeconds(), result.ttlSeconds());

    var tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
    var outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);

    InOrder ordered = inOrder(verificationTokenRepository, outboxRepository);
    ordered
        .verify(verificationTokenRepository)
        .invalidateActiveTokens(
            eq(USER_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW));
    ordered.verify(verificationTokenRepository).saveNewToken(tokenCaptor.capture());
    ordered.verify(outboxRepository).insertPending(outboxCaptor.capture());

    VerificationToken savedToken = tokenCaptor.getValue();
    assertEquals(USER_ID, savedToken.userId());
    assertEquals(VerificationToken.Type.EMAIL_VERIFICATION, savedToken.type());
    assertEquals(result.verificationSessionId(), savedToken.sessionId());
    assertEquals(tokenHasher.sha256Hex("123456"), savedToken.tokenHash());
    assertEquals(NOW, savedToken.createdAt());
    assertEquals(NOW, savedToken.lastSentAt());
    assertEquals(NOW.plus(OTP_TTL), savedToken.expiresAt());
    assertEquals(0, savedToken.attempts());
    assertNull(savedToken.consumedAt());

    OutboxEvent outboxEvent = outboxCaptor.getValue();
    assertEquals("auth_user", outboxEvent.aggregateType());
    assertEquals(USER_ID.value(), outboxEvent.aggregateId());
    assertEquals(
        EmailVerificationRequestedCommand.EMAIL_VERIFICATION_REQUESTED, outboxEvent.eventType());
    assertEquals(EMAIL_COMMANDS_TOPIC, outboxEvent.topic());
    assertEquals(USER_ID.value().toString(), outboxEvent.key());
    assertEquals(0, outboxEvent.attempts());
    assertEquals(NOW, outboxEvent.createdAt());
    assertEquals(NOW, outboxEvent.updatedAt());

    JsonNode payload = objectMapper.readTree(outboxEvent.payloadJson());
    assertEquals(
        EmailVerificationRequestedCommand.EMAIL_VERIFICATION_REQUESTED,
        payload.get("type").asText());
    assertEquals(
        EmailVerificationRequestedCommand.EMAIL_VERIFICATION_TEMPLATE,
        payload.get("template").asText());
    assertEquals(USER_ID.value().toString(), payload.get("userId").asText());
    assertEquals(EMAIL.value(), payload.get("toEmail").asText());
    assertEquals("123456", payload.get("verificationCode").asText());
    assertEquals("corr-2", payload.get("correlationId").asText());
    assertTrue(payload.get("occurredAt").isNumber());
    assertEquals(NOW.getEpochSecond(), payload.get("occurredAt").asLong());
    assertFalse(payload.get("eventId").asText().isBlank());
  }

  @Test
  void issue_shouldNotRateLimit_whenLatestActiveTokenHasNullLastSentAt() {
    var activeWithoutLastSentAt = activeTokenWithLastSentAt(null);
    when(verificationTokenRepository.findLatestActiveByUserId(
            eq(USER_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.of(activeWithoutLastSentAt));
    when(otpGenerator.generate6Digits()).thenReturn("654321");

    var result = service.issue(USER_ID, EMAIL, "resend", "corr-3");

    assertTrue(result.issued());
    verify(verificationTokenRepository)
        .invalidateActiveTokens(
            eq(USER_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW));
    verify(verificationTokenRepository).saveNewToken(any());
    verify(outboxRepository).insertPending(any());
  }

  @Test
  void issue_shouldThrowAndSkipOutboxInsert_whenPayloadSerializationFails() throws Exception {
    var failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
    when(verificationTokenRepository.findLatestActiveByUserId(
            eq(USER_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW)))
        .thenReturn(Optional.empty());
    when(otpGenerator.generate6Digits()).thenReturn("123456");
    when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

    service =
        new IssueEmailVerificationService(
            verificationTokenRepository,
            outboxRepository,
            clock,
            otpGenerator,
            tokenHasher,
            OTP_TTL,
            RESEND_INTERVAL,
            failingMapper,
            EMAIL_COMMANDS_TOPIC);

    assertThrows(
        IllegalStateException.class, () -> service.issue(USER_ID, EMAIL, "register", "corr-4"));

    verify(verificationTokenRepository)
        .invalidateActiveTokens(
            eq(USER_ID), eq(VerificationToken.Type.EMAIL_VERIFICATION), eq(NOW));
    verify(verificationTokenRepository).saveNewToken(any());
    verify(outboxRepository, never()).insertPending(any());
  }

  private VerificationToken activeTokenWithLastSentAt(Instant lastSentAt) {
    return new VerificationToken(
        new VerificationTokenId(UUID.fromString("22222222-2222-4222-8222-222222222222")),
        USER_ID,
        tokenHasher.sha256Hex("111111"),
        VerificationToken.Type.EMAIL_VERIFICATION,
        UUID.fromString("33333333-3333-4333-8333-333333333333"),
        NOW.plusSeconds(300),
        NOW.minusSeconds(120),
        null,
        0,
        null,
        lastSentAt);
  }
}
