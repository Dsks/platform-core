import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService, SessionStore } from '@platformcore/shared-auth';
import { finalize, take } from 'rxjs';

type AdminNavItem = {
  label: string;
  icon: string;
  path: string;
  exact: boolean;
};

const ADMIN_SIDEBAR_COLLAPSED_KEY = 'platformcore.admin.sidebar.collapsed';

@Component({
  selector: 'admin-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminLayoutComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sessionStore = inject(SessionStore);

  protected readonly currentUser = this.sessionStore.currentUser;
  protected readonly collapsed = signal(this.getInitialCollapsedState());
  protected readonly loggingOut = signal(false);
  protected readonly logoutError = signal<string | null>(null);
  protected readonly navItems: AdminNavItem[] = [
    {
      label: 'Panel',
      icon: 'dashboard',
      path: '/panel',
      exact: true,
    },
    {
      label: 'Usuarios',
      icon: 'group',
      path: '/usuarios',
      exact: false,
    },
  ];
  protected readonly userEmail = computed(
    () => this.currentUser()?.email ?? 'Sesion no disponible',
  );
  protected readonly userInitial = computed(() =>
    this.userEmail().charAt(0).toUpperCase(),
  );
  protected readonly logoutLabel = computed(() =>
    this.loggingOut() ? 'Cerrando sesion' : 'Cerrar sesion',
  );

  constructor() {
    effect(() => {
      localStorage.setItem(
        ADMIN_SIDEBAR_COLLAPSED_KEY,
        String(this.collapsed()),
      );
    });
  }

  toggleSidebar(): void {
    this.collapsed.update((value) => !value);
  }

  logout(): void {
    if (this.loggingOut()) {
      return;
    }

    this.loggingOut.set(true);
    this.logoutError.set(null);

    this.authService
      .logout()
      .pipe(
        take(1),
        finalize(() => {
          this.loggingOut.set(false);
        }),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/iniciar-sesion');
        },
        error: () => {
          this.logoutError.set('No hemos podido cerrar la sesion.');
        },
      });
  }

  private getInitialCollapsedState(): boolean {
    return localStorage.getItem(ADMIN_SIDEBAR_COLLAPSED_KEY) === 'true';
  }
}
