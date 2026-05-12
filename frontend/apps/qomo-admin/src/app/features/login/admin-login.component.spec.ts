import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import {
  AUTH_EXTERNAL_REDIRECT,
  AuthService,
  sha256Hex,
} from '@qomo/shared-auth';
import { CurrentUser } from '@qomo/shared-models';
import { of, throwError } from 'rxjs';
import { AdminLoginComponent } from './admin-login.component';

const adminUser: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'admin@example.com',
  active: true,
  emailVerified: true,
  roles: ['ADMIN'],
};

const regularUser: CurrentUser = {
  ...adminUser,
  email: 'user@example.com',
  roles: ['USER'],
};

describe('AdminLoginComponent', () => {
  let fixture: ComponentFixture<AdminLoginComponent>;
  let authService: {
    login: ReturnType<typeof vi.fn>;
  };
  let router: {
    navigateByUrl: ReturnType<typeof vi.fn>;
  };
  let externalRedirect: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    authService = {
      login: vi.fn(),
    };
    router = {
      navigateByUrl: vi.fn(),
    };
    externalRedirect = vi.fn();

    await TestBed.configureTestingModule({
      imports: [AdminLoginComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
        { provide: AUTH_EXTERNAL_REDIRECT, useValue: externalRedirect },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminLoginComponent);
  });

  it('logs in an admin with a pre-hashed password and navigates to the admin dashboard', async () => {
    authService.login.mockReturnValue(of(adminUser));
    fillForm('admin@example.com', 's3cret');

    await fixture.componentInstance.submit();

    expect(authService.login).toHaveBeenCalledWith({
      email: 'admin@example.com',
      password: await sha256Hex('s3cret'),
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/panel');
    expect(externalRedirect).not.toHaveBeenCalled();
  });

  it('redirects authenticated non-admin users outside the admin app', async () => {
    authService.login.mockReturnValue(of(regularUser));
    fillForm('user@example.com', 's3cret');

    await fixture.componentInstance.submit();

    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(externalRedirect).toHaveBeenCalledWith('/panel');
  });

  it('shows a readable login error', async () => {
    authService.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            statusText: 'Unauthorized',
            error: {
              detail: 'Credenciales invalidas.',
            },
          }),
      ),
    );
    fillForm('admin@example.com', 'bad-password');

    await fixture.componentInstance.submit();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector(
      '.login-error',
    ) as HTMLElement | null;

    expect(error?.textContent).toContain('Email o contraseña incorrectos.');
  });

  function fillForm(email: string, password: string): void {
    fixture.componentInstance.form.setValue({
      email,
      password,
    });
  }
});
