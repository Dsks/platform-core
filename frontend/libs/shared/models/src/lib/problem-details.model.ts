export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: string[] | Record<string, unknown>;
  params?: Record<string, unknown>;
  [extension: string]: unknown;
}
