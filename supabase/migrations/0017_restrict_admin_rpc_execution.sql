-- 0017: restrict administrative and match-registration RPCs to signed-in users
--
-- Postgres grants EXECUTE on new functions to PUBLIC by default. Every admin
-- RPC already calls public.require_admin() and therefore rejects anonymous
-- callers at runtime, but removing the anon grant is defence in depth and
-- clears the Supabase advisor warning for these functions.
--
-- `is_admin()` explicitly stays executable by anon: the client uses it to
-- decide whether to reveal the admin entry point, and it only reports the
-- caller's own role membership.

revoke all on function public.admin_upsert_category(uuid, jsonb) from public, anon, authenticated;
revoke all on function public.admin_delete_category(uuid) from public, anon, authenticated;
revoke all on function public.admin_upsert_game_mode(uuid, jsonb) from public, anon, authenticated;
revoke all on function public.admin_delete_game_mode(uuid) from public, anon, authenticated;
revoke all on function public.admin_upsert_tournament_type(uuid, jsonb) from public, anon, authenticated;
revoke all on function public.admin_delete_tournament_type(uuid) from public, anon, authenticated;
revoke all on function public.admin_upsert_tournament(uuid, jsonb) from public, anon, authenticated;
revoke all on function public.admin_set_tournament_status(uuid, public.tournament_status) from public, anon, authenticated;
revoke all on function public.admin_delete_tournament(uuid) from public, anon, authenticated;
revoke all on function public.admin_upsert_match(uuid, jsonb, boolean) from public, anon, authenticated;
revoke all on function public.admin_set_match_status(uuid, public.match_status) from public, anon, authenticated;
revoke all on function public.admin_set_match_published(uuid, boolean) from public, anon, authenticated;
revoke all on function public.admin_duplicate_match(uuid) from public, anon, authenticated;
revoke all on function public.admin_delete_match(uuid) from public, anon, authenticated;
revoke all on function public.register_for_match(uuid, uuid) from public, anon, authenticated;
revoke all on function public.cancel_match_registration(uuid) from public, anon, authenticated;

grant execute on function public.admin_upsert_category(uuid, jsonb) to authenticated, service_role;
grant execute on function public.admin_delete_category(uuid) to authenticated, service_role;
grant execute on function public.admin_upsert_game_mode(uuid, jsonb) to authenticated, service_role;
grant execute on function public.admin_delete_game_mode(uuid) to authenticated, service_role;
grant execute on function public.admin_upsert_tournament_type(uuid, jsonb) to authenticated, service_role;
grant execute on function public.admin_delete_tournament_type(uuid) to authenticated, service_role;
grant execute on function public.admin_upsert_tournament(uuid, jsonb) to authenticated, service_role;
grant execute on function public.admin_set_tournament_status(uuid, public.tournament_status) to authenticated, service_role;
grant execute on function public.admin_delete_tournament(uuid) to authenticated, service_role;
grant execute on function public.admin_upsert_match(uuid, jsonb, boolean) to authenticated, service_role;
grant execute on function public.admin_set_match_status(uuid, public.match_status) to authenticated, service_role;
grant execute on function public.admin_set_match_published(uuid, boolean) to authenticated, service_role;
grant execute on function public.admin_duplicate_match(uuid) to authenticated, service_role;
grant execute on function public.admin_delete_match(uuid) to authenticated, service_role;
grant execute on function public.register_for_match(uuid, uuid) to authenticated, service_role;
grant execute on function public.cancel_match_registration(uuid) to authenticated, service_role;
