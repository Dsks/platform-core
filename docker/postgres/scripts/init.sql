
DO
$$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qomo_api_users_user') THEN
    CREATE ROLE qomo_api_users_user LOGIN PASSWORD 'qomo_api_users_password';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qomo_api_core_user') THEN
    CREATE ROLE qomo_api_core_user LOGIN PASSWORD 'qomo_api_core_password';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qomo_email_sender_user') THEN
    CREATE ROLE qomo_email_sender_user LOGIN PASSWORD 'qomo_email_sender_password';
  END IF;
END
$$;


CREATE DATABASE qomo_api_users_db OWNER qomo_api_users_user;
CREATE DATABASE qomo_api_core_db OWNER qomo_api_core_user;
CREATE DATABASE qomo_email_sender_db OWNER qomo_email_sender_user;
