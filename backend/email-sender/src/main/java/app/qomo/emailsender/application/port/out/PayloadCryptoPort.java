package app.qomo.emailsender.application.port.out;

public interface PayloadCryptoPort {

  EncryptedPayload encrypt(byte[] plaintext);

  byte[] decrypt(EncryptedPayload encrypted);

  record EncryptedPayload(byte[] ciphertext, byte[] nonce) {}
}
