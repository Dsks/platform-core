import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { API_BASE_URL } from './api-base-url';
import { apiCredentialsInterceptor } from './api-credentials.interceptor';
import { apiCsrfInterceptor } from './api-csrf.interceptor';
import { normalizeApiBaseUrl } from './api-url';

export interface SharedApiConfig {
  baseUrl?: string;
  csrfInterceptor?: boolean;
}

export function provideSharedApi(
  config: SharedApiConfig = {},
): EnvironmentProviders {
  const interceptors =
    config.csrfInterceptor === false
      ? [apiCredentialsInterceptor]
      : [apiCredentialsInterceptor, apiCsrfInterceptor];

  return makeEnvironmentProviders([
    {
      provide: API_BASE_URL,
      useValue: normalizeApiBaseUrl(config.baseUrl),
    },
    provideHttpClient(withInterceptors(interceptors)),
  ]);
}
