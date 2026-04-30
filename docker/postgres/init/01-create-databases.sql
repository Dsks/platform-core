-- Crear roles si no existen
SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L;',
  'qomo_api_users_user', 'qomo_api_users_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qomo_api_users_user')
\gexec

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L;',
  'qomo_api_core_user', 'qomo_api_core_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qomo_api_core_user')
\gexec

SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L;',
  'qomo_email_sender_user', 'qomo_email_sender_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qomo_email_sender_user')
\gexec


-- Crear bases de datos si no existen (fuera de DO)
SELECT format(
  'CREATE DATABASE %I OWNER %I;',
  'qomo_api_users_db', 'qomo_api_users_user'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'qomo_api_users_db')
\gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I;',
  'qomo_api_core_db', 'qomo_api_core_user'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'qomo_api_core_db')
\gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I;',
  'qomo_email_sender_db', 'qomo_email_sender_user'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'qomo_email_sender_db')
\gexec
