# Launch checklist – KontorKaffe only

Database: new dedicated project rpoziaylvjhfqitfcbhm (Frankfurt). Initial schema and periodic cleanup are applied. Edge Function kontorkaffe v1 deployed; fails closed until configured. No other project is used.

1. Create a separate static website on Cloudflare Pages or equivalent with its own HTTPS origin. GitHub repository tibraxTIMB/KontorKaffe, branch main, no framework, no build command, output directory site. Do not use a shared github.io origin.
2. In the KontorKaffe project only, open Edge Functions > Secrets. Set KK_SITE_ORIGIN to the exact HTTPS origin (no trailing slash) and KK_SITE_URL to the same origin. Generate a unique random 32-byte hex admin key locally (password manager or openssl rand -hex 32); enter it as KK_ADMIN_KEY. Never commit it or share it in chat. Keep it in a private password manager. For web push, generate unique VAPID keys and configure KK_VAPID_PUBLIC, KK_VAPID_PRIVATE and KK_VAPID_SUBJECT (private contact URI). Do not reuse any keys. The connector cannot set secrets or provision external hosting.
3. Open the new /admin/ address in a browser. Confirm unauthenticated requests cannot access the dashboard or RPC, wrong QR cannot activate, new QR requires physical browser approval, single active order, rescan and refunds. Test two devices, expiry and renewals. Check actual push delivery with the screen locked. Leave the bar closed until tests pass.
4. Create all eight cards in one admin session, print in 100% scale on A4 and check with a ruler. Do not use fit-to-page. Verify scans before handing out. Cards contain secret URLs, so do not post them publicly.
5. Android test APK is a browser launcher. Set the dedicated site address. Use PWA / browser notifications. Do not install on managed devices without workplace approval. Production signing and physical-device testing remain necessary.

Do not deploy until workplace IT policies and privacy responsibilities are clarified. No tracking, external fonts or customer permissions. Short-lived order data and no public registration. Direct public database access is denied.
