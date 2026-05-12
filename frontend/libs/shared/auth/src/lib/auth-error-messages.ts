import { HttpErrorResponse } from '@angular/common/http';
import { normalizeApiError } from '@qomo/shared-api';
import type { RegistrationAcceptedResponse } from '@qomo/shared-models';

const LOGIN_ERROR_MESSAGE =
  'No hemos podido iniciar sesión. Revisa tus credenciales.';
const INVALID_CREDENTIALS_MESSAGE = 'Email o contraseña incorrectos.';
const REGISTER_ALREADY_REGISTERED_MESSAGE =
  'Ya existe una cuenta con este email. Inicia sesión.';
const REGISTER_ERROR_MESSAGE =
  'No hemos podido crear la cuenta. Revisa los datos e inténtalo de nuevo.';
const INVALID_REGISTER_DATA_MESSAGE = 'Datos de registro inválidos.';

export function toLoginErrorMessage(error: unknown): string {
  const problem = normalizeApiError(error).problem;
  const message = translateLoginErrorMessage(problem.detail ?? problem.title);

  return message ?? LOGIN_ERROR_MESSAGE;
}

export function toRegisterErrorMessage(error: unknown): string {
  if (isAlreadyRegisteredError(error)) {
    return REGISTER_ALREADY_REGISTERED_MESSAGE;
  }

  const problem = normalizeApiError(error).problem;
  const message = translateRegisterErrorMessage(
    problem.detail ?? problem.title,
  );

  return message ?? REGISTER_ERROR_MESSAGE;
}

function translateLoginErrorMessage(
  message: string | null | undefined,
): string | null {
  if (!message) {
    return null;
  }

  const normalizedMessage = message.trim().toLowerCase();

  if (
    normalizedMessage === 'invalid credentials' ||
    normalizedMessage === 'invalid_credentials' ||
    normalizedMessage === 'credenciales invalidas.' ||
    normalizedMessage === 'credenciales inválidas.' ||
    normalizedMessage === 'unauthorized' ||
    normalizedMessage === 'not authorized'
  ) {
    return INVALID_CREDENTIALS_MESSAGE;
  }

  return message;
}

function translateRegisterErrorMessage(
  message: string | null | undefined,
): string | null {
  if (!message) {
    return null;
  }

  const normalizedMessage = message.trim().toLowerCase();

  if (
    normalizedMessage === 'account already exists' ||
    normalizedMessage === 'account already registered. please sign in.' ||
    normalizedMessage === 'already_registered' ||
    normalizedMessage === 'user_email_already_in_use'
  ) {
    return REGISTER_ALREADY_REGISTERED_MESSAGE;
  }

  if (
    normalizedMessage === 'invalid registration data' ||
    normalizedMessage === 'invalid register data' ||
    normalizedMessage === 'datos de registro invalidos.' ||
    normalizedMessage === 'datos de registro inválidos.'
  ) {
    return INVALID_REGISTER_DATA_MESSAGE;
  }

  return message;
}

function isAlreadyRegisteredError(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse) || !isRecord(error.error)) {
    return false;
  }

  return (
    asRegistrationStatus(error.error) === 'ALREADY_REGISTERED' ||
    error.error['title'] === 'USER_EMAIL_ALREADY_IN_USE'
  );
}

function asRegistrationStatus(
  value: Record<string, unknown>,
): RegistrationAcceptedResponse['status'] | null {
  const status = value['status'];

  return status === 'VERIFICATION_REQUIRED' || status === 'ALREADY_REGISTERED'
    ? status
    : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
