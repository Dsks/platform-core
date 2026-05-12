import {
  EnvironmentProviders,
  InjectionToken,
  makeEnvironmentProviders,
} from '@angular/core';

export interface AuthRedirects {
  loginPath: string;
  authenticatedPath: string;
  notAuthorizedPath: string;
}

export type AuthExternalRedirect = (path: string) => void;

export const DEFAULT_AUTH_REDIRECTS: AuthRedirects = {
  loginPath: '/login',
  authenticatedPath: '/dashboard',
  notAuthorizedPath: '/not-authorized',
};

export const AUTH_REDIRECTS = new InjectionToken<AuthRedirects>(
  'AUTH_REDIRECTS',
  {
    providedIn: 'root',
    factory: () => DEFAULT_AUTH_REDIRECTS,
  },
);

export const AUTH_EXTERNAL_REDIRECT = new InjectionToken<AuthExternalRedirect>(
  'AUTH_EXTERNAL_REDIRECT',
  {
    providedIn: 'root',
    factory: () => (path: string) => {
      window.location.href = path;
    },
  },
);

export function provideAuthRedirects(
  redirects: Partial<AuthRedirects>,
): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: AUTH_REDIRECTS,
      useValue: {
        ...DEFAULT_AUTH_REDIRECTS,
        ...redirects,
      },
    },
  ]);
}
