import { computed, Injectable, signal } from '@angular/core';
import { CurrentUser, QOMO_ROLES, Role } from '@qomo/shared-models';
import type { SessionState } from './session.types';

const initialSessionState: SessionState = {
  status: 'unknown',
  currentUser: null,
};

@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly stateSignal = signal<SessionState>(initialSessionState);

  readonly state = this.stateSignal.asReadonly();
  readonly status = computed(() => this.state().status);
  readonly currentUser = computed(() => this.state().currentUser);
  readonly roles = computed(() => this.currentUser()?.roles ?? []);
  readonly isAuthenticated = computed(
    () => this.status() === 'authenticated' && this.currentUser() !== null,
  );
  readonly isGuest = computed(() => this.status() === 'guest');
  readonly isAdmin = computed(() =>
    this.hasAnyRole(QOMO_ROLES.ADMIN, QOMO_ROLES.SUPERADMIN),
  );
  readonly isSuperAdmin = computed(() =>
    this.hasAnyRole(QOMO_ROLES.SUPERADMIN),
  );
  readonly isClient = computed(
    () =>
      this.hasAnyRole(QOMO_ROLES.CLIENT, QOMO_ROLES.USER) ||
      (this.isAuthenticated() && !this.isAdmin()),
  );

  setAuthenticated(user: CurrentUser): void {
    this.stateSignal.set({
      status: 'authenticated',
      currentUser: user,
    });
  }

  setGuest(): void {
    this.stateSignal.set({
      status: 'guest',
      currentUser: null,
    });
  }

  clear(): void {
    this.stateSignal.set(initialSessionState);
  }

  hasAnyRole(...roles: Role[]): boolean {
    const currentRoles = new Set(this.roles());

    return roles.some((role) => currentRoles.has(role));
  }
}
