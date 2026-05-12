import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { AuthApiService } from '@platformcore/shared-api';
import {
  PasswordMismatchErrorStateMatcher,
  passwordsMatchValidator,
  sha256Hex,
  toRegisterErrorMessage,
} from '@platformcore/shared-auth';
import type { RegisterRequest } from '@platformcore/shared-models';
import { finalize, take } from 'rxjs';
import { EmailVerificationFlowStore } from '../verify-email/email-verification-flow.store';

type ClientRegisterFormField = 'email' | 'password' | 'confirmPassword';

const ALREADY_REGISTERED_MESSAGE =
  'Ya existe una cuenta con este email. Inicia sesión.';

@Component({
  selector: 'app-client-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './client-register.component.html',
  styleUrl: './client-register.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientRegisterComponent {
  private readonly authApiService = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly emailVerificationFlowStore = inject(
    EmailVerificationFlowStore,
  );

  readonly form = this.formBuilder.group(
    {
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
      confirmPassword: ['', [Validators.required]],
    },
    {
      validators: [passwordsMatchValidator],
    },
  );

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly confirmPasswordErrorStateMatcher =
    new PasswordMismatchErrorStateMatcher();

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();

      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.emailVerificationFlowStore.clear();

    let request: RegisterRequest;

    try {
      request = {
        email: this.form.controls.email.value.trim(),
        password: await sha256Hex(this.form.controls.password.value),
      };
    } catch (error) {
      this.loading.set(false);
      this.errorMessage.set(toRegisterErrorMessage(error));

      return;
    }

    this.authApiService
      .register(request)
      .pipe(
        take(1),
        finalize(() => {
          this.loading.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          if (response.status === 'VERIFICATION_REQUIRED') {
            this.emailVerificationFlowStore.setEmail(request.email);
            void this.router.navigate(['/verificar-cuenta']);

            return;
          }

          if (response.status === 'ALREADY_REGISTERED') {
            this.errorMessage.set(ALREADY_REGISTERED_MESSAGE);

            return;
          }

          this.errorMessage.set(toRegisterErrorMessage(null));
        },
        error: (error: unknown) => {
          this.errorMessage.set(toRegisterErrorMessage(error));
        },
      });
  }

  protected isFieldInvalid(fieldName: ClientRegisterFormField): boolean {
    const field = this.form.controls[fieldName];

    return field.invalid && (field.dirty || field.touched);
  }

  protected isPasswordMismatch(): boolean {
    const confirmPassword = this.form.controls.confirmPassword;

    return (
      this.form.hasError('passwordMismatch') &&
      (confirmPassword.dirty || confirmPassword.touched)
    );
  }
}
