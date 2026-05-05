package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.command.EmailVerificationRequestedCommand;
import app.qomo.apiusers.application.observability.PiiUtil;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.OutboxRepositoryPort;
import app.qomo.apiusers.application.port.out.VerificationTokenRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.OutboxEvent;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.model.VerificationToken;
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

/**
 * Issues email-verification challenges for registration, resend, and valid-but-unverified login
 * paths.
 *
 * <p>This service owns the write side of verification issuance: it enforces the minimum resend
 * interval, invalidates older active email-verification tokens before creating a new one, stores
 * only the hashed OTP, and publishes the email command through the outbox port. Callers choose the
 * business reason and correlation id so the same issuance policy can be reused by registration,
 * resend, and valid-but-unverified login flows.
 *
 * <p>Issuing is different from resending and verifying: resend decides whether an external retry
 * request should be allowed to reach issuance, while verification validates a submitted session and
 * code. The generated OTP and recipient email are sensitive; logs use fingerprints and must not
 * expose codes, verification sessions, tokens, or secrets in clear text.
 */
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
        Objects.requireNonNull(
            verificationTokenRepository, "verificationTokenRepository cannot be null");
    this.outboxRepository =
        Objects.requireNonNull(outboxRepository, "outboxRepository cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    this.otpGenerator = Objects.requireNonNull(otpGenerator, "otpGenerator cannot be null");
    this.tokenHasher = Objects.requireNonNull(tokenHasher, "tokenHasher cannot be null");
    this.emailOtpTtl = Objects.requireNonNull(emailOtpTtl, "emailOtpTtl cannot be null");
    this.resendMinInterval =
        Objects.requireNonNull(resendMinInterval, "resendMinInterval cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    this.emailCommandsTopic =
        Objects.requireNonNull(emailCommandsTopic, "emailCommandsTopic cannot be null");
  }

  /**
   * Creates a new email-verification OTP and queues the corresponding email command unless the
   * latest active token is still inside the configured resend interval.
   *
   * <p>When rate-limited, the method returns a non-issued result without invalidating tokens or
   * publishing an outbox event. When issued, it invalidates previous active email-verification
   * tokens, generates and hashes a six-digit OTP, persists the new token with a verification
   * session id, and inserts a pending outbox event containing the email command payload.
   *
   * @param userId target user that must prove ownership of the email address
   * @param email canonical recipient email for the verification challenge
   * @param reason sanitized operational reason used for audit logs and diagnostics
   * @param correlationId caller-supplied correlation id propagated into the outbox payload
   * @return issuance metadata including the verification session when a challenge was sent, the
   *     configured TTL, and a flag indicating whether issuance occurred
   * @throws IllegalStateException when the email command payload cannot be serialized
   */
  @Transactional
  public IssueResult issue(UserId userId, Email email, String reason, String correlationId) {
    var now = clock.now();
    var latestActive =
        verificationTokenRepository.findLatestActiveByUserId(
            userId, VerificationToken.Type.EMAIL_VERIFICATION, now);

    if (latestActive.isPresent()) {
      var lastSentAt = latestActive.get().lastSentAt();
      if (lastSentAt != null && now.isBefore(lastSentAt.plus(resendMinInterval))) {
        // Rate limiting leaves the active token usable instead of rotating it away.
        log.info(
            "email_verification_resend_rate_limited reason={} userId={} emailFingerprint={}",
            reason,
            userId.value(),
            PiiUtil.emailFingerprint(email.value()));
        return new IssueResult(null, emailOtpTtl.toSeconds(), false);
      }
    }

    // Rotate active sessions before creating the replacement so only the newest OTP can verify.
    verificationTokenRepository.invalidateActiveTokens(
        userId, VerificationToken.Type.EMAIL_VERIFICATION, now);

    String otp = otpGenerator.generate6Digits();
    String otpHash = tokenHasher.sha256Hex(otp);
    UUID verificationSessionId = UUID.randomUUID();

    verificationTokenRepository.saveNewToken(
        VerificationToken.emailVerificationOtp(
            userId, otpHash, verificationSessionId, now, emailOtpTtl));

    // Persist the token before enqueuing email so a delivered OTP can verify immediately.
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

  /**
   * Serializes the outbox payload that downstream email delivery needs.
   *
   * <p>The verification code is intentionally present in this command payload so the email service
   * can deliver it to the user, but it must be treated as secret material by every outbox consumer
   * and logging path.
   */
  private String payloadJson(EmailVerificationRequestedCommand command) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", command.eventId());
    payload.put("occurredAt", command.occurredAt());
    payload.put("type", command.type());
    payload.put("toEmail", command.toEmail());
    // The OTP appears only in the delivery payload; logs must keep using fingerprints.
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

  /**
   * Captures the outcome of an email-verification issuance attempt.
   *
   * @param verificationSessionId session identifier to give back to the adapter when a new
   *     challenge was issued; {@code null} when issuance was rate-limited
   * @param ttlSeconds configured validity window for the verification challenge, in seconds
   * @param issued {@code true} when a new token and outbox event were created
   */
  public record IssueResult(UUID verificationSessionId, long ttlSeconds, boolean issued) {}
}
