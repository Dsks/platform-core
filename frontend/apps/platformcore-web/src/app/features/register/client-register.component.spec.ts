import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthApiService } from '@platformcore/shared-api';
import { sha256Hex } from '@platformcore/shared-auth';
import { of, throwError } from 'rxjs';
import { ClientRegisterComponent } from './client-register.component';
import { EmailVerificationFlowStore } from '../verify-email/email-verification-flow.store';

describe('ClientRegisterComponent', () => {
  let fixture: ComponentFixture<ClientRegisterComponent>;
  let authApiService: {
    register: ReturnType<typeof vi.fn>;
  };
  let router: Router;
  let navigateSpy: ReturnType<typeof vi.spyOn>;
  let emailVerificationFlowStore: EmailVerificationFlowStore;

  beforeEach(async () => {
    authApiService = {
      register: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ClientRegisterComponent],
      providers: [
        { provide: AuthApiService, useValue: authApiService },
        provideRouter([]),
      ],
    }).compileComponents();

    router = TestBed.inject(Router);

    vi.spyOn(router, 'getCurrentNavigation').mockReturnValue(null);

    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(ClientRegisterComponent);
    emailVerificationFlowStore = TestBed.inject(EmailVerificationFlowStore);
    emailVerificationFlowStore.clear();
  });

  it('registers with a pre-hashed password and navigates to email verification', async () => {
    authApiService.register.mockReturnValue(
      of({
        requestId: 'req-123',
        status: 'VERIFICATION_REQUIRED',
        message: "If the email is valid, you'll receive next steps.",
      }),
    );
    fillForm('user@example.com', 's3cret', 's3cret');

    await fixture.componentInstance.submit();

    expect(authApiService.register).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: await sha256Hex('s3cret'),
    });
    expect(emailVerificationFlowStore.email()).toBe('user@example.com');
    expect(navigateSpy).toHaveBeenCalledWith(['/verificar-cuenta']);
  });

  it('shows an already registered message and does not navigate on conflict', async () => {
    authApiService.register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            statusText: 'Conflict',
            error: {
              requestId: 'req-registered',
              status: 'ALREADY_REGISTERED',
              message: 'Account already registered. Please sign in.',
            },
          }),
      ),
    );
    fillForm('user@example.com', 's3cret', 's3cret');

    await fixture.componentInstance.submit();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector(
      '.register-error',
    ) as HTMLElement | null;

    expect(router.navigate).not.toHaveBeenCalled();
    expect(emailVerificationFlowStore.email()).toBeNull();
    expect(error?.textContent).toContain(
      'Ya existe una cuenta con este email. Inicia sesión.',
    );
  });

  it('does not submit when passwords do not match', async () => {
    fillForm('user@example.com', 's3cret', 'different');

    await fixture.componentInstance.submit();

    expect(authApiService.register).not.toHaveBeenCalled();
  });

  it('shows a readable register error', async () => {
    authApiService.register.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            statusText: 'Bad Request',
            error: {
              detail: 'Datos de registro invalidos.',
            },
          }),
      ),
    );
    fillForm('user@example.com', 's3cret', 's3cret');

    await fixture.componentInstance.submit();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector(
      '.register-error',
    ) as HTMLElement | null;

    expect(error?.textContent).toContain('Datos de registro inválidos.');
  });

  function fillForm(
    email: string,
    password: string,
    confirmPassword: string,
  ): void {
    fixture.componentInstance.form.setValue({
      email,
      password,
      confirmPassword,
    });
  }
});
