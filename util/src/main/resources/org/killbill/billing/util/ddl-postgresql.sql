/* We cannot use timestamp in MySQL because of the implicit TimeZone conversions it does behind the scenes */
DROP DOMAIN IF EXISTS datetime CASCADE;
CREATE DOMAIN datetime AS timestamp without time zone;
/* TEXT in MySQL is smaller then MEDIUMTEXT */
DROP DOMAIN IF EXISTS mediumtext CASCADE;
CREATE DOMAIN mediumtext AS text;
/* PostgreSQL uses BYTEA to manage all BLOB types */
DROP DOMAIN IF EXISTS mediumblob CASCADE;
CREATE DOMAIN mediumblob AS bytea;

CREATE OR REPLACE LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION last_insert_id() RETURNS BIGINT AS $$
    DECLARE
        result BIGINT;
    BEGIN
        SELECT lastval() INTO result;
        RETURN result;
    EXCEPTION WHEN OTHERS THEN
        SELECT NULL INTO result;
        RETURN result;
    END;
$$ LANGUAGE plpgsql VOLATILE;

CREATE OR REPLACE FUNCTION schema() RETURNS VARCHAR AS $$
    DECLARE
        result VARCHAR;
    BEGIN
        SELECT current_schema() INTO result;
        RETURN result;
    EXCEPTION WHEN OTHERS THEN
        SELECT NULL INTO result;
        RETURN result;
    END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION hour(ts TIMESTAMP WITH TIME ZONE) RETURNS INTEGER AS $$
    DECLARE
        result INTEGER;
    BEGIN
        SELECT EXTRACT(HOUR FROM ts) INTO result;
        RETURN result;
    EXCEPTION WHEN OTHERS THEN
        SELECT NULL INTO result;
        RETURN result;
    END;
$$ LANGUAGE plpgsql IMMUTABLE;
