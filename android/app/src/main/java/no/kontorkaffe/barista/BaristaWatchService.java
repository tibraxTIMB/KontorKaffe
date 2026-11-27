package no.kontorkaffe.barista;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class BaristaWatchService extends Service {
 static final String API="https://rpoziaylvjhfqitfcbhm.supabase.co/functions/v1/kontorkaffe";
 static final String WATCH="kk_watch", ORDERS="kk_orders";
 static final int RUN=71,ALERT=72,END=73;
 static volatile boolean running=false;
 static volatile String status="Av",session="";
 static volatile long lastSuccess=0;
 private ScheduledExecutorService worker;
 private final Set<String> seen=new HashSet<>();
 private boolean first=true;
 private long deadline;
 private NotificationManager nm;
 static void channels(Context c){NotificationManager n=c.getSystemService(NotificationManager.class);NotificationChannel w=new NotificationChannel(WATCH,"Baristamodus",NotificationManager.IMPORTANCE_LOW);w.setShowBadge(false);n.createNotificationChannel(w);NotificationChannel o=new NotificationChannel(ORDERS,"Kaffibestillingar",NotificationManager.IMPORTANCE_HIGH);o.enableVibration(true);o.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);n.createNotificationChannel(o);}
 private PendingIntent open(){return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 private Notification notice(String text){Intent stop=new Intent(this,BaristaWatchService.class).setAction("stop");PendingIntent off=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,WATCH).setSmallIcon(R.drawable.ic_coffee).setContentTitle("KontorKaffe · Baristamodus").setContentText(text).setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"Stopp",off).build()).build();}
 private void alert(String title,String text,int id){if(!nm.areNotificationsEnabled())return;nm.notify(id,new Notification.Builder(this,ORDERS).setSmallIcon(R.drawable.ic_coffee).setContentTitle(title).setContentText(text).setContentIntent(open()).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());}
 @Override public void onCreate(){super.onCreate();nm=getSystemService(NotificationManager.class);channels(this);}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null||"stop".equals(intent.getAction())){stopWatch("Av",false);return START_NOT_STICKY;}
  if("test".equals(intent.getAction())){if(running)alert("KontorKaffe","Testvarsel frå appen",ALERT);return START_NOT_STICKY;}
  String token=intent.getStringExtra("session");if(token==null||!token.matches("[a-f0-9]{64}")){stopSelf();return START_NOT_STICKY;}
  if(worker!=null)worker.shutdownNow();session=token;seen.clear();first=true;lastSuccess=0;deadline=SystemClock.elapsedRealtime()+5*60*60*1000L+55*60*1000L;running=true;status="Koplar til";
  try{startForeground(RUN,notice("Koplar til kaffibaren"));}catch(RuntimeException ex){running=false;session="";status="Android stoppa oppstarten";stopSelf();return START_NOT_STICKY;}
  worker=Executors.newSingleThreadScheduledExecutor();worker.scheduleWithFixedDelay(this::poll,0,15,TimeUnit.SECONDS);return START_NOT_STICKY;
 }
 static JSONObject request(String action,String token)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API).openConnection();try{c.setRequestMethod("POST");c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Origin","https://appassets.androidplatform.net");c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);byte[] data=new JSONObject().put("action",action).put("session",token).toString().getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(data);}int code=c.getResponseCode();if(code==401||code==403||code==410)return new JSONObject().put("expired",true);if(code!=200)throw new IOException("HTTP "+code);try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>32768)throw new IOException("Response too large");}return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}}
 private void poll(){String token=session;if(!running||token.isEmpty())return;try{
  if(SystemClock.elapsedRealtime()>=deadline){new Handler(Looper.getMainLooper()).post(()->stopWatch("Tidsgrensa er nådd. Start på nytt.",true));return;}
  JSONObject d=request("native_watch_state",token);if(!running||!token.equals(session))return;
  if(d.optBoolean("expired")||"closed".equals(d.optString("mode"))){new Handler(Looper.getMainLooper()).post(()->stopWatch(d.optBoolean("expired")?"Økta er utløpt":"Kaffibaren er stengd",d.optBoolean("expired")));return;}
  JSONArray ids=d.getJSONArray("pending");Set<String> now=new HashSet<>();int fresh=0;for(int i=0;i<ids.length();i++){String id=ids.getString(i);now.add(id);if(!seen.contains(id))fresh++;}
  seen.clear();seen.addAll(now);lastSuccess=System.currentTimeMillis();status="Aktiv";nm.notify(RUN,notice("Aktiv · "+now.size()+" ventande"));if(fresh>0)alert("KontorKaffe",fresh==1?"Ei ny kaffibestilling":fresh+" nye kaffibestillingar",ALERT);first=false;
 }catch(Exception e){if(!running||!token.equals(session))return;status="Mellombels nettutfall";nm.notify(RUN,notice("Ingen kontakt – prøver igjen"));}}
 private void stopWatch(String why,boolean warn){String old=session;running=false;session="";status=why;if(worker!=null){worker.shutdownNow();worker=null;}if(!old.isEmpty())new Thread(()->{try{request("native_watch_stop",old);}catch(Exception ignored){}}).start();stopForeground(STOP_FOREGROUND_REMOVE);if(warn)alert("KontorKaffe",why,END);stopSelf();}
 @Override public void onTimeout(int startId,int fgsType){stopWatch("Android-tidsgrensa er nådd. Start på nytt.",true);}
 @Override public void onDestroy(){running=false;session="";if(worker!=null)worker.shutdownNow();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
