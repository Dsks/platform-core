import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import type { ProblemDetails } from '@platformcore/shared-models';

export interface NormalizedApiError {
  problem: ProblemDetails;
  status?: number;
  raw: unknown;
}

@Injectable({ providedIn: 'root' })
export class ApiErrorMapper {
  map(error: unknown): NormalizedApiError {
    return normalizeApiError(error);
  }
}

export function normalizeApiError(error: unknown): NormalizedApiError {
  if (error instanceof HttpErrorResponse) {
    return {
      problem: normalizeProblemDetails(error.error, error),
      status: error.status || undefined,
      raw: error,
    };
  }

  return {
    problem: normalizeProblemDetails(error),
    raw: error,
  };
}

export function normalizeProblemDetails(
  value: unknown,
  fallback?: Pick<
    HttpErrorResponse,
    'message' | 'status' | 'statusText' | 'url'
  >,
): ProblemDetails {
  if (isRecord(value)) {
    return {
      ...value,
      type: asString(value['type']),
      title: asString(value['title']) ?? fallback?.statusText,
      status: asNumber(value['status']) ?? asHttpStatus(fallback?.status),
      detail: asString(value['detail']),
      errors: asProblemErrors(value['errors']),
      params: asRecord(value['params']),
    };
  }

  if (typeof value === 'string' && value.trim()) {
    return {
      title: fallback?.statusText,
      status: asHttpStatus(fallback?.status),
      detail: value,
    };
  }

  return {
    title: fallback?.statusText,
    status: asHttpStatus(fallback?.status),
    detail: fallback?.message,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return isRecord(value) ? value : undefined;
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

function asNumber(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined;
}

function asHttpStatus(value: number | undefined): number | undefined {
  return value && value > 0 ? value : undefined;
}

function asProblemErrors(value: unknown): ProblemDetails['errors'] {
  if (Array.isArray(value) && value.every((item) => typeof item === 'string')) {
    return value;
  }

  return isRecord(value) ? value : undefined;
}
