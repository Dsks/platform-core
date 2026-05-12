import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import {
  AUTH_EXTERNAL_REDIRECT,
  AuthService,
  sha256Hex,
  toLoginErrorMessage,
} from '@qomo/shared-auth';
import { CurrentUser, QOMO_ROLES } from '@qomo/shared-models';
import { finalize, take } from 'rxjs';

type AdminLoginFormField = 'email' | 'password';

@Component({
  selector: 'admin-login',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './admin-login.component.html',
  styleUrl: './admin-login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminLoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly externalRedirect = inject(AUTH_EXTERNAL_REDIRECT);

  readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();

      return;
    }

    const formValue = this.form.getRawValue();

    this.loading.set(true);
    this.errorMessage.set(null);

    let passwordHash: string;

    try {
      passwordHash = await sha256Hex(formValue.password);
    } catch (error) {
      this.loading.set(false);
      this.errorMessage.set(toLoginErrorMessage(error));

      return;
    }

    this.authService
      .login({
        ...formValue,
        password: passwordHash,
      })
      .pipe(
        take(1),
        finalize(() => {
          this.loading.set(false);
        }),
      )
      .subscribe({
        next: (user) => {
          this.handleAuthenticatedUser(user);
        },
        error: (error: unknown) => {
          this.errorMessage.set(toLoginErrorMessage(error));
        },
      });
  }

  protected isFieldInvalid(fieldName: AdminLoginFormField): boolean {
    const field = this.form.controls[fieldName];

    return field.invalid && (field.dirty || field.touched);
  }

  private handleAuthenticatedUser(user: CurrentUser): void {
    if (this.isAdminUser(user)) {
      void this.router.navigateByUrl('/panel');

      return;
    }

    this.externalRedirect('/panel');
  }

  private isAdminUser(user: CurrentUser): boolean {
    return (
      user.roles.includes(QOMO_ROLES.ADMIN) ||
      user.roles.includes(QOMO_ROLES.SUPERADMIN)
    );
  }
}
