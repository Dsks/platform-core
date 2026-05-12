import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL, buildApiUrl } from '@qomo/shared-api';
import { AuthService } from '@qomo/shared-auth';
import {
  CreateUserRequest,
  UpdateUserRequest,
  UserDetail,
  UsersListQuery,
  UsersPage,
} from '@qomo/shared-models';
import { map, Observable, switchMap } from 'rxjs';

type UserDetailResponse = {
  id: string;
  email: string;
  isActive?: boolean;
  active?: boolean;
  isVerified?: boolean;
  emailVerified?: boolean;
  lastLogin?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  roles: string[];
};

@Injectable({ providedIn: 'root' })
export class UsersApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly authService = inject(AuthService);

  listUsers(
    page: number,
    size: number,
    query: UsersListQuery,
  ): Observable<UsersPage> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('deleted', query.deleted)
      .set('sortBy', query.sortBy)
      .set('sortDirection', query.sortDirection);

    const search = query.search?.trim();
    if (search) {
      params = params.set('search', search);
    }

    if (query.active !== null && query.active !== undefined) {
      params = params.set('active', String(query.active));
    }

    if (query.verified !== null && query.verified !== undefined) {
      params = params.set('verified', String(query.verified));
    }

    if (query.role) {
      params = params.set('role', query.role);
    }

    return this.http.get<UsersPage>(this.url('users'), { params });
  }

  getUser(id: string): Observable<UserDetail> {
    return this.http
      .get<UserDetailResponse>(this.url(`users/${id}`))
      .pipe(map((response) => this.toUserDetail(response)));
  }

  createUser(request: CreateUserRequest): Observable<void> {
    return this.authService
      .refreshCsrfToken()
      .pipe(switchMap(() => this.http.post<void>(this.url('users'), request)));
  }

  updateUser(
    id: string,
    request: UpdateUserRequest,
  ): Observable<UserDetail> {
    return this.authService.refreshCsrfToken().pipe(
      switchMap(() =>
        this.http.patch<UserDetailResponse>(this.url(`users/${id}`), request),
      ),
      map((response) => this.toUserDetail(response)),
    );
  }

  deleteUser(id: string): Observable<void> {
    return this.authService.refreshCsrfToken().pipe(
      switchMap(() => this.http.delete<void>(this.url(`users/${id}`))),
    );
  }

  private url(path: string): string {
    return buildApiUrl(path, this.baseUrl);
  }

  private toUserDetail(response: UserDetailResponse): UserDetail {
    return {
      id: response.id,
      email: response.email,
      active: response.active ?? response.isActive ?? false,
      emailVerified: response.emailVerified ?? response.isVerified ?? false,
      roles: response.roles,
      lastLogin: response.lastLogin,
      createdAt: response.createdAt,
      updatedAt: response.updatedAt,
    };
  }
}
