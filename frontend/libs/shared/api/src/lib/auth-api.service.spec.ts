import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideSharedApi } from './provide-shared-api';
import { AuthApiService } from './auth-api.service';
import { CsrfTokenStore } from './csrf-token-store';

describe('AuthApiService', () => {
  let service: AuthApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideSharedApi(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AuthApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('posts login to the backend login endpoint with credentials', () => {
    service
      .login({ email: 'user@example.com', password: 's3cret' })
      .subscribe();

    const request = http.expectOne('/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.body).toEqual({
      email: 'user@example.com',
      password: 's3cret',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('gets the current user through the auth me endpoint with credentials', () => {
    service.me().subscribe((user) => {
      expect(user).toEqual({
        id: '2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec',
        email: 'user@example.com',
        active: true,
        emailVerified: true,
        roles: ['USER'],
      });
    });

    const request = http.expectOne('/v1/auth/me');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      id: '2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec',
      email: 'user@example.com',
      active: true,
      emailVerified: true,
      roles: ['USER'],
    });
  });

  it('posts logout to the backend logout endpoint with credentials and csrf', () => {
    TestBed.inject(CsrfTokenStore).set({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-123',
    });

    service.logout().subscribe();

    const request = http.expectOne('/v1/auth/logout');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('gets and stores the csrf token without adding a csrf header to the GET request', () => {
    const store = TestBed.inject(CsrfTokenStore);

    service.csrf().subscribe((csrf) => {
      expect(csrf).toEqual({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-123',
      });
      expect(store.snapshot()).toEqual(csrf);
    });

    const request = http.expectOne('/v1/auth/csrf');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.has('X-CSRF-TOKEN')).toBe(false);
    request.flush({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-123',
    });
  });

  it('adds the stored csrf token to mutating API requests', () => {
    TestBed.inject(CsrfTokenStore).set({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-123',
    });

    service
      .register({ email: 'new@example.com', password: 's3cret' })
      .subscribe();

    const request = http.expectOne('/v1/auth/register');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
    request.flush(
      {
        requestId: 'req-123',
        status: 'VERIFICATION_REQUIRED',
        message: "If the email is valid, you'll receive next steps.",
      },
      { status: 202, statusText: 'Accepted' },
    );
  });

  it('uses the verification endpoints with credentials', () => {
    service.verifyEmail({ code: '123456' }).subscribe((response) => {
      expect(response.status).toBe(204);
    });
    service.resendVerification({ email: 'user@example.com' }).subscribe();

    const verifyRequest = http.expectOne('/v1/users/verify-email');
    expect(verifyRequest.request.method).toBe('POST');
    expect(verifyRequest.request.withCredentials).toBe(true);
    verifyRequest.flush(null, { status: 204, statusText: 'No Content' });

    const resendRequest = http.expectOne('/v1/users/verification/resend');
    expect(resendRequest.request.method).toBe('POST');
    expect(resendRequest.request.withCredentials).toBe(true);
    resendRequest.flush(
      {
        message: 'Forwarded Code',
      },
      { status: 202, statusText: 'Accepted' },
    );
  });
});
