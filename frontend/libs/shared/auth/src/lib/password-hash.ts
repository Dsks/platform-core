export async function sha256Hex(value: string): Promise<string> {
  const encodedValue = new TextEncoder().encode(value);
  const digest = await window.crypto.subtle.digest('SHA-256', encodedValue);

  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, '0'),
  ).join('');
}
