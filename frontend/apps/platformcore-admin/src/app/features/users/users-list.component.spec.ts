import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import type { PageEvent } from '@angular/material/paginator';
import type { Sort } from '@angular/material/sort';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import type {
  UserSummary,
  UsersListQuery,
  UsersPage,
} from '@platformcore/shared-models';
import { Observable, of, throwError } from 'rxjs';
import { UsersApiService } from './users-api.service';
import { UsersListComponent } from './users-list.component';

type UsersApiServiceMock = {
  listUsers: ReturnType<typeof vi.fn>;
  deleteUser: ReturnType<typeof vi.fn>;
};

type RouterMock = {
  navigateByUrl: ReturnType<typeof vi.fn>;
};

type MatDialogMock = {
  open: ReturnType<typeof vi.fn>;
};

type UsersListHarness = {
  onPageChange(event: PageEvent): void;
  onSearchChange(value: string): void;
  onDeletedFilterChange(value: 'false' | 'true' | 'all'): void;
  onActiveFilterChange(value: boolean | null): void;
  onVerifiedFilterChange(value: boolean | null): void;
  onRoleFilterChange(value: 'USER' | 'ADMIN' | 'SUPERADMIN' | null): void;
  onSortChange(event: Sort): void;
};

const user: UserSummary = {
  id: 'user-1',
  email: 'ana@platformcore.test',
  active: true,
  emailVerified: true,
  roles: ['USER'],
  lastLogin: '2026-05-11T10:30:00.000Z',
  createdAt: '2026-05-01T08:00:00.000Z',
  updatedAt: '2026-05-10T12:00:00.000Z',
  deletedAt: null,
};

const secondUser: UserSummary = {
  id: 'user-2',
  email: 'beto@platformcore.test',
  active: false,
  emailVerified: false,
  roles: ['ADMIN'],
  lastLogin: null,
  createdAt: '2026-04-20T08:00:00.000Z',
  updatedAt: '2026-05-02T12:00:00.000Z',
  deletedAt: null,
};

const initialQuery: UsersListQuery = {
  search: '',
  deleted: 'false',
  active: null,
  verified: null,
  role: null,
  sortBy: 'createdAt',
  sortDirection: 'desc',
};

describe('UsersListComponent', () => {
  let fixture: ComponentFixture<UsersListComponent>;
  let usersApiService: UsersApiServiceMock;
  let router: RouterMock;
  let dialog: MatDialogMock;

  beforeEach(async () => {
    usersApiService = {
      listUsers: vi.fn(
        (page: number, size: number): Observable<UsersPage> =>
          of(makeUsersPage([user, secondUser], page, size)),
      ),
      deleteUser: vi.fn(() => of(undefined)),
    };
    router = {
      navigateByUrl: vi.fn(),
    };
    dialog = {
      open: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [UsersListComponent],
      providers: [
        provideNoopAnimations(),
        { provide: UsersApiService, useValue: usersApiService },
        { provide: Router, useValue: router },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(UsersListComponent);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads the first page with the current default query', () => {
    fixture.detectChanges();

    expect(usersApiService.listUsers).toHaveBeenCalledWith(0, 10, initialQuery);
  });

  it('keeps filters and sort when changing pagination', () => {
    fixture.detectChanges();
    const component = asHarness(fixture);

    component.onDeletedFilterChange('all');
    component.onActiveFilterChange(true);
    component.onVerifiedFilterChange(false);
    component.onRoleFilterChange('ADMIN');
    component.onSortChange({ active: 'email', direction: 'asc' });
    usersApiService.listUsers.mockClear();

    component.onPageChange({
      pageIndex: 2,
      pageSize: 50,
      length: 200,
      previousPageIndex: 0,
    });

    expect(usersApiService.listUsers).toHaveBeenCalledWith(2, 50, {
      search: '',
      deleted: 'all',
      active: true,
      verified: false,
      role: 'ADMIN',
      sortBy: 'email',
      sortDirection: 'asc',
    });
  });

  it('resets to the first page when filters change', () => {
    fixture.detectChanges();
    const component = asHarness(fixture);

    moveToLaterPage(component);
    usersApiService.listUsers.mockClear();

    component.onDeletedFilterChange('true');

    expect(usersApiService.listUsers).toHaveBeenLastCalledWith(0, 50, {
      ...initialQuery,
      deleted: 'true',
    });

    moveToLaterPage(component);
    usersApiService.listUsers.mockClear();

    component.onActiveFilterChange(false);

    expect(usersApiService.listUsers).toHaveBeenLastCalledWith(0, 50, {
      ...initialQuery,
      deleted: 'true',
      active: false,
    });

    moveToLaterPage(component);
    usersApiService.listUsers.mockClear();

    component.onVerifiedFilterChange(true);

    expect(usersApiService.listUsers).toHaveBeenLastCalledWith(0, 50, {
      ...initialQuery,
      deleted: 'true',
      active: false,
      verified: true,
    });

    moveToLaterPage(component);
    usersApiService.listUsers.mockClear();

    component.onRoleFilterChange('SUPERADMIN');

    expect(usersApiService.listUsers).toHaveBeenLastCalledWith(0, 50, {
      ...initialQuery,
      deleted: 'true',
      active: false,
      verified: true,
      role: 'SUPERADMIN',
    });
  });

  it('debounces trimmed search changes and skips unchanged applied searches', () => {
    vi.useFakeTimers();
    fixture.detectChanges();
    const component = asHarness(fixture);

    moveToLaterPage(component);
    usersApiService.listUsers.mockClear();

    component.onSearchChange('  ana@platformcore.test  ');

    expect(usersApiService.listUsers).not.toHaveBeenCalled();

    vi.advanceTimersByTime(299);

    expect(usersApiService.listUsers).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);

    expect(usersApiService.listUsers).toHaveBeenCalledWith(0, 50, {
      ...initialQuery,
      search: 'ana@platformcore.test',
    });

    usersApiService.listUsers.mockClear();

    component.onSearchChange('ana@platformcore.test');
    vi.advanceTimersByTime(300);

    expect(usersApiService.listUsers).not.toHaveBeenCalled();
  });

  it('resets to the first page when sorting and maps table columns to API sort fields', () => {
    fixture.detectChanges();
    const component = asHarness(fixture);

    moveToLaterPage(component);
    usersApiService.listUsers.mockClear();

    component.onSortChange({ active: 'roles', direction: '' });

    expect(usersApiService.listUsers).toHaveBeenCalledWith(0, 50, {
      ...initialQuery,
      sortBy: 'role',
      sortDirection: 'desc',
    });
  });

  it('navigates to create and edit routes from the action buttons', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.create-user-button')?.click();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/usuarios/nuevo');

    compiled.querySelector<HTMLButtonElement>('.edit-btn')?.click();

    expect(router.navigateByUrl).toHaveBeenCalledWith(
      `/usuarios/${user.id}/editar`,
    );
  });

  it('confirms before deleting and reloads the current page after deletion', () => {
    dialog.open.mockReturnValue({
      afterClosed: () => of(true),
    });
    fixture.detectChanges();
    const component = asHarness(fixture);

    component.onPageChange({
      pageIndex: 1,
      pageSize: 20,
      length: 100,
      previousPageIndex: 0,
    });
    usersApiService.listUsers.mockClear();

    (
      fixture.nativeElement as HTMLElement
    ).querySelector<HTMLButtonElement>('.delete-btn')?.click();

    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(usersApiService.deleteUser).toHaveBeenCalledWith(user.id);
    expect(usersApiService.listUsers).toHaveBeenCalledWith(1, 20, initialQuery);
  });

  it('does not delete when the confirmation dialog is cancelled', () => {
    dialog.open.mockReturnValue({
      afterClosed: () => of(false),
    });
    fixture.detectChanges();

    (
      fixture.nativeElement as HTMLElement
    ).querySelector<HTMLButtonElement>('.delete-btn')?.click();

    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(usersApiService.deleteUser).not.toHaveBeenCalled();
  });

  it('shows load errors inline', () => {
    usersApiService.listUsers.mockReturnValue(
      throwError(() => ({ detail: 'Could not load users.' })),
    );

    fixture.detectChanges();

    expect(alertText(fixture)).toContain('Could not load users.');
  });

  it('shows delete errors inline while keeping the loaded table data', () => {
    dialog.open.mockReturnValue({
      afterClosed: () => of(true),
    });
    usersApiService.deleteUser.mockReturnValue(
      throwError(() => ({ detail: 'Could not delete user.' })),
    );
    fixture.detectChanges();

    (
      fixture.nativeElement as HTMLElement
    ).querySelector<HTMLButtonElement>('.delete-btn')?.click();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(alertText(fixture)).toContain('Could not delete user.');
    expect(compiled.textContent).toContain(user.email);
  });
});

function makeUsersPage(
  content: UserSummary[],
  page: number,
  size: number,
): UsersPage {
  return {
    content,
    page,
    size,
    totalElements: content.length,
    totalPages: 1,
  };
}

function asHarness(
  fixture: ComponentFixture<UsersListComponent>,
): UsersListHarness {
  return fixture.componentInstance as unknown as UsersListHarness;
}

function moveToLaterPage(component: UsersListHarness): void {
  component.onPageChange({
    pageIndex: 3,
    pageSize: 50,
    length: 200,
    previousPageIndex: 0,
  });
}

function alertText(fixture: ComponentFixture<UsersListComponent>): string {
  return (
    (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')
      ?.textContent ?? ''
  );
}
