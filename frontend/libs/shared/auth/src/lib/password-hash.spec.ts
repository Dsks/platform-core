import { webcrypto } from 'node:crypto';
import { sha256Hex } from './password-hash';

describe('sha256Hex', () => {
  const originalWindowDescriptor = Object.getOwnPropertyDescriptor(
    globalThis,
    'window',
  );

  beforeAll(() => {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: {
        crypto: webcrypto,
      },
    });
  });

  afterAll(() => {
    if (originalWindowDescriptor) {
      Object.defineProperty(globalThis, 'window', originalWindowDescriptor);

      return;
    }

    delete (globalThis as { window?: Window }).window;
  });

  it('returns a lowercase SHA-256 hex digest', async () => {
    await expect(sha256Hex('password')).resolves.toBe(
      '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8',
    );
  });

  it('hashes empty strings', async () => {
    await expect(sha256Hex('')).resolves.toBe(
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    );
  });
});
