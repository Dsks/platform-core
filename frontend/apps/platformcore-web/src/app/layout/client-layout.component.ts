import {
  ChangeDetectionStrategy,
  Component,
  computed,
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

type ClientNavItem = {
  label: string;
  path: string;
  exact: boolean;
};

@Component({
  selector: 'app-client-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './client-layout.component.html',
  styleUrl: './client-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientLayoutComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sessionStore = inject(SessionStore);

  protected readonly currentUser = this.sessionStore.currentUser;
  protected readonly mobileMenuOpen = signal(false);
  protected readonly loggingOut = signal(false);
  protected readonly logoutError = signal<string | null>(null);
  protected readonly navItems: ClientNavItem[] = [
    {
      label: 'Panel',
      path: '/panel',
      exact: true,
    },
  ];
  protected readonly userEmail = computed(
    () => this.currentUser()?.email ?? 'Sesion no disponible',
  );
  protected readonly logoutLabel = computed(() =>
    this.loggingOut() ? 'Cerrando sesion' : 'Cerrar sesion',
  );

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update((value) => !value);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  logout(): void {
    if (this.loggingOut()) {
      return;
    }

    this.closeMobileMenu();
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
}
