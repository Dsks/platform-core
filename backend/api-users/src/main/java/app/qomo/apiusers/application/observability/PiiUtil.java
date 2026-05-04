package app.qomo.apiusers.application.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PiiUtil {

  private PiiUtil() {}

  public static String emailFingerprint(String email) {
    if (email == null) {
      return null;
    }
    var normalized = email.trim().toLowerCase();
    try {
      var md = MessageDigest.getInstance("SHA-256");
      var bytes = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes).substring(0, 12);
    } catch (Exception e) {
      return "hash_error";
    }
  }
}
