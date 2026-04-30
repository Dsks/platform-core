package app.qomo.emailsender.infrastructure.adapter.out.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.qomo.emailsender.domain.port.out.PayloadCryptoPort.EncryptedPayload;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmPayloadCryptoAdapterTest {

  @Test
  void encryptAndDecrypt_withValidKey_returnsOriginalPayload() {
    AesGcmPayloadCryptoAdapter adapter = new AesGcmPayloadCryptoAdapter(validKeyB64());
    byte[] plaintext = "important-email-payload".getBytes(StandardCharsets.UTF_8);

    EncryptedPayload encrypted = adapter.encrypt(plaintext);

    assertArrayEquals(plaintext, adapter.decrypt(encrypted));
  }

  @Test
  void constructor_withKeyDecodedLengthDifferentThan32_throwsIllegalArgumentException() {
    String shortKeyB64 = Base64.getEncoder()
        .encodeToString("short-key".getBytes(StandardCharsets.UTF_8));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class,
            () -> new AesGcmPayloadCryptoAdapter(shortKeyB64));

    assertNotEquals(-1, exception.getMessage().indexOf("must decode to 32 bytes"));
  }

  @Test
  void constructor_withMalformedBase64_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class,
        () -> new AesGcmPayloadCryptoAdapter("###not-base64###"));
  }

  @Test
  void decrypt_withTamperedCiphertext_throwsIllegalStateException() {
    AesGcmPayloadCryptoAdapter adapter = new AesGcmPayloadCryptoAdapter(validKeyB64());
    EncryptedPayload encrypted =
        adapter.encrypt("payload-that-must-not-be-tampered".getBytes(StandardCharsets.UTF_8));

    byte[] tamperedCiphertext = encrypted.ciphertext().clone();
    tamperedCiphertext[0] = (byte) (tamperedCiphertext[0] ^ 0x01);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> adapter.decrypt(new EncryptedPayload(tamperedCiphertext, encrypted.nonce())));

    assertNotEquals(-1, exception.getMessage().indexOf("payload_decrypt_failed"));
  }

  @Test
  void decrypt_withTamperedNonce_throwsIllegalStateException() {
    AesGcmPayloadCryptoAdapter adapter = new AesGcmPayloadCryptoAdapter(validKeyB64());
    EncryptedPayload encrypted =
        adapter.encrypt("payload-with-nonce".getBytes(StandardCharsets.UTF_8));

    byte[] tamperedNonce = encrypted.nonce().clone();
    tamperedNonce[0] = (byte) (tamperedNonce[0] ^ 0x01);

    assertThrows(IllegalStateException.class,
        () -> adapter.decrypt(new EncryptedPayload(encrypted.ciphertext(), tamperedNonce)));
  }

  private String validKeyB64() {
    byte[] key = new byte[32];
    for (int i = 0; i < key.length; i++) {
      key[i] = (byte) (i + 1);
    }
    return Base64.getEncoder().encodeToString(key);
  }
}