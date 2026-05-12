import {
  HttpErrorResponse,
  HttpResponse,
  HttpStatusCode,
} from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthApiService } from '@qomo/shared-api';
import { of, throwError } from 'rxjs';
import { ClientVerifyEmailComponent } from './client-verify-email.component';
import { EmailVerificationFlowStore } from './email-verification-flow.store';

describe('ClientVerifyEmailComponent', () => {
  let fixture: ComponentFixture<ClientVerifyEmailComponent>;
  let authApiService: {
    verifyEmail: ReturnType<typeof vi.fn>;
    resendVerification: ReturnType<typeof vi.fn>;
  };
  let emailVerificationFlowStore: EmailVerificationFlowStore;

  beforeEach(async () => {
    authApiService = {
      verifyEmail: vi.fn(),
      resendVerification: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ClientVerifyEmailComponent],
      providers: [
        provideRouter([]),
        { provide: AuthApiService, useValue: authApiService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ClientVerifyEmailComponent);
    emailVerificationFlowStore = TestBed.inject(EmailVerificationFlowStore);
    emailVerificationFlowStore.clear();
  });

  it('verifies email with a trimmed code and shows success on 204', () => {
    const navigateSpy = vi
      .spyOn(TestBed.inject(Router), 'navigate')
      .mockResolvedValue(true);
    authApiService.verifyEmail.mockReturnValue(
      of(new HttpResponse({ status: HttpStatusCode.NoContent })),
    );
    fixture.componentInstance.verifyForm.setValue({ code: ' 123456 ' });

    fixture.componentInstance.verifyEmail();
    fixture.detectChanges();

    expect(authApiService.verifyEmail).toHaveBeenCalledWith({
      code: '123456',
    });
    expect(navigateSpy).toHaveBeenCalledWith(['/iniciar-sesion'], {
      state: {
        snackBarMessage: 'Email verificado. Ya puedes iniciar sesión.',
      },
    });
  });

  it('shows a generic verify error on accepted non-success responses', () => {
    authApiService.verifyEmail.mockReturnValue(
      of(
        new HttpResponse({
          status: HttpStatusCode.Accepted,
          body: { message: 'Code Not Found' },
        }),
      ),
    );
    fixture.componentInstance.verifyForm.setValue({ code: '123456' });

    fixture.componentInstance.verifyEmail();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'No hemos podido verificar el código',
    );
    expect(fixture.nativeElement.textContent).not.toContain('Code Not Found');
  });

  it('does not verify when code is empty after trimming', () => {
    fixture.componentInstance.verifyForm.setValue({ code: '   ' });

    fixture.componentInstance.verifyEmail();

    expect(authApiService.verifyEmail).not.toHaveBeenCalled();
  });

  it('shows a readable verify error', () => {
    authApiService.verifyEmail.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            statusText: 'Bad Request',
            error: {
              detail: 'Codigo invalido.',
            },
          }),
      ),
    );
    fixture.componentInstance.verifyForm.setValue({ code: '123456' });

    fixture.componentInstance.verifyEmail();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Código de verificación incorrecto.',
    );
  });

  it('hides resend when no verification email is stored', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.resend-section')).toBeNull();
  });

  it('resends verification with the stored email and shows generic success', () => {
    emailVerificationFlowStore.setEmail('user@example.com');
    authApiService.resendVerification.mockReturnValue(
      of({
        message: 'Forwarded Code',
      }),
    );

    fixture.componentInstance.resendCode();
    fixture.detectChanges();

    expect(authApiService.resendVerification).toHaveBeenCalledWith({
      email: 'user@example.com',
    });
    expect(fixture.nativeElement.textContent).toContain(
      'Si podemos reenviar el código',
    );
  });

  it('does not resend during the cooldown', () => {
    emailVerificationFlowStore.setEmail('user@example.com');
    fixture.detectChanges();

    fixture.componentInstance.resendCode();

    expect(authApiService.resendVerification).not.toHaveBeenCalled();
  });
});
