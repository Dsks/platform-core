import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  UrlTree,
} from '@angular/router';
import { CurrentUser } from '@platformcore/shared-models';
import { adminGuard, authGuard, guestGuard } from './auth-guards';
import { AUTH_EXTERNAL_REDIRECT, provideAuthRedirects } from './auth-redirects';
import { SessionStore } from './session-store';

const user: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'user@example.com',
  active: true,
  emailVerified: true,
  roles: ['USER'],
};

const admin: CurrentUser = {
  ...user,
  roles: ['ADMIN'],
};

describe('auth guards', () => {
  let router: Router;
  let sessionStore: SessionStore;
  let externalRedirect: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    externalRedirect = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AUTH_EXTERNAL_REDIRECT, useValue: externalRedirect },
        provideAuthRedirects({
          loginPath: '/sign-in',
          authenticatedPath: '/home',
          notAuthorizedPath: '/forbidden',
        }),
      ],
    });

    router = TestBed.inject(Router);
    sessionStore = TestBed.inject(SessionStore);
  });

  it('authGuard allows authenticated users', () => {
    sessionStore.setAuthenticated(user);

    const result = runGuard(authGuard);

    expect(result).toBe(true);
  });

  it('authGuard redirects guests to login', () => {
    sessionStore.setGuest();

    const result = runGuard(authGuard);

    expect(serializeGuardResult(result)).toBe('/sign-in');
  });

  it('guestGuard redirects authenticated users', () => {
    sessionStore.setAuthenticated(user);

    const result = runGuard(guestGuard);

    expect(serializeGuardResult(result)).toBe('/home');
  });

  it('adminGuard allows admin users', () => {
    sessionStore.setAuthenticated(admin);

    const result = runGuard(adminGuard);

    expect(result).toBe(true);
  });

  it('adminGuard redirects guests to login and non-admins to not authorized', () => {
    sessionStore.setGuest();

    expect(serializeGuardResult(runGuard(adminGuard))).toBe('/sign-in');

    sessionStore.setAuthenticated(user);

    expect(serializeGuardResult(runGuard(adminGuard))).toBe('/forbidden');
  });

  it('adminGuard can redirect non-admin users outside Angular routing', () => {
    sessionStore.setAuthenticated(user);

    const result = runGuard(adminGuard, {
      externalNotAuthorizedRedirect: true,
      notAuthorizedPath: '/dashboard',
    });

    expect(result).toBe(false);
    expect(externalRedirect).toHaveBeenCalledWith('/dashboard');
  });

  it('supports route-level redirect overrides', () => {
    sessionStore.setGuest();

    const result = runGuard(authGuard, {
      loginPath: '/custom-login',
    });

    expect(serializeGuardResult(result)).toBe('/custom-login');
  });

  function runGuard(
    guard: typeof authGuard,
    data: Record<string, unknown> = {},
  ) {
    return TestBed.runInInjectionContext(() =>
      guard(
        { data } as ActivatedRouteSnapshot,
        {
          url: '/protected',
        } as never,
      ),
    );
  }

  function serializeGuardResult(result: ReturnType<typeof authGuard>): string {
    if (typeof result === 'boolean') {
      return String(result);
    }

    if (result instanceof UrlTree) {
      return router.serializeUrl(result);
    }

    throw new Error('Expected a synchronous UrlTree guard result.');
  }
});
