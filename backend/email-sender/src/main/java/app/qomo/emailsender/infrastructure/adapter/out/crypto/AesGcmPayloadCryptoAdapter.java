package app.qomo.emailsender.infrastructure.adapter.out.crypto;

import app.qomo.emailsender.application.port.out.PayloadCryptoPort;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@link PayloadCryptoPort} implementation using JCA AES-GCM for persisted email job payloads.
 *
 * <p>This adapter encapsulates key decoding, JCA cipher selection, nonce generation, and
 * authenticated encryption details. Callers provide plaintext bytes before persistence and receive
 * ciphertext plus nonce that can be stored with the job; decryption requires the same pair loaded
 * from persistence.
 *
 * <p>Each encryption generates a fresh 96-bit nonce and uses a 128-bit authentication tag. The
 * adapter does not log plaintext, ciphertext, nonces, or key material; cryptographic failures are
 * converted to runtime exceptions so application services are not coupled to provider-specific
 * exception types.
 */
public final class AesGcmPayloadCryptoAdapter implements PayloadCryptoPort {

  private static final String ALG = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int NONCE_LEN = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec keySpec;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Creates the adapter from a Base64-encoded AES-256 key supplied by infrastructure configuration.
   *
   * @throws IllegalArgumentException when the decoded key is not exactly 32 bytes
   */
  public AesGcmPayloadCryptoAdapter(String keyB64) {
    byte[] key = Base64.getDecoder().decode(keyB64);
    if (key.length != 32) {
      throw new IllegalArgumentException("PAYLOAD_KEY_B64 must decode to 32 bytes (AES-256)");
    }
    this.keySpec = new SecretKeySpec(key, ALG);
  }

  /**
   * Encrypts application-prepared payload bytes for storage beside an email job.
   *
   * @return ciphertext and the generated nonce; both values are required for later decryption
   * @throws IllegalStateException when the configured crypto provider cannot encrypt the payload
   */
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

  /**
   * Decrypts persisted payload material previously produced with the configured key.
   *
   * @throws IllegalStateException when decryption fails, including authentication-tag validation
   *     failures or malformed encrypted input
   */
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
