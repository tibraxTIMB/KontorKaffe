package no.kontorkaffe.barista;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.security.SecureRandom;

public final class PushApplication extends Application {
 static final String PREF="kk_private_push";
 static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
 static String device(Context c){SharedPreferences p=prefs(c);String s=p.getString("device","");if(s.isEmpty()){byte[] b=new byte[32];new SecureRandom().nextBytes(b);StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format(java.util.Locale.ROOT,"%02x",v&255));s=x.toString();p.edit().putString("device",s).commit();}return s;}
 static synchronized boolean configure(Context c,String json){
  try{JSONObject j=new JSONObject(json);FirebaseOptions o=new FirebaseOptions.Builder().setApplicationId(j.getString("appId")).setApiKey(j.getString("apiKey")).setProjectId(j.getString("projectId")).setGcmSenderId(j.getString("senderId")).build();
   if(FirebaseApp.getApps(c).isEmpty())FirebaseApp.initializeApp(c,o);
   else if(!FirebaseApp.getInstance().getOptions().getApplicationId().equals(o.getApplicationId()))return false;
   prefs(c).edit().putString("config",json).apply();return true;
  }catch(Exception e){return false;}
 }
 @Override public void onCreate(){super.onCreate();String cfg=prefs(this).getString("config","");if(!cfg.isEmpty()&&configure(this,cfg)&&prefs(this).getBoolean("enabled",false))FirebaseMessaging.getInstance().setAutoInitEnabled(true);OrderPushService.channel(this);}
}
