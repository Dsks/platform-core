import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { AuthApiService, CsrfTokenStore } from '@qomo/shared-api';
import { CurrentUser } from '@qomo/shared-models';
import { of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { SessionStore } from './session-store';

const user: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'user@example.com',
  active: true,
  emailVerified: true,
  roles: ['USER'],
};

describe('AuthService', () => {
  let service: AuthService;
  let sessionStore: SessionStore;
  let csrfTokenStore: CsrfTokenStore;
  let authApi: {
    login: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
    me: ReturnType<typeof vi.fn>;
    csrf: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authApi = {
      login: vi.fn(),
      logout: vi.fn(),
      me: vi.fn(),
      csrf: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AuthApiService, useValue: authApi }],
    });

    service = TestBed.inject(AuthService);
    sessionStore = TestBed.inject(SessionStore);
    csrfTokenStore = TestBed.inject(CsrfTokenStore);
  });

  it('bootstraps an authenticated session from /me', () => {
    authApi.me.mockReturnValue(of(user));

    service.bootstrapSession().subscribe((result) => {
      expect(result).toEqual(user);
      expect(sessionStore.currentUser()).toEqual(user);
      expect(sessionStore.isAuthenticated()).toBe(true);
    });

    expect(authApi.me).toHaveBeenCalledTimes(1);
  });

  it('sets guest when bootstrap receives 401', () => {
    authApi.me.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            statusText: 'Unauthorized',
          }),
      ),
    );

    service.bootstrapSession().subscribe((result) => {
      expect(result).toBeNull();
      expect(sessionStore.status()).toBe('guest');
      expect(sessionStore.currentUser()).toBeNull();
    });
  });

  it('logs in and then loads the current user', () => {
    const request = {
      email: 'user@example.com',
      password: 's3cret',
    };
    authApi.login.mockReturnValue(of(undefined));
    authApi.me.mockReturnValue(of(user));

    service.login(request).subscribe((result) => {
      expect(result).toEqual(user);
      expect(sessionStore.currentUser()).toEqual(user);
    });

    expect(authApi.login).toHaveBeenCalledWith(request);
    expect(authApi.me).toHaveBeenCalledTimes(1);
  });

  it('loads csrf token only when it is missing', () => {
    authApi.csrf.mockReturnValue(
      of({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-123',
      }),
    );

    service.ensureCsrfToken().subscribe((csrf) => {
      expect(csrf.token).toBe('csrf-123');
    });

    expect(authApi.csrf).toHaveBeenCalledTimes(1);

    service.ensureCsrfToken().subscribe((csrf) => {
      expect(csrf.token).toBe('csrf-123');
    });

    expect(authApi.csrf).toHaveBeenCalledTimes(1);
  });

  it('calls backend logout and clears local session state', () => {
    sessionStore.setAuthenticated(user);
    csrfTokenStore.set({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-123',
    });
    authApi.logout.mockReturnValue(of(undefined));

    service.logout().subscribe(() => {
      expect(sessionStore.status()).toBe('guest');
      expect(csrfTokenStore.snapshot()).toBeNull();
    });

    expect(authApi.logout).toHaveBeenCalledTimes(1);
    expect(authApi.me).not.toHaveBeenCalled();
  });

  it('fetches a csrf token before logout when none is cached', () => {
    sessionStore.setAuthenticated(user);
    authApi.csrf.mockReturnValue(
      of({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-123',
      }),
    );
    authApi.logout.mockReturnValue(of(undefined));

    service.logout().subscribe();

    expect(authApi.csrf).toHaveBeenCalledTimes(1);
    expect(authApi.logout).toHaveBeenCalledTimes(1);
    expect(sessionStore.status()).toBe('guest');
  });
});
