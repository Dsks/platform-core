import { inject, Injectable } from '@angular/core';
import { AuthApiService, CsrfTokenStore } from '@qomo/shared-api';
import { CsrfResponse, CurrentUser, LoginRequest } from '@qomo/shared-models';
import { catchError, map, Observable, of, switchMap, tap } from 'rxjs';
import { SessionStore } from './session-store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authApi = inject(AuthApiService);
  private readonly csrfTokenStore = inject(CsrfTokenStore);
  private readonly sessionStore = inject(SessionStore);

  bootstrapSession(): Observable<CurrentUser | null> {
    return this.authApi.me().pipe(
      tap((user) => {
        this.sessionStore.setAuthenticated(user);
      }),
      map((user) => user),
      catchError(() => {
        this.sessionStore.setGuest();

        return of(null);
      }),
    );
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.authApi
      .login(request)
      .pipe(switchMap(() => this.refreshCurrentUser()));
  }

  refreshCurrentUser(): Observable<CurrentUser> {
    return this.authApi.me().pipe(
      tap((user) => {
        this.sessionStore.setAuthenticated(user);
      }),
    );
  }

  ensureCsrfToken(): Observable<CsrfResponse> {
    const existingToken = this.csrfTokenStore.snapshot();

    return existingToken
      ? of(existingToken)
      : this.authApi.csrf().pipe(
          tap((csrf) => {
            this.csrfTokenStore.set(csrf);
          }),
        );
  }

  refreshCsrfToken(): Observable<CsrfResponse> {
    return this.authApi.csrf();
  }

  logout(): Observable<void> {
    return this.ensureCsrfToken().pipe(
      switchMap(() => this.authApi.logout()),
      tap(() => {
        this.clearLocalSession();
      }),
    );
  }

  private clearLocalSession(): void {
    this.csrfTokenStore.clear();
    this.sessionStore.setGuest();
  }
}
