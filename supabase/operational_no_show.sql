-- Apply only to existing dedicated KontorKaffe DB. Preserve function privileges and all rows.
begin;
do $patch$
declare d text; old text := 'or (k=''collected'' and o.status=''ready'') then null;'; new text := 'or (k=''collected'' and o.status=''ready'') or (k=''no_show'' and o.status in (''preparing'',''ready'') and o.pickup<=now()) then null;';
begin
 select pg_get_functiondef('public.kk_action(text,jsonb)'::regprocedure) into d;
 if position(new in d)>0 then return; end if;
 if position(old in d)=0 then raise exception 'Unexpected kk_action definition'; end if;
 execute replace(d,old,new);
end $patch$;
commit;
