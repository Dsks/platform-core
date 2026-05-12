import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import {
  AuthService,
  sha256Hex,
  toLoginErrorMessage,
} from '@qomo/shared-auth';
import { finalize, take } from 'rxjs';

type ClientLoginFormField = 'email' | 'password';

@Component({
  selector: 'app-client-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './client-login.component.html',
  styleUrl: './client-login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientLoginComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly snackBar = inject(MatSnackBar);
  private readonly navigationSnackBarMessage =
    this.router.getCurrentNavigation()?.extras.state?.['snackBarMessage'];

  readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const snackBarMessage = this.navigationSnackBarMessage;

    if (typeof snackBarMessage !== 'string' || snackBarMessage.length === 0) {
      return;
    }

    this.snackBar.open(snackBarMessage, undefined, {
      duration: 4000,
      panelClass: ['snackbar-success'],
    });
  }

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
        next: () => {
          void this.router.navigateByUrl('/panel');
        },
        error: (error: unknown) => {
          this.errorMessage.set(toLoginErrorMessage(error));
        },
      });
  }

  protected isFieldInvalid(fieldName: ClientLoginFormField): boolean {
    const field = this.form.controls[fieldName];

    return field.invalid && (field.dirty || field.touched);
  }
}
