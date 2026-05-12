import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
  type TestRequest,
} from '@angular/common/http/testing';
import { provideSharedApi } from '@platformcore/shared-api';
import type {
  CreateUserRequest,
  UpdateUserRequest,
  UserDetail,
  UsersListQuery,
  UsersPage,
} from '@platformcore/shared-models';
import { UsersApiService } from './users-api.service';

const csrfResponse = {
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf',
  token: 'csrf-123',
};

const usersPage: UsersPage = {
  content: [
    {
      id: 'user-1',
      email: 'ana@platformcore.test',
      active: false,
      emailVerified: true,
      roles: ['ADMIN'],
      lastLogin: null,
      createdAt: '2026-05-01T08:00:00.000Z',
      updatedAt: '2026-05-10T12:00:00.000Z',
      deletedAt: null,
    },
  ],
  page: 2,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

describe('UsersApiService', () => {
  let service: UsersApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideSharedApi(), provideHttpClientTesting()],
    });

    service = TestBed.inject(UsersApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('serializes pagination, filters, sort, and trimmed search when listing users', () => {
    const query: UsersListQuery = {
      search: '  ana@platformcore.test  ',
      deleted: 'all',
      active: false,
      verified: false,
      role: 'ADMIN',
      sortBy: 'email',
      sortDirection: 'asc',
    };

    service.listUsers(2, 50, query).subscribe((response) => {
      expect(response).toEqual(usersPage);
    });

    const request = http.expectOne((candidate) => candidate.url === '/v1/users');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.params.keys().sort()).toEqual(
      [
        'active',
        'deleted',
        'page',
        'role',
        'search',
        'size',
        'sortBy',
        'sortDirection',
        'verified',
      ].sort(),
    );
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
    expect(request.request.params.get('deleted')).toBe('all');
    expect(request.request.params.get('sortBy')).toBe('email');
    expect(request.request.params.get('sortDirection')).toBe('asc');
    expect(request.request.params.get('search')).toBe('ana@platformcore.test');
    expect(request.request.params.get('active')).toBe('false');
    expect(request.request.params.get('verified')).toBe('false');
    expect(request.request.params.get('role')).toBe('ADMIN');
    request.flush(usersPage);
  });

  it('omits blank search and null optional filters when listing users', () => {
    const query: UsersListQuery = {
      search: '   ',
      deleted: 'false',
      active: null,
      verified: null,
      role: null,
      sortBy: 'createdAt',
      sortDirection: 'desc',
    };

    service.listUsers(0, 10, query).subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/v1/users');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual(
      ['deleted', 'page', 'size', 'sortBy', 'sortDirection'].sort(),
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.params.get('deleted')).toBe('false');
    expect(request.request.params.get('sortBy')).toBe('createdAt');
    expect(request.request.params.get('sortDirection')).toBe('desc');
    request.flush({
      ...usersPage,
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('refreshes csrf before creating a user', () => {
    const requestBody: CreateUserRequest = {
      email: 'new@platformcore.test',
      password: 'hashed-password',
      roles: ['USER'],
    };

    service.createUser(requestBody).subscribe();

    const csrfRequest = expectCsrfRefresh(http);
    http.expectNone('/v1/users');
    csrfRequest.flush(csrfResponse);

    const request = http.expectOne('/v1/users');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
    expect(request.request.body).toEqual(requestBody);
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('refreshes csrf before updating a user and maps the response', () => {
    const requestBody: UpdateUserRequest = {
      active: false,
    };
    let updatedUser: UserDetail | undefined;

    service.updateUser('user-1', requestBody).subscribe((user) => {
      updatedUser = user;
    });

    const csrfRequest = expectCsrfRefresh(http);
    http.expectNone('/v1/users/user-1');
    csrfRequest.flush(csrfResponse);

    const request = http.expectOne('/v1/users/user-1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
    expect(request.request.body).toEqual(requestBody);
    request.flush({
      id: 'user-1',
      email: 'ana@platformcore.test',
      isActive: false,
      isVerified: true,
      roles: ['USER'],
      lastLogin: null,
      createdAt: '2026-05-01T08:00:00.000Z',
      updatedAt: '2026-05-10T12:00:00.000Z',
    });

    expect(updatedUser).toEqual({
      id: 'user-1',
      email: 'ana@platformcore.test',
      active: false,
      emailVerified: true,
      roles: ['USER'],
      lastLogin: null,
      createdAt: '2026-05-01T08:00:00.000Z',
      updatedAt: '2026-05-10T12:00:00.000Z',
    });
  });

  it('refreshes csrf before deleting a user', () => {
    service.deleteUser('user-1').subscribe();

    const csrfRequest = expectCsrfRefresh(http);
    http.expectNone('/v1/users/user-1');
    csrfRequest.flush(csrfResponse);

    const request = http.expectOne('/v1/users/user-1');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
    request.flush(null, { status: 204, statusText: 'No Content' });
  });
});

function expectCsrfRefresh(http: HttpTestingController): TestRequest {
  const request = http.expectOne('/v1/auth/csrf');
  expect(request.request.method).toBe('GET');
  expect(request.request.withCredentials).toBe(true);
  expect(request.request.headers.has('X-CSRF-TOKEN')).toBe(false);

  return request;
}
