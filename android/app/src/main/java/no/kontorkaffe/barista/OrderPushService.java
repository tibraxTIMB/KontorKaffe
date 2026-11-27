package no.kontorkaffe.barista;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class OrderPushService extends FirebaseMessagingService {
 static final String CHANNEL="kk_orders";
 static void channel(Context c){NotificationManager n=c.getSystemService(NotificationManager.class);NotificationChannel ch=new NotificationChannel(CHANNEL,"Kaffibestillingar",NotificationManager.IMPORTANCE_HIGH);ch.enableVibration(true);ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);n.createNotificationChannel(ch);}
 @Override public void onMessageReceived(RemoteMessage m){
  if(!PushApplication.prefs(this).getBoolean("enabled",false))return;
  String kind=m.getData().get("kind");if(!"order".equals(kind)&&!"test".equals(kind))return;
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
  Intent i=new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent tap=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_coffee).setContentTitle("KontorKaffe").setContentText("test".equals(kind)?"Testvarsel frå appen":"Ny kaffibestilling").setContentIntent(tap).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
  getSystemService(NotificationManager.class).notify("kk-"+m.getMessageId(),1,n);
 }
 @Override public void onNewToken(String token){
  var p=PushApplication.prefs(this);p.edit().putString("token",token).apply();
  if(!p.getBoolean("registered",false)||!p.getBoolean("enabled",false))return;
  // A per-installation secret can rotate only this already registered push destination.
  try{JSONObject body=new JSONObject().put("action","native_push_rotate").put("device",PushApplication.device(this)).put("token",token);
   HttpURLConnection c=(HttpURLConnection)new URL("https://rpoziaylvjhfqitfcbhm.supabase.co/functions/v1/kontorkaffe").openConnection();c.setRequestMethod("POST");c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setRequestProperty("Origin","https://appassets.androidplatform.net");c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}if(c.getResponseCode()!=200)p.edit().putBoolean("registered",false).apply();c.disconnect();
  }catch(Exception e){p.edit().putBoolean("registered",false).apply();}
 }
}
