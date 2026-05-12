import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './api-base-url';
import { isApiUrl } from './api-url';
import { CsrfTokenStore } from './csrf-token-store';

const unsafeMethods = new Set(['DELETE', 'PATCH', 'POST', 'PUT']);

export const apiCsrfInterceptor: HttpInterceptorFn = (request, next) => {
  const baseUrl = inject(API_BASE_URL);

  if (
    !unsafeMethods.has(request.method.toUpperCase()) ||
    !isApiUrl(request.url, baseUrl)
  ) {
    return next(request);
  }

  const csrf = inject(CsrfTokenStore).snapshot();
  if (
    !csrf?.headerName ||
    !csrf.token ||
    request.headers.has(csrf.headerName)
  ) {
    return next(request);
  }

  return next(
    request.clone({
      headers: request.headers.set(csrf.headerName, csrf.token),
    }),
  );
};
