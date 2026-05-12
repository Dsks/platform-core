import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { FormGroupDirective } from '@angular/forms';
import {
  PasswordMismatchErrorStateMatcher,
  passwordsMatchValidator,
} from './password-confirmation.validator';

describe('passwordsMatchValidator', () => {
  it('returns null when passwords match', () => {
    const form = createPasswordForm('s3cret', 's3cret');

    expect(passwordsMatchValidator(form)).toBeNull();
  });

  it('returns a passwordMismatch error when passwords do not match', () => {
    const form = createPasswordForm('s3cret', 'different');

    expect(passwordsMatchValidator(form)).toEqual({
      passwordMismatch: true,
    });
  });

  it('returns null when password is missing', () => {
    const form = createPasswordForm('', 's3cret');

    expect(passwordsMatchValidator(form)).toBeNull();
  });

  it('returns null when confirmPassword is missing', () => {
    const form = createPasswordForm('s3cret', '');

    expect(passwordsMatchValidator(form)).toBeNull();
  });
});

describe('PasswordMismatchErrorStateMatcher', () => {
  it('marks the confirm password control as invalid when interacted and passwords mismatch', () => {
    const form = createPasswordForm('s3cret', 'different');
    form.setErrors(passwordsMatchValidator(form));
    form.controls.confirmPassword.markAsTouched();
    const matcher = new PasswordMismatchErrorStateMatcher();

    expect(
      matcher.isErrorState(form.controls.confirmPassword, null),
    ).toBe(true);
  });

  it('marks the confirm password control as invalid when the submitted form has a password mismatch', () => {
    const form = createPasswordForm('s3cret', 'different');
    form.setErrors(passwordsMatchValidator(form));
    const matcher = new PasswordMismatchErrorStateMatcher();
    const submittedForm = { submitted: true } as FormGroupDirective;

    expect(
      matcher.isErrorState(form.controls.confirmPassword, submittedForm),
    ).toBe(true);
  });

  it('preserves control-level invalid state behavior', () => {
    const form = new FormGroup({
      confirmPassword: new FormControl('', Validators.required),
    });
    form.controls.confirmPassword.markAsTouched();
    const matcher = new PasswordMismatchErrorStateMatcher();

    expect(
      matcher.isErrorState(form.controls.confirmPassword, null),
    ).toBe(true);
  });
});

function createPasswordForm(
  password: string,
  confirmPassword: string,
): FormGroup<{
  password: FormControl<string | null>;
  confirmPassword: FormControl<string | null>;
}> {
  return new FormGroup({
    password: new FormControl(password),
    confirmPassword: new FormControl(confirmPassword),
  });
}
