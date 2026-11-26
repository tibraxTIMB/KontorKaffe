-- ONLY for a new dedicated KontorKaffe Supabase project.
begin;
create extension if not exists pgcrypto with schema extensions;
create table public.kk_members (
 id integer primary key check(id between 1 and 8), name text not null default '', active boolean not null default false,
 credits integer not null default 3 check(credits between 0 and 3), qr_hash text unique, browser_hash text, pending_hash text, pending_until timestamptz
);
insert into public.kk_members(id) select generate_series(1,8);
create table public.kk_settings (id integer primary key check(id=1), mode text not null check(mode in ('open','busy','closed')));
insert into public.kk_settings values(1,'closed');
create table public.kk_orders (
 id uuid primary key default gen_random_uuid(), member_id integer not null references public.kk_members(id),
 drink text not null check(drink in ('Espresso','Long Black','Americano','Cortado','Cappuccino','Latte','Mocha','Kakao')),
 pickup timestamptz not null, status text not null default 'pending' check(status in ('pending','proposed','accepted','preparing','ready','collected','rejected','cancelled','expired','no_show')),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), request_id uuid not null,
 unique(member_id,request_id)
);
create unique index kk_one_order on public.kk_orders(member_id) where status in ('pending','proposed','accepted','preparing','ready');
create table public.kk_push (endpoint text primary key, subscription jsonb not null);
alter table public.kk_members enable row level security;
alter table public.kk_orders enable row level security;
alter table public.kk_settings enable row level security;
alter table public.kk_push enable row level security;
revoke all on public.kk_members,public.kk_orders,public.kk_settings,public.kk_push from public,anon,authenticated;
grant select,insert,update,delete on public.kk_members,public.kk_orders,public.kk_settings,public.kk_push to service_role;
create function public.kk_action(a text, p jsonb) returns jsonb language plpgsql security invoker set search_path='' as $$
declare m public.kk_members%rowtype; o public.kk_orders%rowtype; t timestamptz; k text; result jsonb; mode_now text; mid integer; n integer; fresh boolean:=false;
begin
 perform pg_catalog.pg_advisory_xact_lock(790221);
 with expired as (update public.kk_orders set status='expired',updated_at=now() where (status in ('pending','proposed') and pickup <= now()) or (status='accepted' and pickup < now()-interval '2 hours') returning member_id)
 update public.kk_members x set credits=least(3,x.credits+1) from expired e where x.id=e.member_id;
 update public.kk_orders set status='no_show',updated_at=now() where status in ('preparing','ready') and pickup < now()-interval '2 hours';
 delete from public.kk_orders where status in ('collected','rejected','cancelled','expired','no_show') and updated_at < now()-interval '24 hours';
 select mode into mode_now from public.kk_settings where id=1;
 if a='admin_state' then
  return jsonb_build_object('mode',mode_now,'members',(select jsonb_agg(jsonb_build_object('id',id,'name',name,'active',active,'credits',credits,'linked',browser_hash is not null,'pending',pending_hash is not null and pending_until>now(),'activation_code',case when pending_until>now() then substr(pending_hash,1,6) end) order by id) from public.kk_members),'orders',coalesce((select jsonb_agg(to_jsonb(q) order by q.pickup) from (select x.id,x.member_id,mb.name,x.drink,x.pickup,x.status from public.kk_orders x join public.kk_members mb on mb.id=x.member_id where x.status in ('pending','proposed','accepted','preparing','ready')) q),'[]'::jsonb));
 elsif a='admin_mode' then
  update public.kk_settings set mode=p->>'mode' where id=1; return jsonb_build_object('ok',true);
 elsif a='admin_push' then
  insert into public.kk_push(endpoint,subscription) values(p->'subscription'->>'endpoint',p->'subscription') on conflict(endpoint) do update set subscription=excluded.subscription; return jsonb_build_object('ok',true);
 elsif a in ('admin_issue','admin_approve','admin_renew','admin_block') then
  mid=(p->>'id')::integer; select * into m from public.kk_members where id=mid for update;
  if not found then raise exception 'Ukjent medlem'; end if;
  if a='admin_issue' then
   if exists(select 1 from public.kk_orders where member_id=mid and status in ('pending','proposed','accepted','preparing','ready')) then raise exception 'Avslutt aktiv bestilling først'; end if;
   if length(trim(coalesce(p->>'name',''))) not between 1 and 40 then raise exception 'Skriv eit namn (1–40 teikn)'; end if;
   update public.kk_members set name=trim(p->>'name'),active=true,credits=case when qr_hash is null then 3 else credits end,qr_hash=p->>'qr_hash',browser_hash=null,pending_hash=null,pending_until=null where id=mid;
  elsif a='admin_approve' then
   if not m.active or m.pending_hash is null or m.pending_until<=now() or coalesce(p->>'code','')<>substr(m.pending_hash,1,6) then raise exception 'Ingen gyldig aktivering'; end if;
   update public.kk_members set browser_hash=pending_hash,pending_hash=null,pending_until=null where id=mid;
  elsif a='admin_renew' then
   if not m.active or m.credits<>0 or exists(select 1 from public.kk_orders where member_id=mid and status in ('pending','proposed','accepted','preparing','ready')) then raise exception 'Fornying krev null klipp og inga aktiv bestilling'; end if;
   update public.kk_members set credits=3 where id=mid;
  else
   update public.kk_members set active=false,browser_hash=null,pending_hash=null,pending_until=null where id=mid;
   update public.kk_orders set status='cancelled',updated_at=now() where member_id=mid and status in ('pending','proposed','accepted','preparing','ready');
   get diagnostics n=row_count;
   update public.kk_members set credits=least(3,credits+n) where id=mid;
  end if;
  return jsonb_build_object('ok',true);
 elsif a='admin_order' then
  select member_id into mid from public.kk_orders where id=(p->>'id')::uuid;
  perform 1 from public.kk_members where id=mid for update;
  select * into o from public.kk_orders where id=(p->>'id')::uuid for update;
  if not found then raise exception 'Ukjent bestilling'; end if;
  k=p->>'status'; t=o.pickup;
  if k='rejected' and o.status in ('pending','proposed','accepted','preparing','ready') then
   update public.kk_members set credits=least(3,credits+1) where id=o.member_id;
  elsif k='proposed' and o.status in ('pending','accepted','proposed') then
   t=(p->>'pickup')::timestamptz;
   if t<now() or t>now()+interval '24 hours' then raise exception 'Ugyldig hentetid'; end if;
  elsif (k='accepted' and o.status='pending') or (k='preparing' and o.status='accepted') or (k='ready' and o.status in ('accepted','preparing')) or (k='collected' and o.status='ready') then null;
  else raise exception 'Ugyldig statusendring'; end if;
  update public.kk_orders set status=k,pickup=t,updated_at=now() where id=o.id; return jsonb_build_object('ok',true);
 end if;
 select * into m from public.kk_members where qr_hash=p->>'qr_hash' for update;
 if not found or not m.active then raise exception 'Kortet er ikkje gyldig'; end if;
 if m.browser_hash is distinct from p->>'browser_hash' then
  if a='activate' then
   if m.pending_hash is not null and m.pending_until>now() and m.pending_hash is distinct from p->>'browser_hash' then raise exception 'Ei anna aktivering ventar. Kontakt baristaen.'; end if;
   update public.kk_members set pending_hash=p->>'browser_hash',pending_until=now()+interval '5 minutes' where id=m.id;
   return jsonb_build_object('activation','pending','activation_code',substr(p->>'browser_hash',1,6));
  end if;
  if a='state' then return jsonb_build_object('activation',case when m.pending_hash=p->>'browser_hash' and m.pending_until>now() then 'pending' else 'required' end,'activation_code',case when m.pending_hash=p->>'browser_hash' and m.pending_until>now() then substr(m.pending_hash,1,6) end); end if;
  raise exception 'Telefonnettlesaren er ikkje godkjend';
 end if;
 if a='order' then
  select * into o from public.kk_orders where member_id=m.id and request_id=(p->>'request_id')::uuid;
  if not found then
   if mode_now='closed' then raise exception 'Kaffibaren er stengd'; end if;
   if m.credits<1 then raise exception 'Ingen klipp att'; end if;
   if exists(select 1 from public.kk_orders where member_id=m.id and status in ('pending','proposed','accepted','preparing','ready')) then raise exception 'Du har allereie ei aktiv bestilling'; end if;
   t=(p->>'pickup')::timestamptz;
   if t<=now() or t>now()+interval '24 hours' or (mode_now='busy' and t<now()+interval '30 minutes') then raise exception 'Vel eit seinare hentetidspunkt'; end if;
   insert into public.kk_orders(member_id,drink,pickup,request_id) values(m.id,p->>'drink',t,(p->>'request_id')::uuid) returning * into o;
   update public.kk_members set credits=credits-1 where id=m.id returning * into m;
   fresh=true;
  end if;
 elsif a in ('accept','cancel') then
  select * into o from public.kk_orders where member_id=m.id and id=(p->>'id')::uuid for update;
  if not found then raise exception 'Ukjend bestilling'; end if;
  if a='accept' and o.status='proposed' then update public.kk_orders set status='accepted',updated_at=now() where id=o.id;
  elsif a='cancel' and o.status in ('pending','proposed','accepted') then
   update public.kk_orders set status='cancelled',updated_at=now() where id=o.id;
   update public.kk_members set credits=least(3,credits+1) where id=m.id returning * into m;
  else raise exception 'Bestillinga kan ikkje endrast'; end if;
 elsif a not in ('state','activate') then raise exception 'Ukjend handling'; end if;
 select coalesce((select jsonb_build_object('id',id,'drink',drink,'pickup',pickup,'status',status) from public.kk_orders where member_id=m.id order by (status in ('pending','proposed','accepted','preparing','ready')) desc,created_at desc limit 1),'null'::jsonb) into result;
 return jsonb_build_object('activation','approved','name',m.name,'credits',m.credits,'mode',mode_now,'order',result,'notify',fresh);
end $$;
revoke execute on function public.kk_action(text,jsonb) from public,anon,authenticated;
grant execute on function public.kk_action(text,jsonb) to service_role;
commit;