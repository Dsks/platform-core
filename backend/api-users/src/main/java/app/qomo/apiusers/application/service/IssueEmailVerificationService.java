package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.command.EmailVerificationRequestedCommand;
import app.qomo.apiusers.application.observability.PiiUtil;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.OutboxEvent;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.OutboxRepositoryPort;
import app.qomo.apiusers.domain.port.out.VerificationTokenRepositoryPort;
import app.qomo.apiusers.infrastructure.util.OtpGenerator;
import app.qomo.apiusers.infrastructure.util.TokenHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class IssueEmailVerificationService {

  private static final Logger log = LoggerFactory.getLogger(IssueEmailVerificationService.class);
  private static final String AGGREGATE_TYPE = "auth_user";

  private final VerificationTokenRepositoryPort verificationTokenRepository;
  private final OutboxRepositoryPort outboxRepository;
  private final ClockPort clock;
  private final OtpGenerator otpGenerator;
  private final TokenHasher tokenHasher;
  private final Duration emailOtpTtl;
  private final Duration resendMinInterval;
  private final ObjectMapper objectMapper;
  private final String emailCommandsTopic;

  public IssueEmailVerificationService(
      VerificationTokenRepositoryPort verificationTokenRepository,
      OutboxRepositoryPort outboxRepository,
      ClockPort clock,
      OtpGenerator otpGenerator,
      TokenHasher tokenHasher,
      Duration emailOtpTtl,
      Duration resendMinInterval,
      ObjectMapper objectMapper,
      String emailCommandsTopic) {
    this.verificationTokenRepository =
        Objects.requireNonNull(verificationTokenRepository,
            "verificationTokenRepository cannot be null");
    this.outboxRepository = Objects.requireNonNull(outboxRepository,
        "outboxRepository cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    this.otpGenerator = Objects.requireNonNull(otpGenerator, "otpGenerator cannot be null");
    this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher cannot be null");
    this.emailOtpTtl = Objects.requireNonNull(emailOtpTtl, "emailOtpTtl cannot be null");
    this.resendMinInterval = Objects.requireNonNull(resendMinInterval,
        "resendMinInterval cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    this.emailCommandsTopic = Objects.requireNonNull(emailCommandsTopic,
        "emailCommandsTopic cannot be null");
  }

  @Transactional
  public IssueResult issue(UserId userId, Email email, String reason, String correlationId) {
    var now = clock.now();
    var latestActive = verificationTokenRepository.findLatestActiveByUserId(
        userId,
        VerificationToken.Type.EMAIL_VERIFICATION,
        now);

    if (latestActive.isPresent()) {
      var lastSentAt = latestActive.get().lastSentAt();
      if (lastSentAt != null && now.isBefore(lastSentAt.plus(resendMinInterval))) {
        log.info(
            "email_verification_resend_rate_limited reason={} userId={} emailFingerprint={}",
            reason,
            userId.value(),
            PiiUtil.emailFingerprint(email.value()));
        return new IssueResult(null, emailOtpTtl.toSeconds(), false);
      }
    }

    verificationTokenRepository.invalidateActiveTokens(userId,
        VerificationToken.Type.EMAIL_VERIFICATION, now);

    String otp = otpGenerator.generate6Digits();
    String otpHash = tokenHasher.sha256Hex(otp);
    UUID verificationSessionId = UUID.randomUUID();

    verificationTokenRepository.saveNewToken(
        VerificationToken.emailVerificationOtp(userId, otpHash, verificationSessionId, now,
            emailOtpTtl));

    var event =
        EmailVerificationRequestedCommand.emailVerification(
            UUID.randomUUID().toString(),
            now,
            correlationId,
            userId.value().toString(),
            email.value(),
            otp);

    outboxRepository.insertPending(
        new OutboxEvent(
            UUID.randomUUID(),
            AGGREGATE_TYPE,
            userId.value(),
            EmailVerificationRequestedCommand.EMAIL_VERIFICATION_REQUESTED,
            emailCommandsTopic,
            userId.value().toString(),
            payloadJson(event),
            0,
            now,
            now));

    log.info(
        "email_verification_issued reason={} userId={} emailFingerprint={}",
        reason,
        userId.value(),
        PiiUtil.emailFingerprint(email.value()));

    return new IssueResult(verificationSessionId, emailOtpTtl.toSeconds(), true);
  }

  private String payloadJson(EmailVerificationRequestedCommand command) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", command.eventId());
    payload.put("occurredAt", command.occurredAt());
    payload.put("type", command.type());
    payload.put("toEmail", command.toEmail());
    payload.put("verificationCode", command.verificationCode());
    payload.put("template", command.template());
    payload.put("correlationId", command.correlationId());
    payload.put("userId", command.userId());
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to serialize outbox payload", ex);
    }
  }

  public record IssueResult(UUID verificationSessionId, long ttlSeconds, boolean issued) {

  }
}