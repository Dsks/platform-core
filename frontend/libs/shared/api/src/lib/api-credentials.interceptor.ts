import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './api-base-url';
import { isApiUrl } from './api-url';

export const apiCredentialsInterceptor: HttpInterceptorFn = (request, next) => {
  const baseUrl = inject(API_BASE_URL);

  if (!isApiUrl(request.url, baseUrl) || request.withCredentials) {
    return next(request);
  }

  return next(request.clone({ withCredentials: true }));
};
