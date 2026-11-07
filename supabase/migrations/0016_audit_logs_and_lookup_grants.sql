-- 0016: audit-log read access and lookup write hardening
--
-- audit_logs already has an admin-only SELECT policy, but table-level grants
-- were never extended to authenticated, so even an administrator received
-- "permission denied for table audit_logs". Grant SELECT and let RLS decide
-- which rows are visible (public.is_admin()).
--
-- The lookup tables and match_registrations are written exclusively through
-- SECURITY DEFINER RPCs. Supabase's default grants still exposed INSERT /
-- UPDATE / DELETE to anon and authenticated; RLS already rejected those
-- writes, but removing the grants adds defence in depth.

grant select on public.audit_logs to authenticated;

revoke insert, update, delete, truncate, references, trigger
    on public.categories,
       public.game_modes,
       public.tournament_types,
       public.match_registrations
    from anon, authenticated;

grant select on public.categories,
               public.game_modes,
               public.tournament_types,
               public.match_registrations
    to anon, authenticated;
