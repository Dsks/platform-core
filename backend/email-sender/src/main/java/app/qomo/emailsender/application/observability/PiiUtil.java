package app.qomo.emailsender.application.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PiiUtil {

  private PiiUtil() {}

  public static String emailFingerprint(String email) {
    if (email == null) {
      return null;
    }

    String normalized = email.trim().toLowerCase();
    if (normalized.isBlank()) {
      return "";
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash).substring(0, 12);
    } catch (Exception ex) {
      return "hash_error";
    }
  }
}
