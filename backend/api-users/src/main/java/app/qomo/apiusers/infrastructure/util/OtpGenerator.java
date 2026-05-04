package app.qomo.apiusers.infrastructure.util;

import java.security.SecureRandom;

public class OtpGenerator {

  private static final int OTP_MAX_EXCLUSIVE = 1_000_000;

  private final SecureRandom secureRandom;

  public OtpGenerator() {
    this.secureRandom = new SecureRandom();
  }

  public String generate6Digits() {
    int value = secureRandom.nextInt(OTP_MAX_EXCLUSIVE);
    return "%06d".formatted(value);
  }
}
