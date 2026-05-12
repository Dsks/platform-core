import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiErrorMapper } from '@platformcore/shared-api';
import {
  PasswordMismatchErrorStateMatcher,
  passwordsMatchValidator,
  SessionStore,
  sha256Hex,
} from '@platformcore/shared-auth';
import {
  CreateUserRequest,
  PLATFORMCORE_ROLES,
  UpdateUserRequest,
  UserDetail,
  UsersRoleFilter,
} from '@platformcore/shared-models';
import { finalize, take } from 'rxjs';
import { UsersApiService } from './users-api.service';

type UserFormField = 'email' | 'password' | 'confirmPassword' | 'roles';

type RoleOption = {
  label: string;
  value: UsersRoleFilter;
};

@Component({
  selector: 'admin-user-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './user-form.component.html',
  styleUrl: './user-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly usersApiService = inject(UsersApiService);
  private readonly sessionStore = inject(SessionStore);
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly apiErrorMapper = inject(ApiErrorMapper);

  readonly form = this.formBuilder.group(
    {
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
      confirmPassword: ['', [Validators.required]],
      roles: [[PLATFORMCORE_ROLES.USER] as string[], [rolesRequiredValidator]],
      active: [true as boolean],
    },
    {
      validators: [passwordsMatchValidator],
    },
  );

  protected readonly loadingUser = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadErrorMessage = signal<string | null>(null);
  protected readonly saveErrorMessage = signal<string | null>(null);
  protected readonly user = signal<UserDetail | null>(null);
  protected readonly userId = signal<string | null>(null);
  protected readonly isEditMode = computed(() => this.userId() !== null);
  protected readonly title = computed(() =>
    this.isEditMode() ? 'Editar usuario' : 'Crear usuario',
  );
  protected readonly submitLabel = computed(() =>
    this.isEditMode() ? 'Guardar cambios' : 'Crear usuario',
  );
  protected readonly savingLabel = computed(() =>
    this.isEditMode() ? 'Guardando...' : 'Creando...',
  );
  protected readonly roleOptions = computed<RoleOption[]>(() => {
    const options: RoleOption[] = [
      {
        label: PLATFORMCORE_ROLES.USER,
        value: PLATFORMCORE_ROLES.USER,
      },
    ];

    if (this.sessionStore.currentUser()?.roles.includes(PLATFORMCORE_ROLES.SUPERADMIN)) {
      options.push({
        label: PLATFORMCORE_ROLES.ADMIN,
        value: PLATFORMCORE_ROLES.ADMIN,
      });
    }

    return options;
  });
  protected readonly confirmPasswordErrorStateMatcher =
    new PasswordMismatchErrorStateMatcher();

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.userId.set(id);

    if (id) {
      this.configureEditMode();
      this.loadUser(id);

      return;
    }

    this.configureCreateMode();
  }

  async submit(): Promise<void> {
    if (this.saving() || this.loadingUser()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();

      return;
    }

    const id = this.userId();

    if (id) {
      this.saveExistingUser(id);

      return;
    }

    await this.createUser();
  }

  protected isFieldInvalid(fieldName: UserFormField): boolean {
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

  private configureCreateMode(): void {
    this.form.controls.email.enable();
    this.form.controls.password.setValidators([Validators.required]);
    this.form.controls.confirmPassword.setValidators([Validators.required]);
    this.form.controls.roles.setValidators([rolesRequiredValidator]);
    this.form.controls.roles.setValue([PLATFORMCORE_ROLES.USER]);
    this.form.controls.active.setValue(true);
    this.form.controls.password.updateValueAndValidity();
    this.form.controls.confirmPassword.updateValueAndValidity();
    this.form.controls.roles.updateValueAndValidity();
  }

  private configureEditMode(): void {
    this.form.controls.email.enable();
    this.form.controls.password.clearValidators();
    this.form.controls.confirmPassword.clearValidators();
    this.form.controls.roles.clearValidators();
    this.form.controls.password.setValue('');
    this.form.controls.confirmPassword.setValue('');
    this.form.controls.password.updateValueAndValidity();
    this.form.controls.confirmPassword.updateValueAndValidity();
    this.form.controls.roles.updateValueAndValidity();
  }

  private loadUser(id: string): void {
    this.loadingUser.set(true);
    this.loadErrorMessage.set(null);
    this.saveErrorMessage.set(null);

    this.usersApiService
      .getUser(id)
      .pipe(
        take(1),
        finalize(() => {
          this.loadingUser.set(false);
        }),
      )
      .subscribe({
        next: (user) => {
          this.user.set(user);
          this.form.patchValue({
            email: user.email,
            roles: user.roles,
            active: user.active,
          });
        },
        error: (error: unknown) => {
          this.user.set(null);
          this.loadErrorMessage.set(this.toLoadErrorMessage(error));
        },
      });
  }

  private async createUser(): Promise<void> {
    this.saving.set(true);
    this.saveErrorMessage.set(null);

    let request: CreateUserRequest;

    try {
      request = {
        email: this.form.controls.email.value.trim(),
        password: await sha256Hex(this.form.controls.password.value),
        roles: this.form.controls.roles.value,
      };
    } catch (error) {
      this.saving.set(false);
      this.saveErrorMessage.set(this.toSaveErrorMessage(error));

      return;
    }

    this.usersApiService
      .createUser(request)
      .pipe(
        take(1),
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/usuarios');
        },
        error: (error: unknown) => {
          this.saveErrorMessage.set(this.toSaveErrorMessage(error));
        },
      });
  }

  private saveExistingUser(id: string): void {
    this.saving.set(true);
    this.saveErrorMessage.set(null);

    const request: UpdateUserRequest = {
      active: this.form.controls.active.value,
    };

    this.usersApiService
      .updateUser(id, request)
      .pipe(
        take(1),
        finalize(() => {
          this.saving.set(false);
        }),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/usuarios');
        },
        error: (error: unknown) => {
          this.saveErrorMessage.set(this.toSaveErrorMessage(error));
        },
      });
  }

  private toLoadErrorMessage(error: unknown): string {
    const problem = this.apiErrorMapper.map(error).problem;

    return (
      problem.detail ?? problem.title ?? 'No hemos podido cargar el usuario.'
    );
  }

  private toSaveErrorMessage(error: unknown): string {
    const problem = this.apiErrorMapper.map(error).problem;
    const message = this.translateSaveErrorMessage(
      problem.detail ?? problem.title,
    );

    return (
      message ?? 'No hemos podido guardar el usuario. Revisa los datos.'
    );
  }

  private translateSaveErrorMessage(
    message: string | null | undefined,
  ): string | null {
    if (!message) {
      return null;
    }

    const normalizedMessage = message.trim().toLowerCase();

    if (
      normalizedMessage === 'user_email_already_in_use' ||
      normalizedMessage === 'email already in use'
    ) {
      return 'Ya existe un usuario con este email.';
    }

    return message;
  }
}

function rolesRequiredValidator(
  control: AbstractControl,
): ValidationErrors | null {
  const value = control.value;

  return Array.isArray(value) && value.length > 0
    ? null
    : { rolesRequired: true };
}
