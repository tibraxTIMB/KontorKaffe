package no.kontorkaffe.barista;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.net.URI;

public class MainActivity extends Activity {
 private static final int GREEN=Color.rgb(79,121,57);
 private static final int PAPER=Color.rgb(255,254,250);
 private static final int GOLD=Color.rgb(225,230,222);
 private void styleButton(Button b, boolean primary){android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,primary?new int[]{Color.rgb(213,39,40),Color.rgb(166,13,22)}:new int[]{Color.rgb(94,133,67),Color.rgb(67,106,49)});bg.setCornerRadius(14);bg.setStroke(2,GOLD);b.setBackground(bg);b.setTextColor(PAPER);b.setAllCaps(false);b.setMinHeight(56);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,12,0,0);b.setLayoutParams(lp);}
 private SharedPreferences prefs;
 private EditText input;
 private TextView status;
 private String validated(String raw){
  try {
   URI u=new URI(raw.trim());
   if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getPort()!=-1)return null;
   String host=u.getHost().toLowerCase(java.util.Locale.ROOT);
   if(host.endsWith(".github.io")||host.equals("github.io")||host.equals("localhost")||host.indexOf('.')<0)return null;
   String path=u.getPath();
   if(path==null||path.isEmpty()||path.equals("/")||path.equals("/admin")||path.equals("/admin/"))return "https://"+host+"/admin/";
   return null;
  } catch(Exception e){return null;}
 }
 private void open(String url){
  try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
  catch(ActivityNotFoundException e){status.setText("Ingen nettlesar funnen.");}
 }
 private void saveAndOpen(){
  String u=validated(input.getText().toString());
  if(u==null){status.setText("Bruk ei eiga HTTPS-adresse for KontorKaffe, utan ekstra sti eller parameter.");return;}
  new android.app.AlertDialog.Builder(this).setTitle("Stadfest KontorKaffe-adressa").setMessage("Bruk berre di eiga, publiserte KontorKaffe-adresse.\n\n"+u).setNegativeButton("Avbryt",null).setPositiveButton("Lagre og opne",(d,w)->{prefs.edit().putString("admin_url",u).apply();input.setText(u);status.setText("Adresse lagra.");open(u);}).show();
 }
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  prefs=getSharedPreferences("kontorkaffe_launcher",MODE_PRIVATE);
  getWindow().setStatusBarColor(GREEN);
  getWindow().setNavigationBarColor(GREEN);
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,80,32,32);box.setGravity(Gravity.CENTER_HORIZONTAL);box.setBackgroundColor(GREEN);
  ImageView icon=new ImageView(this);icon.setImageResource(no.kontorkaffe.barista.R.drawable.ic_coffee);box.addView(icon,new LinearLayout.LayoutParams(160,160));
  TextView title=new TextView(this);title.setText("KontorKaffe");title.setTextSize(32);title.setTextColor(PAPER);title.setTypeface(android.graphics.Typeface.SERIF);title.setGravity(Gravity.CENTER);box.addView(title);
  TextView subtitle=new TextView(this);subtitle.setText("Barista · Edizione Firenze · Caffè su invito");subtitle.setTextColor(GOLD);subtitle.setGravity(Gravity.CENTER);box.addView(subtitle);
  TextView explain=new TextView(this);explain.setText("\nPrivat administratorflate. Opnar den sikre nettappen i nettlesaren, der pushvarsling og utskrift vert handterte. Første gong: legg inn den separate KontorKaffe-adressa.");explain.setTextColor(PAPER);box.addView(explain);
  input=new EditText(this);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setTextColor(PAPER);input.setHintTextColor(GOLD);input.setHint("https://di-kontorkaffe-adresse");input.setText(prefs.getString("admin_url",""));box.addView(input,new LinearLayout.LayoutParams(-1,-2));
  Button button=new Button(this);button.setText("Lagre adresse og opne");button.setOnClickListener(v->saveAndOpen());styleButton(button,true);box.addView(button);
  Button direct=new Button(this);direct.setText("Opne masterappen");direct.setOnClickListener(v->{String u=validated(prefs.getString("admin_url",""));if(u==null)status.setText("KontorKaffe må publiserast og adressa lagrast først.");else open(u);});styleButton(direct,false);box.addView(direct);
  Button clear=new Button(this);clear.setText("Gløym lagra adresse");clear.setOnClickListener(v->{prefs.edit().remove("admin_url").apply();input.setText("");status.setText("Adresse fjerna.");});styleButton(clear,false);box.addView(clear);
  status=new TextView(this);status.setTextColor(PAPER);box.addView(status);
  ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
 }
}
