-- 0021: category match feed support.
--
-- The public category screen filters matches by `category_id`, publication,
-- archive state and the canonical feed status, ordered by `scheduled_at`.
-- These are additive performance indexes only: no policy, grant, RPC or data
-- change is made here, so existing RLS and admin authority are untouched.
--
-- `category_id` remains the single authoritative relationship between a match
-- and a category. There is no duplicate category name/label column on matches.

create index if not exists matches_category_feed_idx
    on public.matches (category_id, status, scheduled_at)
    where is_published and archived_at is null;

create index if not exists matches_category_completed_idx
    on public.matches (category_id, scheduled_at desc)
    where is_published
      and archived_at is null
      and status = 'completed';
