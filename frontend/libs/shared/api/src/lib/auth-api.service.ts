import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  CsrfResponse,
  CurrentUser,
  GenericMessageResponse,
  LoginRequest,
  RegisterRequest,
  RegistrationAcceptedResponse,
  ResendVerificationRequest,
  VerifyEmailRequest,
} from '@qomo/shared-models';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import { buildApiUrl } from './api-url';
import { CsrfTokenStore } from './csrf-token-store';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrfTokenStore = inject(CsrfTokenStore);

  login(request: LoginRequest): Observable<void> {
    return this.http.post<void>(this.url('auth/login'), request);
  }

  logout(): Observable<void> {
    return this.http.post<void>(this.url('auth/logout'), null);
  }

  me(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(this.url('auth/me'));
  }

  csrf(): Observable<CsrfResponse> {
    return this.http.get<CsrfResponse>(this.url('auth/csrf')).pipe(
      tap((response) => {
        this.csrfTokenStore.set(response);
      }),
    );
  }

  register(request: RegisterRequest): Observable<RegistrationAcceptedResponse> {
    return this.http.post<RegistrationAcceptedResponse>(
      this.url('auth/register'),
      request,
    );
  }

  verifyEmail(
    request: VerifyEmailRequest,
  ): Observable<HttpResponse<GenericMessageResponse | null>> {
    return this.http.post<GenericMessageResponse | null>(
      this.url('users/verify-email'),
      request,
      {
        observe: 'response',
      },
    );
  }

  resendVerification(
    request: ResendVerificationRequest,
  ): Observable<GenericMessageResponse> {
    return this.http.post<GenericMessageResponse>(
      this.url('users/verification/resend'),
      request,
    );
  }

  private url(path: string): string {
    return buildApiUrl(path, this.baseUrl);
  }
}
