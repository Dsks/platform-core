import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import {
  ActivatedRoute,
  convertToParamMap,
  Router,
} from '@angular/router';
import { SessionStore, sha256Hex } from '@qomo/shared-auth';
import type { CurrentUser, UserDetail } from '@qomo/shared-models';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { UserFormComponent } from './user-form.component';
import { UsersApiService } from './users-api.service';

type UsersApiServiceMock = {
  getUser: ReturnType<typeof vi.fn>;
  createUser: ReturnType<typeof vi.fn>;
  updateUser: ReturnType<typeof vi.fn>;
};

type RouterMock = {
  navigateByUrl: ReturnType<typeof vi.fn>;
  createUrlTree: ReturnType<typeof vi.fn>;
  serializeUrl: ReturnType<typeof vi.fn>;
  events: typeof EMPTY;
};

type FormSetup = {
  fixture: ComponentFixture<UserFormComponent>;
  usersApiService: UsersApiServiceMock;
  router: RouterMock;
};

const superAdminUser: CurrentUser = {
  id: 'admin-1',
  email: 'admin@qomo.test',
  active: true,
  emailVerified: true,
  roles: ['SUPERADMIN'],
};

const editableUser: UserDetail = {
  id: 'user-1',
  email: 'ana@qomo.test',
  active: false,
  emailVerified: true,
  roles: ['USER'],
  lastLogin: '2026-05-11T10:30:00.000Z',
  createdAt: '2026-05-01T08:00:00.000Z',
  updatedAt: '2026-05-10T12:00:00.000Z',
};

describe('UserFormComponent', () => {
  it('starts in create mode for the new-user route', async () => {
    const { fixture, usersApiService } = await setupForm();

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(usersApiService.getUser).not.toHaveBeenCalled();
    expect(compiled.textContent).toContain('Crear usuario');
    expect(
      compiled.querySelector<HTMLInputElement>('input[type="password"]'),
    ).not.toBeNull();
    expect(fixture.componentInstance.form.getRawValue()).toEqual({
      email: '',
      password: '',
      confirmPassword: '',
      roles: ['USER'],
      active: true,
    });
  });

  it('validates email, password, confirmation, and roles before creating', async () => {
    const { fixture, usersApiService } = await setupForm();

    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      email: 'not-an-email',
      password: '',
      confirmPassword: '',
      roles: [],
      active: true,
    });

    await fixture.componentInstance.submit();

    expect(fixture.componentInstance.form.invalid).toBe(true);
    expect(
      fixture.componentInstance.form.controls.email.hasError('email'),
    ).toBe(true);
    expect(
      fixture.componentInstance.form.controls.password.hasError('required'),
    ).toBe(true);
    expect(
      fixture.componentInstance.form.controls.confirmPassword.hasError(
        'required',
      ),
    ).toBe(true);
    expect(
      fixture.componentInstance.form.controls.roles.hasError('rolesRequired'),
    ).toBe(true);
    expect(fixture.componentInstance.form.controls.email.touched).toBe(true);
    expect(usersApiService.createUser).not.toHaveBeenCalled();

    fixture.componentInstance.form.patchValue({
      email: 'ana@qomo.test',
      password: 'one-password',
      confirmPassword: 'another-password',
      roles: ['USER'],
    });

    await fixture.componentInstance.submit();

    expect(
      fixture.componentInstance.form.hasError('passwordMismatch'),
    ).toBe(true);
    expect(usersApiService.createUser).not.toHaveBeenCalled();
  });

  it('creates users with email, hashed password, and selected roles', async () => {
    const { fixture, usersApiService, router } = await setupForm();

    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      email: 'ana@qomo.test',
      password: 's3cret',
      confirmPassword: 's3cret',
      roles: ['USER', 'ADMIN'],
      active: true,
    });

    await fixture.componentInstance.submit();

    expect(usersApiService.createUser).toHaveBeenCalledWith({
      email: 'ana@qomo.test',
      password: await sha256Hex('s3cret'),
      roles: ['USER', 'ADMIN'],
    });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/usuarios');
  });

  it('loads an existing user in edit mode and renders readonly fields', async () => {
    const { fixture, usersApiService } = await setupForm('user-1');

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const emailInput = compiled.querySelector<HTMLInputElement>(
      'input[formcontrolname="email"]',
    );

    expect(usersApiService.getUser).toHaveBeenCalledWith('user-1');
    expect(fixture.componentInstance.form.controls.email.value).toBe(
      editableUser.email,
    );
    expect(fixture.componentInstance.form.controls.roles.value).toEqual(
      editableUser.roles,
    );
    expect(fixture.componentInstance.form.controls.active.value).toBe(false);
    expect(emailInput?.readOnly).toBe(true);
    expect(
      compiled.querySelector<HTMLInputElement>('input[type="password"]'),
    ).toBeNull();
    expect(
      compiled.querySelector('mat-select[formcontrolname="roles"]'),
    ).toBeNull();
    expect(
      compiled.querySelector('mat-checkbox[formcontrolname="active"]'),
    ).not.toBeNull();
    expect(compiled.textContent).toContain('USER');
    expect(compiled.textContent).toContain('Editar usuario');
  });

  it('updates only the active flag in edit mode', async () => {
    const { fixture, usersApiService, router } = await setupForm('user-1');

    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({
      email: 'changed@qomo.test',
      password: 'ignored-password',
      confirmPassword: 'ignored-password',
      roles: ['ADMIN'],
      active: true,
    });

    await fixture.componentInstance.submit();

    expect(usersApiService.updateUser).toHaveBeenCalledWith('user-1', {
      active: true,
    });
    expect(usersApiService.updateUser.mock.calls[0]?.[1]).toEqual({
      active: true,
    });
    expect(usersApiService.createUser).not.toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/usuarios');
  });

  it('shows load errors inline and does not render the editable form', async () => {
    const { fixture, usersApiService } = await setupForm('missing-user');
    usersApiService.getUser.mockReturnValue(
      throwError(() => ({ detail: 'Could not load user.' })),
    );

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(alertText(fixture)).toContain('Could not load user.');
    expect(compiled.querySelector('form.user-form')).toBeNull();
  });

  it('shows create errors inline', async () => {
    const { fixture, usersApiService } = await setupForm();
    usersApiService.createUser.mockReturnValue(
      throwError(() => ({ detail: 'email already in use' })),
    );

    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      email: 'ana@qomo.test',
      password: 's3cret',
      confirmPassword: 's3cret',
      roles: ['USER'],
      active: true,
    });

    await fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(alertText(fixture)).toContain(
      'Ya existe un usuario con este email.',
    );
  });

  it('shows update errors inline', async () => {
    const { fixture, usersApiService } = await setupForm('user-1');
    usersApiService.updateUser.mockReturnValue(
      throwError(() => ({ detail: 'Could not save user.' })),
    );

    fixture.detectChanges();
    fixture.componentInstance.form.controls.active.setValue(true);

    await fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(alertText(fixture)).toContain('Could not save user.');
  });
});

async function setupForm(id: string | null = null): Promise<FormSetup> {
  const usersApiService: UsersApiServiceMock = {
    getUser: vi.fn((): Observable<UserDetail> => of(editableUser)),
    createUser: vi.fn(() => of(undefined)),
    updateUser: vi.fn((): Observable<UserDetail> => of(editableUser)),
  };
  const router: RouterMock = {
    navigateByUrl: vi.fn(),
    createUrlTree: vi.fn(() => ({})),
    serializeUrl: vi.fn(() => '/usuarios'),
    events: EMPTY,
  };

  await TestBed.configureTestingModule({
    imports: [UserFormComponent],
    providers: [
      provideNoopAnimations(),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            paramMap: convertToParamMap(id ? { id } : {}),
          },
        },
      },
      { provide: UsersApiService, useValue: usersApiService },
      { provide: Router, useValue: router },
    ],
  }).compileComponents();

  TestBed.inject(SessionStore).setAuthenticated(superAdminUser);

  return {
    fixture: TestBed.createComponent(UserFormComponent),
    usersApiService,
    router,
  };
}

function alertText(fixture: ComponentFixture<UserFormComponent>): string {
  return (
    (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')
      ?.textContent ?? ''
  );
}
