-- 0011: allow the public_profiles view to evaluate its soft-delete filter.
--
-- public_profiles is declared WITH (security_invoker = true). Its definition
-- filters `WHERE deleted_at IS NULL`, so the caller needs column-level SELECT on
-- deleted_at. Migration 0007 removed that column along with the contact fields,
-- which made every read through public_profiles fail with 42501 even though the
-- view only exposes safe profile columns.
--
-- deleted_at is a soft-delete timestamp, not personal data, so granting SELECT
-- on that single column is safe. email and phone remain revoked. This does not
-- re-enable `SELECT *` for authenticated users.

grant select (deleted_at) on public.profiles to anon, authenticated;
