import { HttpErrorResponse } from '@angular/common/http';
import {
  toLoginErrorMessage,
  toRegisterErrorMessage,
} from './auth-error-messages';

describe('auth error messages', () => {
  it('maps invalid login credential variants to a readable message', () => {
    expect(toLoginErrorMessage(problemError('Credenciales invalidas.'))).toBe(
      'Email o contraseña incorrectos.',
    );
    expect(toLoginErrorMessage(problemError('invalid_credentials'))).toBe(
      'Email o contraseña incorrectos.',
    );
    expect(toLoginErrorMessage(problemError('Unauthorized'))).toBe(
      'Email o contraseña incorrectos.',
    );
  });

  it('falls back to the generic login message', () => {
    expect(toLoginErrorMessage(new Error('boom'))).toBe(
      'No hemos podido iniciar sesión. Revisa tus credenciales.',
    );
  });

  it('maps already registered responses by status, title, and normalized message', () => {
    expect(
      toRegisterErrorMessage(
        new HttpErrorResponse({
          status: 409,
          statusText: 'Conflict',
          error: {
            status: 'ALREADY_REGISTERED',
          },
        }),
      ),
    ).toBe('Ya existe una cuenta con este email. Inicia sesión.');
    expect(
      toRegisterErrorMessage(
        new HttpErrorResponse({
          status: 409,
          statusText: 'Conflict',
          error: {
            title: 'USER_EMAIL_ALREADY_IN_USE',
          },
        }),
      ),
    ).toBe('Ya existe una cuenta con este email. Inicia sesión.');
    expect(
      toRegisterErrorMessage(
        problemError('Account already registered. Please sign in.'),
      ),
    ).toBe('Ya existe una cuenta con este email. Inicia sesión.');
  });

  it('maps invalid register data to a readable message', () => {
    expect(
      toRegisterErrorMessage(problemError('Datos de registro invalidos.')),
    ).toBe('Datos de registro inválidos.');
    expect(toRegisterErrorMessage(problemError('invalid register data'))).toBe(
      'Datos de registro inválidos.',
    );
  });

  it('falls back to the generic register message', () => {
    expect(toRegisterErrorMessage(new Error('boom'))).toBe(
      'No hemos podido crear la cuenta. Revisa los datos e inténtalo de nuevo.',
    );
  });
});

function problemError(detail: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 400,
    statusText: 'Bad Request',
    error: {
      detail,
    },
  });
}
