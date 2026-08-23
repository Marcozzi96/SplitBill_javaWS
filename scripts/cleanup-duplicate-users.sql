-- ============================================================================
-- Bonifica dati produzione: colonna users.email_canonical + vincoli UNIQUE
-- ============================================================================
-- Contesto: l'email viene salvata come la digita l'utente (users.email);
-- unicita' e lookup usano users.email_canonical (lowercase; per
-- Gmail/Googlemail senza punti nel local part, perche' Google li ignora).
--
-- Da eseguire con il backend FERMO (docker compose stop backend), PRIMA del
-- deploy della versione con i vincoli UNIQUE (username, email_canonical):
-- se ci sono duplicati canonici, la CREATE UNIQUE INDEX fallisce.
--
-- Esecuzione sul VPS (dalla cartella ~/splitbill/javaWS):
--   docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
--       < scripts/cleanup-duplicate-users.sql
--
-- IMPORTANTE: fare un backup prima:
--   docker compose exec -T db pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > backup_pre_cleanup.sql
-- ============================================================================


-- ----------------------------------------------------------------------------
-- SEZIONE 1 — ANALISI (sola lettura, eseguire per prima e valutare l'output)
-- ----------------------------------------------------------------------------

-- 1a. Email o username NULL (violerebbero il NOT NULL)
SELECT 'null_email' AS problema, id, username, email, deleted FROM users WHERE email IS NULL
UNION ALL
SELECT 'null_username', id, username, email, deleted FROM users WHERE username IS NULL;

-- 1b. Duplicati sulla chiave canonica (quella che usera' l'app):
--     lowercase sempre; per gmail.com/googlemail.com senza punti nel local part
WITH canonica AS (
    SELECT id, username, email, deleted,
           CASE WHEN lower(split_part(email, '@', 2)) IN ('gmail.com', 'googlemail.com')
                THEN replace(lower(split_part(email, '@', 1)), '.', '') || '@' || lower(split_part(email, '@', 2))
                ELSE lower(email)
           END AS email_canonical
    FROM users
)
SELECT email_canonical, COUNT(*) AS n,
       array_agg(id ORDER BY id) AS ids,
       array_agg(username ORDER BY id) AS usernames,
       array_agg(email ORDER BY id) AS emails
FROM canonica
GROUP BY email_canonical
HAVING COUNT(*) > 1;

-- 1c. Username duplicati (confronto esatto, come il futuro vincolo UNIQUE)
SELECT username, COUNT(*) AS n, array_agg(id ORDER BY id) AS ids
FROM users
GROUP BY username
HAVING COUNT(*) > 1;

-- 1d. Anteprima del backfill: email salvata -> email_canonical
SELECT id, username, email,
       CASE WHEN lower(split_part(email, '@', 2)) IN ('gmail.com', 'googlemail.com')
            THEN replace(lower(split_part(email, '@', 1)), '.', '') || '@' || lower(split_part(email, '@', 2))
            ELSE lower(email)
       END AS email_canonical
FROM users
ORDER BY id;


-- ----------------------------------------------------------------------------
-- SEZIONE 2 — BONIFICA
-- Tutto in un'unica transazione: se i conteggi finali non tornano, ROLLBACK.
-- Eseguire SOLO se la sezione 1 non mostra duplicati canonici (1b = 0 righe);
-- in caso contrario risolvere prima i duplicati (soft-delete dei doppioni).
-- ----------------------------------------------------------------------------
BEGIN;

-- 2a. Nuova colonna (IF NOT EXISTS: Hibernate la creerebbe altrimenti al deploy)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_canonical varchar(255);

-- 2b. Backfill della chiave canonica per tutte le righe esistenti
UPDATE users
SET email_canonical =
    CASE WHEN lower(split_part(email, '@', 2)) IN ('gmail.com', 'googlemail.com')
         THEN replace(lower(split_part(email, '@', 1)), '.', '') || '@' || lower(split_part(email, '@', 2))
         ELSE lower(email)
    END;

-- 2c. Indice univoco sulla chiave canonica
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_canonical ON users(email_canonical);


-- ----------------------------------------------------------------------------
-- SEZIONE 3 — VERIFICA (devono restituire TUTTE zero righe prima del COMMIT)
-- ----------------------------------------------------------------------------
SELECT 'null_canonical' AS check_name, COUNT(*) AS righe FROM users WHERE email_canonical IS NULL
UNION ALL
SELECT 'canonical_dup', COUNT(*) FROM (SELECT email_canonical FROM users GROUP BY email_canonical HAVING COUNT(*) > 1) t
UNION ALL
SELECT 'username_dup', COUNT(*) FROM (SELECT username FROM users GROUP BY username HAVING COUNT(*) > 1) t;

-- Se tutte le righe sopra sono 0:
COMMIT;
-- Altrimenti:
-- ROLLBACK;
