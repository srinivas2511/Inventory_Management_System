-- ShedLock table for distributed scheduler coordination (JDBC template provider)
CREATE TABLE shedlock (
  name       VARCHAR(64)  NOT NULL PRIMARY KEY,
  lock_until TIMESTAMP    NOT NULL,
  locked_at  TIMESTAMP    NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
