import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  UrlTree,
} from '@angular/router';
import { buildApiUrl, isApiUrl, normalizeApiBaseUrl } from '@platformcore/shared-api';
import {
  adminGuard,
  authGuard,
  AUTH_EXTERNAL_REDIRECT,
  AUTH_REDIRECTS,
  DEFAULT_AUTH_REDIRECTS,
  guestGuard,
  provideAuthRedirects,
  SessionStore,
} from '@platformcore/shared-auth';
import { CurrentUser } from '@platformcore/shared-models';

const adminUser: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'admin@example.com',
  active: true,
  emailVerified: true,
  roles: ['ADMIN'],
};

const superAdminUser: CurrentUser = {
  ...adminUser,
  email: 'superadmin@example.com',
  roles: ['SUPERADMIN'],
};

const clientUser: CurrentUser = {
  ...adminUser,
  email: 'client@example.com',
  roles: ['CLIENT'],
};

const rolelessUser: CurrentUser = {
  ...adminUser,
  email: 'roleless@example.com',
  roles: [],
};

describe('shared behavior used by platformcore-admin', () => {
  describe('SessionStore', () => {
    let store: SessionStore;

    beforeEach(() => {
      TestBed.configureTestingModule({});
      store = TestBed.inject(SessionStore);
    });

    it('starts without an authenticated or guest session', () => {
      expect(store.state()).toEqual({
        status: 'unknown',
        currentUser: null,
      });
      expect(store.status()).toBe('unknown');
      expect(store.currentUser()).toBeNull();
      expect(store.roles()).toEqual([]);
      expect(store.isAuthenticated()).toBe(false);
      expect(store.isGuest()).toBe(false);
      expect(store.isAdmin()).toBe(false);
      expect(store.isSuperAdmin()).toBe(false);
      expect(store.isClient()).toBe(false);
      expect(store.hasAnyRole('ADMIN')).toBe(false);
    });

    it('stores an authenticated admin and exposes role helpers', () => {
      store.setAuthenticated(adminUser);

      expect(store.state()).toEqual({
        status: 'authenticated',
        currentUser: adminUser,
      });
      expect(store.status()).toBe('authenticated');
      expect(store.currentUser()).toEqual(adminUser);
      expect(store.roles()).toEqual(['ADMIN']);
      expect(store.isAuthenticated()).toBe(true);
      expect(store.isGuest()).toBe(false);
      expect(store.isAdmin()).toBe(true);
      expect(store.isSuperAdmin()).toBe(false);
      expect(store.isClient()).toBe(false);
      expect(store.hasAnyRole('CLIENT', 'ADMIN')).toBe(true);
      expect(store.hasAnyRole('CLIENT')).toBe(false);
    });

    it('computes superadmin and client capabilities from roles', () => {
      store.setAuthenticated(superAdminUser);

      expect(store.isAdmin()).toBe(true);
      expect(store.isSuperAdmin()).toBe(true);
      expect(store.isClient()).toBe(false);
      expect(store.hasAnyRole('SUPERADMIN')).toBe(true);

      store.setAuthenticated(clientUser);

      expect(store.isAdmin()).toBe(false);
      expect(store.isSuperAdmin()).toBe(false);
      expect(store.isClient()).toBe(true);
      expect(store.hasAnyRole('CLIENT')).toBe(true);
    });

    it('treats authenticated users without explicit admin roles as clients', () => {
      store.setAuthenticated(rolelessUser);

      expect(store.isAuthenticated()).toBe(true);
      expect(store.isAdmin()).toBe(false);
      expect(store.isClient()).toBe(true);
    });

    it('can become guest and clear back to the initial state', () => {
      store.setAuthenticated(adminUser);
      store.setGuest();

      expect(store.status()).toBe('guest');
      expect(store.currentUser()).toBeNull();
      expect(store.roles()).toEqual([]);
      expect(store.isAuthenticated()).toBe(false);
      expect(store.isGuest()).toBe(true);
      expect(store.hasAnyRole('ADMIN')).toBe(false);

      store.clear();

      expect(store.state()).toEqual({
        status: 'unknown',
        currentUser: null,
      });
      expect(store.isGuest()).toBe(false);
    });
  });

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
            loginPath: '/iniciar-sesion',
            authenticatedPath: '/panel',
            notAuthorizedPath: '/sin-permisos',
          }),
        ],
      });

      router = TestBed.inject(Router);
      sessionStore = TestBed.inject(SessionStore);
    });

    it('authGuard allows authenticated users and redirects guests to login', () => {
      sessionStore.setAuthenticated(adminUser);

      expect(runGuard(authGuard)).toBe(true);

      sessionStore.setGuest();

      expect(serializeGuardResult(runGuard(authGuard))).toBe(
        '/iniciar-sesion',
      );
    });

    it('guestGuard allows guests and redirects authenticated users', () => {
      sessionStore.setGuest();

      expect(runGuard(guestGuard)).toBe(true);

      sessionStore.setAuthenticated(adminUser);

      expect(serializeGuardResult(runGuard(guestGuard))).toBe('/panel');
    });

    it('adminGuard allows admins, redirects guests, and blocks non-admin users', () => {
      sessionStore.setAuthenticated(adminUser);

      expect(runGuard(adminGuard)).toBe(true);

      sessionStore.setGuest();

      expect(serializeGuardResult(runGuard(adminGuard))).toBe(
        '/iniciar-sesion',
      );

      sessionStore.setAuthenticated(clientUser);

      expect(serializeGuardResult(runGuard(adminGuard))).toBe('/sin-permisos');
    });

    it('adminGuard can use a route-level external redirect for non-admin users', () => {
      sessionStore.setAuthenticated(clientUser);

      const result = runGuard(adminGuard, {
        externalNotAuthorizedRedirect: true,
        notAuthorizedPath: '/panel',
      });

      expect(result).toBe(false);
      expect(externalRedirect).toHaveBeenCalledWith('/panel');
    });

    it('uses route-level redirect overrides before configured defaults', () => {
      sessionStore.setGuest();

      expect(
        serializeGuardResult(
          runGuard(authGuard, {
            loginPath: '/login-personalizado',
          }),
        ),
      ).toBe('/login-personalizado');
    });

    function runGuard(
      guard: CanActivateFn,
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

    function serializeGuardResult(result: ReturnType<CanActivateFn>): string {
      if (typeof result === 'boolean') {
        return String(result);
      }

      if (result instanceof UrlTree) {
        return router.serializeUrl(result);
      }

      throw new Error('Expected a synchronous UrlTree guard result.');
    }
  });

  describe('auth redirects providers', () => {
    it('provides default redirects from the root token factory', () => {
      TestBed.configureTestingModule({});

      expect(TestBed.inject(AUTH_REDIRECTS)).toEqual(DEFAULT_AUTH_REDIRECTS);
    });

    it('merges redirect overrides with defaults', () => {
      TestBed.configureTestingModule({
        providers: [
          provideAuthRedirects({
            loginPath: '/iniciar-sesion',
          }),
        ],
      });

      expect(TestBed.inject(AUTH_REDIRECTS)).toEqual({
        ...DEFAULT_AUTH_REDIRECTS,
        loginPath: '/iniciar-sesion',
      });
    });

    it('provides an external redirect function without invoking navigation', () => {
      TestBed.configureTestingModule({});

      expect(TestBed.inject(AUTH_EXTERNAL_REDIRECT)).toEqual(
        expect.any(Function),
      );
    });
  });

  describe('api URL helpers', () => {
    it('normalizes API base URLs', () => {
      expect(normalizeApiBaseUrl()).toBe('/v1');
      expect(normalizeApiBaseUrl('')).toBe('/v1');
      expect(normalizeApiBaseUrl('  /v1///  ')).toBe('/v1');
      expect(normalizeApiBaseUrl('https://api.platformcore.app/v1/')).toBe(
        'https://api.platformcore.app/v1',
      );
    });

    it('builds API URLs from normalized bases and relative paths', () => {
      expect(buildApiUrl('auth/login')).toBe('/v1/auth/login');
      expect(buildApiUrl('/auth/me', '/v1/')).toBe('/v1/auth/me');
      expect(buildApiUrl('  users  ', 'https://api.platformcore.app/v1/')).toBe(
        'https://api.platformcore.app/v1/users',
      );
      expect(buildApiUrl('', '/v1/')).toBe('/v1');
    });

    it('detects URLs that belong to the configured API base', () => {
      expect(isApiUrl('/v1/auth/login')).toBe(true);
      expect(isApiUrl('/assets/config.json')).toBe(false);
      expect(isApiUrl('https://frontend.example/v1/auth/login')).toBe(true);
      expect(
        isApiUrl(
          'https://api.platformcore.app/v1/auth/login',
          'https://api.platformcore.app/v1',
        ),
      ).toBe(true);
      expect(
        isApiUrl(
          'https://other.example/v1/auth/login',
          'https://api.platformcore.app/v1',
        ),
      ).toBe(false);
      expect(isApiUrl('https://[malformed-url', '/v1')).toBe(false);
    });
  });
});
