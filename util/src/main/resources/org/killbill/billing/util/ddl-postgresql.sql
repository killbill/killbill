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

/* Alter 'serial' columns to 'bigint' because 'serial' is 32bit in PG while 64bit in MySQL */
CREATE OR REPLACE FUNCTION update_serial_to_bigint_oncreate()
        RETURNS event_trigger LANGUAGE plpgsql AS $$
DECLARE
    r record;
    matches text[];
BEGIN
    FOR r IN SELECT * FROM pg_event_trigger_ddl_commands()
    LOOP
	    SELECT regexp_matches(current_query(), E'\\m(\\w+)\\s+serial\\M') INTO matches;
	    IF r.object_type = 'table' AND array_length(matches, 1) > 0 THEN
            RAISE NOTICE 'Altering % % column % from serial to bigint',
                     r.object_type,
                     r.object_identity,
                     matches[1];
             EXECUTE 'ALTER TABLE ' || r.object_identity || ' ALTER COLUMN ' || matches[1] || ' TYPE bigint';
        END IF;
    END LOOP;
END
$$;

CREATE EVENT TRIGGER update_serial_to_bigint_oncreate
   ON ddl_command_end WHEN TAG IN ('CREATE TABLE')
   EXECUTE PROCEDURE update_serial_to_bigint_oncreate();
