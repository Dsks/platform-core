export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export type RegistrationStatus = 'VERIFICATION_REQUIRED' | 'ALREADY_REGISTERED';

export interface RegistrationAcceptedResponse {
  requestId: string;
  status: RegistrationStatus;
  message: string;
}

export interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface VerifyEmailRequest {
  code: string;
}

export interface ResendVerificationRequest {
  email: string;
}

export interface GenericMessageResponse {
  message: string;
}
