import { FormControl, FormGroupDirective, NgForm } from '@angular/forms';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';

export function passwordsMatchValidator(
  control: AbstractControl,
): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;

  return password && confirmPassword && password !== confirmPassword
    ? { passwordMismatch: true }
    : null;
}

export class PasswordMismatchErrorStateMatcher implements ErrorStateMatcher {
  isErrorState(
    control: FormControl | null,
    form: FormGroupDirective | NgForm | null,
  ): boolean {
    const isSubmitted = form?.submitted ?? false;
    const isControlInteracted = Boolean(
      control && (control.dirty || control.touched || isSubmitted),
    );
    const isControlInvalid = Boolean(
      control?.invalid && isControlInteracted,
    );
    const isPasswordMismatch = Boolean(
      control?.parent?.hasError('passwordMismatch') && isControlInteracted,
    );

    return isControlInvalid || isPasswordMismatch;
  }
}
