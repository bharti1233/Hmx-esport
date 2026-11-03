-- Allow a player to reply to their own support ticket. Staff replies continue
-- to be written with the service role, which bypasses RLS.
create policy support_messages_insert_own on public.support_messages
    for insert to authenticated
    with check (
        sender_id = auth.uid()
        and exists (
            select 1 from public.support_tickets t
            where t.id = support_messages.ticket_id and t.user_id = auth.uid()
        )
    );

grant insert (ticket_id, sender_id, body) on public.support_messages to authenticated;
