import { HttpErrorResponse } from '@angular/common/http';
import {
  ApiErrorMapper,
  normalizeApiError,
  normalizeProblemDetails,
} from './api-error.mapper';

describe('api-error mapper', () => {
  it('preserves backend problem details extensions', () => {
    const problem = normalizeProblemDetails({
      type: 'https://qomo.app/problems/VALIDATION_ERROR',
      title: 'VALIDATION_ERROR',
      status: 400,
      detail: 'Request validation failed',
      errors: ['email: must be a well-formed email address'],
      params: { reason: 'invalid' },
      traceId: 'abc-123',
    });

    expect(problem).toEqual({
      type: 'https://qomo.app/problems/VALIDATION_ERROR',
      title: 'VALIDATION_ERROR',
      status: 400,
      detail: 'Request validation failed',
      errors: ['email: must be a well-formed email address'],
      params: { reason: 'invalid' },
      traceId: 'abc-123',
    });
  });

  it('normalizes HttpErrorResponse bodies that are problem details', () => {
    const error = new HttpErrorResponse({
      status: 403,
      statusText: 'Forbidden',
      error: {
        type: 'https://qomo.app/problems/EMAIL_NOT_VERIFIED',
        title: 'EMAIL_NOT_VERIFIED',
        status: 403,
        detail: 'Login not completed',
      },
    });

    expect(normalizeApiError(error).problem).toMatchObject({
      type: 'https://qomo.app/problems/EMAIL_NOT_VERIFIED',
      title: 'EMAIL_NOT_VERIFIED',
      status: 403,
      detail: 'Login not completed',
    });
  });

  it('falls back for non-problem errors', () => {
    const mapper = new ApiErrorMapper();
    const mapped = mapper.map(
      new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error',
        error: 'Network unavailable',
      }),
    );

    expect(mapped.problem).toEqual({
      title: 'Unknown Error',
      status: undefined,
      detail: 'Network unavailable',
    });
  });
});
