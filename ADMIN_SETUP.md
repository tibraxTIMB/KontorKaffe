# KontorKaffe 1.6 admin setup
Create one user using Supabase Dashboard → Authentication → Users → Add user → Create user. Use your own email and chosen strong password, auto-confirm email only for your own account. No public signup flow is provided. In SQL Editor, explicitly grant only that user's UUID using:

```sql
update auth.users set raw_app_meta_data = coalesce(raw_app_meta_data,'{}'::jsonb) || '{"kontorkaffe_admin":true}'::jsonb where id = 'REPLACE_WITH_EXACT_UUID'::uuid;
```
Do not grant the role to the first account automatically. No customer accounts are required. The former KK_ADMIN_KEY is no longer used. Login uses Supabase Auth, verifies each admin request against Auth, and retains the access token only in memory. When it expires, log in again. Never share your password in chat. QR issuance and open/busy remain disabled until isolated member hosting is configured. Android native push is not implemented; polling works while open.
