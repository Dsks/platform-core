import type { CurrentUser, LoginRequest, ProblemDetails } from '../index';

describe('shared models', () => {
  it('models the current user contract without auth token fields', () => {
    const user: CurrentUser = {
      id: '2fa8b8e9-3090-404e-a6e8-d95dd8e3b0ec',
      email: 'user@example.com',
      active: true,
      emailVerified: true,
      roles: ['USER'],
    };

    expect(user.roles).toEqual(['USER']);
    expect('token' in user).toBe(false);
  });

  it('keeps auth requests and problem details minimal', () => {
    const login: LoginRequest = {
      email: 'user@example.com',
      password: 'StrongPassw0rd!',
    };
    const problem: ProblemDetails = {
      type: 'https://platformcore.app/problems/VALIDATION_ERROR',
      title: 'VALIDATION_ERROR',
      status: 400,
      errors: ['email: must be a well-formed email address'],
    };

    expect(login.email).toBe('user@example.com');
    expect(problem.errors).toEqual([
      'email: must be a well-formed email address',
    ]);
  });
});
