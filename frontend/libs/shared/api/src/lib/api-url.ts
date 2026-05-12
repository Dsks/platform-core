import { DEFAULT_API_BASE_URL } from './api-base-url';

const absoluteUrlPattern = /^https?:\/\//i;

export function normalizeApiBaseUrl(baseUrl = DEFAULT_API_BASE_URL): string {
  const trimmed = baseUrl.trim();
  if (!trimmed) {
    return DEFAULT_API_BASE_URL;
  }

  return trimmed.replace(/\/+$/, '');
}

export function buildApiUrl(
  path: string,
  baseUrl = DEFAULT_API_BASE_URL,
): string {
  const normalizedBaseUrl = normalizeApiBaseUrl(baseUrl);
  const normalizedPath = path.trim().replace(/^\/+/, '');

  if (!normalizedPath) {
    return normalizedBaseUrl;
  }

  return `${normalizedBaseUrl}/${normalizedPath}`;
}

export function isApiUrl(url: string, baseUrl = DEFAULT_API_BASE_URL): boolean {
  const normalizedBaseUrl = normalizeApiBaseUrl(baseUrl);

  if (absoluteUrlPattern.test(normalizedBaseUrl)) {
    return url === normalizedBaseUrl || url.startsWith(`${normalizedBaseUrl}/`);
  }

  if (absoluteUrlPattern.test(url)) {
    try {
      const parsedUrl = new URL(url);
      return (
        parsedUrl.pathname === normalizedBaseUrl ||
        parsedUrl.pathname.startsWith(`${normalizedBaseUrl}/`)
      );
    } catch {
      return false;
    }
  }

  return url === normalizedBaseUrl || url.startsWith(`${normalizedBaseUrl}/`);
}
