import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService, sha256Hex } from '@platformcore/shared-auth';
import { CurrentUser } from '@platformcore/shared-models';
import { of, throwError } from 'rxjs';
import { ClientLoginComponent } from './client-login.component';

const user: CurrentUser = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'user@example.com',
  active: true,
  emailVerified: true,
  roles: ['CLIENT'],
};

describe('ClientLoginComponent', () => {
  let fixture: ComponentFixture<ClientLoginComponent>;
  let authService: {
    login: ReturnType<typeof vi.fn>;
  };
  let router: Router;
  let navigateByUrlSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    authService = {
      login: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ClientLoginComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        provideRouter([]),
      ],
    }).compileComponents();

    router = TestBed.inject(Router);

    vi.spyOn(router, 'getCurrentNavigation').mockReturnValue(null);

    navigateByUrlSpy = vi
      .spyOn(router, 'navigateByUrl')
      .mockResolvedValue(true);

    fixture = TestBed.createComponent(ClientLoginComponent);
  });

  it('logs in with a pre-hashed password and navigates to the dashboard', async () => {
    authService.login.mockReturnValue(of(user));
    fillForm('user@example.com', 's3cret');

    await fixture.componentInstance.submit();

    expect(authService.login).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: await sha256Hex('s3cret'),
    });
    expect(navigateByUrlSpy).toHaveBeenCalledWith('/panel');
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
    fillForm('user@example.com', 'bad-password');

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
