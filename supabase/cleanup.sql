-- Only in new dedicated database.
create extension if not exists pg_cron;
select cron.schedule('kontorkaffe-cleanup','*/15 * * * *', $$select public.kk_action('admin_state','{}'::jsonb);$$);
