begin;
create table if not exists public.kk_watch_sessions(session_hash text primary key check(session_hash ~ '^[a-f0-9]{64}$'),expires_at timestamptz not null);
alter table public.kk_watch_sessions enable row level security;
revoke all on public.kk_watch_sessions from public,anon,authenticated;
grant select,insert,update,delete on public.kk_watch_sessions to service_role;
create or replace function public.kk_watch(a text,p jsonb) returns jsonb language plpgsql set search_path='' as $$
declare m text; h text:=p->>'session_hash'; pending jsonb;
begin
 perform pg_catalog.pg_advisory_xact_lock(790221);
 delete from public.kk_watch_sessions where expires_at<=now();
 if h is null or h !~ '^[a-f0-9]{64}$' then return jsonb_build_object('expired',true);end if;
 select mode into m from public.kk_settings where id=1;
 if a='start' then
  if m='closed' then return jsonb_build_object('ok',false);end if;
  delete from public.kk_watch_sessions;
  insert into public.kk_watch_sessions values(h,now()+interval '6 hours');
  return jsonb_build_object('ok',true);
 elsif a='native_watch_stop' then
  delete from public.kk_watch_sessions where session_hash=h;
  return jsonb_build_object('ok',true);
 elsif a='native_watch_state' then
  if not exists(select 1 from public.kk_watch_sessions where session_hash=h and expires_at>now()) then return jsonb_build_object('expired',true);end if;
  if m='closed' then delete from public.kk_watch_sessions where session_hash=h;return jsonb_build_object('mode','closed','pending','[]'::jsonb);end if;
  select coalesce(jsonb_agg(id),'[]'::jsonb) into pending from public.kk_orders where status='pending' and pickup>now();
  return jsonb_build_object('mode',m,'pending',pending);
 end if;
 return jsonb_build_object('expired',true);
end $$;
revoke all on function public.kk_watch(text,jsonb) from public,anon,authenticated;
grant execute on function public.kk_watch(text,jsonb) to service_role;
commit;
