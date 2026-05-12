import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { AUTH_EXTERNAL_REDIRECT, AUTH_REDIRECTS } from './auth-redirects';
import { SessionStore } from './session-store';

export const authGuard: CanActivateFn = (route) => {
  const sessionStore = inject(SessionStore);

  if (sessionStore.isAuthenticated()) {
    return true;
  }

  return redirectTo(route, 'loginPath');
};

export const guestGuard: CanActivateFn = (route) => {
  const sessionStore = inject(SessionStore);

  if (!sessionStore.isAuthenticated()) {
    return true;
  }

  return redirectTo(route, 'authenticatedPath');
};

export const adminGuard: CanActivateFn = (route) => {
  const sessionStore = inject(SessionStore);

  if (sessionStore.isAdmin()) {
    return true;
  }

  if (!sessionStore.isAuthenticated()) {
    return redirectTo(route, 'loginPath');
  }

  if (route.data[externalRedirectKeys.notAuthorized] === true) {
    redirectExternallyTo(route, 'notAuthorizedPath');

    return false;
  }

  return redirectTo(route, 'notAuthorizedPath');
};

function redirectTo(route: ActivatedRouteSnapshot, key: keyof typeof dataKeys) {
  const router = inject(Router);
  const path = resolveRedirectPath(route, key);

  return router.parseUrl(path);
}

function redirectExternallyTo(
  route: ActivatedRouteSnapshot,
  key: keyof typeof dataKeys,
): void {
  const externalRedirect = inject(AUTH_EXTERNAL_REDIRECT);
  const path = resolveRedirectPath(route, key);

  externalRedirect(path);
}

function resolveRedirectPath(
  route: ActivatedRouteSnapshot,
  key: keyof typeof dataKeys,
): string {
  const redirects = inject(AUTH_REDIRECTS);
  const routeDataKey = dataKeys[key];
  const configuredPath = route.data[routeDataKey];

  return typeof configuredPath === 'string' ? configuredPath : redirects[key];
}

const dataKeys = {
  loginPath: 'loginPath',
  authenticatedPath: 'authenticatedPath',
  notAuthorizedPath: 'notAuthorizedPath',
} as const;

const externalRedirectKeys = {
  notAuthorized: 'externalNotAuthorizedRedirect',
} as const;
