create table if not exists public.kk_host_config (
 id integer primary key check(id=1),
 origin text not null check(length(origin) between 12 and 253),
 vapid_public text not null,
 vapid_private text not null,
 created_at timestamptz not null default now()
);
alter table public.kk_host_config enable row level security;
revoke all on public.kk_host_config from public, anon, authenticated;
grant select,insert on public.kk_host_config to service_role;
