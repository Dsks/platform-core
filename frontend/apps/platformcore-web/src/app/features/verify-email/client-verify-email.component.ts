import { isPlatformBrowser } from '@angular/common';
import { HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { ApiErrorMapper, AuthApiService } from '@platformcore/shared-api';
import {
  ResendVerificationRequest,
  VerifyEmailRequest,
} from '@platformcore/shared-models';
import { finalize, take } from 'rxjs';
import { EmailVerificationFlowStore } from './email-verification-flow.store';

const RESEND_COOLDOWN_SECONDS = 60;
const VERIFY_ERROR_MESSAGE =
  'No hemos podido verificar el código. Revisa el código e inténtalo de nuevo.';

@Component({
  selector: 'app-client-verify-email',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './client-verify-email.component.html',
  styleUrl: './client-verify-email.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientVerifyEmailComponent implements OnInit, OnDestroy {
  private readonly authApiService = inject(AuthApiService);
  private readonly apiErrorMapper = inject(ApiErrorMapper);
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly emailVerificationFlowStore = inject(
    EmailVerificationFlowStore,
  );
  private cooldownTimer: ReturnType<typeof setInterval> | null = null;

  readonly verifyForm = this.formBuilder.group({
    code: ['', [Validators.required]],
  });

  protected readonly verificationEmail = this.emailVerificationFlowStore.email;
  protected readonly verifying = signal(false);
  protected readonly resending = signal(false);
  protected readonly resendCooldown = signal(0);
  protected readonly verifyErrorMessage = signal<string | null>(null);
  protected readonly resendErrorMessage = signal<string | null>(null);
  protected readonly resendSuccessMessage = signal<string | null>(null);

  ngOnInit(): void {
    if (this.verificationEmail()) {
      this.startResendCooldown();
    }
  }

  ngOnDestroy(): void {
    this.clearResendCooldownTimer();
  }

  verifyEmail(): void {
    const code = this.verifyForm.controls.code.value.trim();
    this.verifyForm.controls.code.setValue(code);

    if (this.verifyForm.invalid || !code) {
      this.verifyForm.markAllAsTouched();

      return;
    }

    const request: VerifyEmailRequest = { code };

    this.verifying.set(true);
    this.verifyErrorMessage.set(null);

    this.authApiService
      .verifyEmail(request)
      .pipe(
        take(1),
        finalize(() => {
          this.verifying.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          if (response.status === HttpStatusCode.NoContent) {
            void this.router.navigate(['/iniciar-sesion'], {
              state: {
                snackBarMessage: 'Email verificado. Ya puedes iniciar sesión.',
              },
            });

            return;
          }

          this.verifyErrorMessage.set(VERIFY_ERROR_MESSAGE);
        },
        error: (error: unknown) => {
          this.verifyErrorMessage.set(this.toVerifyErrorMessage(error));
        },
      });
  }

  resendCode(): void {
    const email = this.verificationEmail();
    if (!email || this.resendCooldown() > 0 || this.resending()) {
      return;
    }

    const request: ResendVerificationRequest = { email };

    this.resending.set(true);
    this.resendErrorMessage.set(null);
    this.resendSuccessMessage.set(null);

    this.authApiService
      .resendVerification(request)
      .pipe(
        take(1),
        finalize(() => {
          this.resending.set(false);
        }),
      )
      .subscribe({
        next: () => {
          this.resendSuccessMessage.set(
            'Si podemos reenviar el código, lo recibirás en tu email.',
          );
          this.startResendCooldown();
        },
        error: () => {
          this.resendErrorMessage.set(
            'No hemos podido reenviar el código. Inténtalo de nuevo en unos minutos.',
          );
        },
      });
  }

  protected isCodeInvalid(): boolean {
    const code = this.verifyForm.controls.code;

    return code.invalid && (code.dirty || code.touched);
  }

  private toVerifyErrorMessage(error: unknown): string {
    const problem = this.apiErrorMapper.map(error).problem;
    const message = this.translateVerifyErrorMessage(
      problem.detail ?? problem.title,
    );

    return message ?? VERIFY_ERROR_MESSAGE;
  }

  private translateVerifyErrorMessage(
    message: string | null | undefined,
  ): string | null {
    if (!message) {
      return null;
    }

    const normalizedMessage = message.trim().toLowerCase();

    if (
      normalizedMessage === 'email already verified' ||
      normalizedMessage === 'email_already_verified'
    ) {
      return 'Esta cuenta ya está verificada. Puedes iniciar sesión.';
    }

    if (
      normalizedMessage === 'invalid verification code' ||
      normalizedMessage === 'invalid_code' ||
      normalizedMessage === 'verification code invalid' ||
      normalizedMessage === 'codigo invalido.' ||
      normalizedMessage === 'código inválido.'
    ) {
      return 'Código de verificación incorrecto.';
    }

    return message;
  }

  private startResendCooldown(): void {
    this.clearResendCooldownTimer();
    this.resendCooldown.set(RESEND_COOLDOWN_SECONDS);

    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.cooldownTimer = setInterval(() => {
      const nextValue = Math.max(this.resendCooldown() - 1, 0);
      this.resendCooldown.set(nextValue);

      if (nextValue === 0) {
        this.clearResendCooldownTimer();
      }
    }, 1000);
  }

  private clearResendCooldownTimer(): void {
    if (this.cooldownTimer === null) {
      return;
    }

    clearInterval(this.cooldownTimer);
    this.cooldownTimer = null;
  }
}
