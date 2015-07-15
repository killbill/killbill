/* We cannot use timestamp in MySQL because of the implicit TimeZone conversions it does behind the scenes */
CREATE DOMAIN datetime AS timestamp without time zone;
/* TEXT in MySQL is smaller then MEDIUMTEXT */
CREATE DOMAIN mediumtext AS text;

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
