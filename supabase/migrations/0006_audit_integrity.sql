-- Audit rows are immutable, so they must not be rewritten by foreign-key
-- maintenance when an auth user is deleted. Keep the actor id as a plain uuid.
alter table public.audit_logs drop constraint if exists audit_logs_actor_id_fkey;

-- Evidence rows should survive account deletion for dispute history, while the
-- uploader reference is cleared.
alter table public.result_evidence drop constraint if exists result_evidence_uploaded_by_fkey;
alter table public.result_evidence
    add constraint result_evidence_uploaded_by_fkey
    foreign key (uploaded_by) references public.profiles (id) on delete set null;
