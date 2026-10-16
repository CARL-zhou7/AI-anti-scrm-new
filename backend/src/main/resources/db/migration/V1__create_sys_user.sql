CREATE TABLE sys_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(24)     NOT NULL,
  username_key  VARCHAR(24)     GENERATED ALWAYS AS (LOWER(username)) STORED,
  password_hash VARCHAR(255)    NOT NULL,
  role          VARCHAR(16)     NOT NULL DEFAULT 'USER',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_username_key (username_key),
  CONSTRAINT ck_user_username_len CHECK (CHAR_LENGTH(username) BETWEEN 2 AND 24),
  CONSTRAINT ck_user_role CHECK (role IN ('USER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
