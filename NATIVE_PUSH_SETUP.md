# KontorKaffe – Android push
Use a dedicated Firebase project, no Analytics or customer SDK. Android package no.kontorkaffe.barista.
Enable FCM HTTP v1, create a dedicated service account permitted to send messages in that project only. In Supabase Edge Function Secrets of KontorKaffe ONLY set:
- KK_FCM_ANDROID_CONFIG = JSON object with appId (mobilesdk_app_id), apiKey (current_key), projectId (project_id), senderId (project_number as string). These public client identifiers are returned to authenticated native admin only.
- KK_FCM_SERVICE_ACCOUNT = service account JSON. Never place this secret in GitHub, APK, chat or client configuration.
Apply operational_native_push.sql before deploying the updated edge function. Do not enable other Firebase services.
Then open APK 2.7, log in, Drift > Slå på appvarsling, grant notifications, test with screen locked and app swiped away. Android force stop blocks FCM until relaunched; network, system permissions and battery restrictions affect delivery.
Only generic order/test payload. No member name or drink. Device token + hashed random installation secret are private and removable. Token rotation tries scoped update; after failed rotation register again. Browser push remains separate.
