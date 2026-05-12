import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { ApiErrorMapper } from '@qomo/shared-api';
import {
  UserSummary,
  UsersDeletedFilter,
  UsersListFilters,
  UsersRoleFilter,
  UsersSortBy,
  UsersSortDirection,
} from '@qomo/shared-models';
import { finalize, take } from 'rxjs';
import { UsersApiService } from './users-api.service';
import { MatTooltipModule } from '@angular/material/tooltip';

type FilterOption<T> = {
  label: string;
  value: T;
};

type LoadPageOptions = {
  fallbackToPreviousIfEmpty?: boolean;
};

type ConfirmDeleteUserDialogData = {
  email: string;
};

@Component({
  selector: 'admin-confirm-delete-user-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Eliminar usuario</h2>
    <mat-dialog-content>
      <p>
        Esta accion eliminara y anonimizara el usuario
        <strong>{{ data.email }}</strong
        >.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button
        mat-flat-button
        color="warn"
        type="button"
        [mat-dialog-close]="true"
      >
        Eliminar
      </button>
    </mat-dialog-actions>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class ConfirmDeleteUserDialogComponent {
  protected readonly data =
    inject<ConfirmDeleteUserDialogData>(MAT_DIALOG_DATA);
}

@Component({
  selector: 'admin-users-list',
  imports: [
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatPaginatorModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './users-list.component.html',
  styleUrl: './users-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersListComponent implements OnInit, OnDestroy {
  private readonly usersApiService = inject(UsersApiService);
  private readonly router = inject(Router);
  private readonly apiErrorMapper = inject(ApiErrorMapper);
  private readonly dialog = inject(MatDialog);

  protected readonly displayedColumns = [
    'email',
    'active',
    'emailVerified',
    'roles',
    'lastLogin',
    'createdAt',
    'updatedAt',
    'deletedAt',
    'actions',
  ];
  protected readonly pageSizeOptions = [10, 20, 50, 100];
  protected readonly users = signal<UserSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly deleteErrorMessage = signal<string | null>(null);
  protected readonly deletingUserId = signal<string | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);
  protected readonly totalElements = signal(0);
  protected readonly searchTerm = signal('');
  protected readonly deletedFilter = signal<UsersDeletedFilter>('false');
  protected readonly activeFilter = signal<boolean | null>(null);
  protected readonly verifiedFilter = signal<boolean | null>(null);
  protected readonly roleFilter = signal<UsersRoleFilter | null>(null);
  protected readonly sortBy = signal<UsersSortBy>('createdAt');
  protected readonly sortDirection = signal<UsersSortDirection>('desc');
  protected readonly sortActive = computed(() =>
    this.sortBy() === 'role' ? 'roles' : this.sortBy(),
  );
  protected readonly deletedFilterOptions: FilterOption<UsersDeletedFilter>[] =
    [
      { label: 'No borrados', value: 'false' },
      { label: 'Borrados', value: 'true' },
      { label: 'Todos', value: 'all' },
    ];
  protected readonly activeFilterOptions: FilterOption<boolean | null>[] = [
    { label: 'Todos', value: null },
    { label: 'Activos', value: true },
    { label: 'Inactivos', value: false },
  ];
  protected readonly verifiedFilterOptions: FilterOption<boolean | null>[] = [
    { label: 'Todos', value: null },
    { label: 'Verificados', value: true },
    { label: 'No verificados', value: false },
  ];
  protected readonly roleFilterOptions: FilterOption<UsersRoleFilter | null>[] =
    [
      { label: 'Todos', value: null },
      { label: 'USER', value: 'USER' },
      { label: 'ADMIN', value: 'ADMIN' },
      { label: 'SUPERADMIN', value: 'SUPERADMIN' },
    ];
  private latestRequestId = 0;
  private appliedSearchQuery = '';
  private searchDebounceId: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.loadPage(this.pageIndex(), this.pageSize());
  }

  ngOnDestroy(): void {
    if (this.searchDebounceId !== null) {
      clearTimeout(this.searchDebounceId);
    }
  }

  protected onPageChange(event: PageEvent): void {
    this.loadPage(event.pageIndex, event.pageSize);
  }

  protected onSearchChange(value: string): void {
    this.searchTerm.set(value);

    if (this.searchDebounceId !== null) {
      clearTimeout(this.searchDebounceId);
    }

    this.searchDebounceId = setTimeout(() => {
      this.searchDebounceId = null;
      this.applySearch();
    }, 300);
  }

  protected onDeletedFilterChange(value: UsersDeletedFilter): void {
    this.deletedFilter.set(value);
    this.loadFirstPage();
  }

  protected onActiveFilterChange(value: boolean | null): void {
    this.activeFilter.set(value);
    this.loadFirstPage();
  }

  protected onVerifiedFilterChange(value: boolean | null): void {
    this.verifiedFilter.set(value);
    this.loadFirstPage();
  }

  protected onRoleFilterChange(value: UsersRoleFilter | null): void {
    this.roleFilter.set(value);
    this.loadFirstPage();
  }

  protected onSortChange(event: Sort): void {
    const sortBy = this.toUsersSortBy(event.active);

    if (!sortBy) {
      return;
    }

    this.sortBy.set(sortBy);
    this.sortDirection.set(event.direction || 'desc');
    this.loadFirstPage();
  }

  protected createUser(): void {
    void this.router.navigateByUrl('/usuarios/nuevo');
  }

  protected editUser(user: UserSummary): void {
    void this.router.navigateByUrl(`/usuarios/${user.id}/editar`);
  }

  protected deleteUser(user: UserSummary): void {
    if (user.deletedAt || this.deletingUserId()) {
      return;
    }

    this.dialog
      .open<
        ConfirmDeleteUserDialogComponent,
        ConfirmDeleteUserDialogData,
        boolean
      >(ConfirmDeleteUserDialogComponent, {
        data: {
          email: user.email,
        },
      })
      .afterClosed()
      .pipe(take(1))
      .subscribe((confirmed) => {
        if (confirmed === true) {
          this.confirmDeleteUser(user);
        }
      });
  }

  private loadFirstPage(): void {
    this.pageIndex.set(0);
    this.loadPage(0, this.pageSize());
  }

  private loadPage(
    page: number,
    size: number,
    options: LoadPageOptions = {},
  ): void {
    const requestId = ++this.latestRequestId;

    this.appliedSearchQuery = this.currentSearchQuery();
    this.loading.set(true);
    this.errorMessage.set(null);

    this.usersApiService
      .listUsers(page, size, {
        ...this.currentFilters(),
        sortBy: this.sortBy(),
        sortDirection: this.sortDirection(),
      })
      .pipe(
        take(1),
        finalize(() => {
          if (this.latestRequestId === requestId) {
            this.loading.set(false);
          }
        }),
      )
      .subscribe({
        next: (response) => {
          if (this.latestRequestId !== requestId) {
            return;
          }

          if (
            options.fallbackToPreviousIfEmpty &&
            response.content.length === 0 &&
            page > 0
          ) {
            this.loadPage(page - 1, response.size);

            return;
          }

          this.users.set(response.content);
          this.pageIndex.set(response.page);
          this.pageSize.set(response.size);
          this.totalElements.set(response.totalElements);
        },
        error: (error: unknown) => {
          if (this.latestRequestId !== requestId) {
            return;
          }

          this.users.set([]);
          this.pageIndex.set(page);
          this.pageSize.set(size);
          this.totalElements.set(0);
          this.errorMessage.set(this.toLoadErrorMessage(error));
        },
      });
  }

  private confirmDeleteUser(user: UserSummary): void {
    this.deleteErrorMessage.set(null);
    this.deletingUserId.set(user.id);

    this.usersApiService
      .deleteUser(user.id)
      .pipe(
        take(1),
        finalize(() => {
          this.deletingUserId.set(null);
        }),
      )
      .subscribe({
        next: () => {
          this.loadPage(this.pageIndex(), this.pageSize(), {
            fallbackToPreviousIfEmpty: true,
          });
        },
        error: (error: unknown) => {
          this.deleteErrorMessage.set(this.toDeleteErrorMessage(error));
        },
      });
  }

  private currentFilters(): UsersListFilters {
    return {
      search: this.currentSearchQuery(),
      deleted: this.deletedFilter(),
      active: this.activeFilter(),
      verified: this.verifiedFilter(),
      role: this.roleFilter(),
    };
  }

  private applySearch(): void {
    if (this.currentSearchQuery() === this.appliedSearchQuery) {
      return;
    }

    this.loadFirstPage();
  }

  private currentSearchQuery(): string {
    return this.searchTerm().trim();
  }

  private toUsersSortBy(active: string): UsersSortBy | null {
    switch (active) {
      case 'email':
        return 'email';
      case 'roles':
        return 'role';
      case 'lastLoginAt':
        return 'lastLoginAt';
      case 'createdAt':
        return 'createdAt';
      case 'updatedAt':
        return 'updatedAt';
      case 'deletedAt':
        return 'deletedAt';
      default:
        return null;
    }
  }

  private toLoadErrorMessage(error: unknown): string {
    const problem = this.apiErrorMapper.map(error).problem;

    return (
      problem.detail ??
      problem.title ??
      'No hemos podido cargar el listado de usuarios.'
    );
  }

  private toDeleteErrorMessage(error: unknown): string {
    const problem = this.apiErrorMapper.map(error).problem;

    return (
      problem.detail ?? problem.title ?? 'No hemos podido eliminar el usuario.'
    );
  }
}
