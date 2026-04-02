-- Enable the pgvector extension for vector similarity search
-- Uses a DO block to gracefully handle environments where the current user
-- lacks superuser privileges (e.g. managed databases where the extension
-- must be pre-installed by an administrator).
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping CREATE EXTENSION vector: insufficient privileges. Ensure the extension is installed by a superuser or cloud provider.';
END
$$;
