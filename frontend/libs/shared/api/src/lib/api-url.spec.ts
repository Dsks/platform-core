import { buildApiUrl, isApiUrl, normalizeApiBaseUrl } from './api-url';

describe('api-url', () => {
  it('normalizes the default gateway base URL', () => {
    expect(normalizeApiBaseUrl()).toBe('/v1');
    expect(normalizeApiBaseUrl('/v1/')).toBe('/v1');
    expect(normalizeApiBaseUrl('  /v1///  ')).toBe('/v1');
  });

  it('builds relative API URLs without hardcoded hosts', () => {
    expect(buildApiUrl('auth/login')).toBe('/v1/auth/login');
    expect(buildApiUrl('/auth/me', '/v1/')).toBe('/v1/auth/me');
    expect(buildApiUrl('', '/v1/')).toBe('/v1');
  });

  it('detects URLs that belong to the configured API base', () => {
    expect(isApiUrl('/v1/auth/login')).toBe(true);
    expect(isApiUrl('/assets/config.json')).toBe(false);
    expect(
      isApiUrl('https://api.qomo.app/v1/auth/login', 'https://api.qomo.app/v1'),
    ).toBe(true);
    expect(
      isApiUrl(
        'https://other.example/v1/auth/login',
        'https://api.qomo.app/v1',
      ),
    ).toBe(false);
  });
});
