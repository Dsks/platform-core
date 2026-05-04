package app.qomo.emailsender.infrastructure.adapter.out.crypto;

import app.qomo.emailsender.application.port.out.PayloadCryptoPort;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmPayloadCryptoAdapter implements PayloadCryptoPort {

  private static final String ALG = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int NONCE_LEN = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec keySpec;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesGcmPayloadCryptoAdapter(String keyB64) {
    byte[] key = Base64.getDecoder().decode(keyB64);
    if (key.length != 32) {
      throw new IllegalArgumentException("PAYLOAD_KEY_B64 must decode to 32 bytes (AES-256)");
    }
    this.keySpec = new SecretKeySpec(key, ALG);
  }

  @Override
  public EncryptedPayload encrypt(byte[] plaintext) {
    try {
      byte[] nonce = new byte[NONCE_LEN];
      secureRandom.nextBytes(nonce);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));

      byte[] ciphertext = cipher.doFinal(plaintext);
      return new EncryptedPayload(ciphertext, nonce);
    } catch (Exception e) {
      throw new IllegalStateException("payload_encrypt_failed", e);
    }
  }

  @Override
  public byte[] decrypt(EncryptedPayload encrypted) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, encrypted.nonce()));
      return cipher.doFinal(encrypted.ciphertext());
    } catch (Exception e) {
      throw new IllegalStateException("payload_decrypt_failed", e);
    }
  }
}
