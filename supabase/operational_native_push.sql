begin;
create table if not exists public.kk_native_push(device_hash text primary key check(device_hash ~ '^[a-f0-9]{64}$'),token text not null unique check(length(token) between 20 and 4096),updated_at timestamptz not null default now());
alter table public.kk_native_push enable row level security;
revoke all on public.kk_native_push from public,anon,authenticated;
grant select,insert,update,delete on public.kk_native_push to service_role;
commit;
