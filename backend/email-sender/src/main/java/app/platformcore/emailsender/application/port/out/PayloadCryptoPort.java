package app.platformcore.emailsender.application.port.out;

/**
 * Boundary for protecting email job payloads before they are persisted and reading them back for
 * processing.
 *
 * <p>Implementations normally belong to infrastructure and encapsulate the cryptographic provider,
 * key handling, nonce generation, and binary representation. Application services pass raw payload
 * bytes to this port and receive encrypted material that can be stored through persistence ports,
 * or pass persisted encrypted material back to recover the original payload.
 */
public interface PayloadCryptoPort {

  /**
   * Encrypts application-prepared payload bytes for storage.
   *
   * <p>The returned value contains all non-secret encrypted material the application must persist
   * in order to decrypt the payload later.
   *
   * @param plaintext payload bytes produced by the application layer
   * @return encrypted payload and nonce to persist with the email job
   */
  EncryptedPayload encrypt(byte[] plaintext);

  /**
   * Decrypts payload material previously produced by this boundary.
   *
   * @param encrypted ciphertext and nonce loaded from persistence
   * @return original payload bytes for application processing
   */
  byte[] decrypt(EncryptedPayload encrypted);

  /**
   * Encrypted representation of an application payload that is safe to hand to persistence.
   *
   * @param ciphertext encrypted payload bytes
   * @param nonce nonce associated with the ciphertext and required for decryption
   */
  record EncryptedPayload(byte[] ciphertext, byte[] nonce) {}
}
