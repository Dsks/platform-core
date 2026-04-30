package app.qomo.emailsender.domain.port.out;

public interface PayloadCryptoPort {

  EncryptedPayload encrypt(byte[] plaintext);

  byte[] decrypt(EncryptedPayload encrypted);

  record EncryptedPayload(byte[] ciphertext, byte[] nonce) {

  }
}