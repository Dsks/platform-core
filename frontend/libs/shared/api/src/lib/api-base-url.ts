import { InjectionToken } from '@angular/core';

export const DEFAULT_API_BASE_URL = '/v1';

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => DEFAULT_API_BASE_URL,
});
