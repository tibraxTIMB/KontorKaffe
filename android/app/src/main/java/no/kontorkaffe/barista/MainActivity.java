package no.kontorkaffe.barista;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.*;
import android.print.PrintAttributes;
import android.print.PrintManager;
import java.io.*;
import java.util.*;

public final class MainActivity extends Activity {
 private static final String HOST="appassets.androidplatform.net";
 private static final String START="https://"+HOST+"/kk/admin/index.html";
 private static final String API="https://rpoziaylvjhfqitfcbhm.supabase.co/functions/v1/kontorkaffe";
 private static final Set<String> FILES=new HashSet<>(Arrays.asList(
  "admin/index.html","admin/barista.css","admin/admin.js","admin/icon.svg","admin/manifest.webmanifest","style.css","common.js","config.js","assets/firenze.svg","assets/marble.svg","assets/linea-photo.webp"));
 private WebView web;
 private android.media.ToneGenerator tone;
 private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 private String mime(String p){if(p.endsWith(".html"))return "text/html";if(p.endsWith(".js"))return "text/javascript";if(p.endsWith(".css"))return "text/css";if(p.endsWith(".svg"))return "image/svg+xml";if(p.endsWith(".webp"))return "image/webp";return "application/manifest+json";}
 private boolean localMain(String url){return START.equals(url);}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
  getWindow().setStatusBarColor(Color.rgb(79,121,57));getWindow().setNavigationBarColor(Color.rgb(79,121,57));
  web=new WebView(this);web.setBackgroundColor(Color.rgb(232,236,228));web.setWebChromeClient(new WebChromeClient());
  WebView.setWebContentsDebuggingEnabled(false);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);
  web.addJavascriptInterface(new PrintBridge(),"KontorKaffeNative");
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return !req.isForMainFrame()||!localMain(req.getUrl().toString());}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
    String url=req.getUrl().toString();if(API.equals(url)&&!req.isForMainFrame())return null;
    String path=req.getUrl().getPath();
    if(!"GET".equals(req.getMethod())||!"https".equals(req.getUrl().getScheme())||!HOST.equals(req.getUrl().getHost())||req.getUrl().getPort()!=-1||path==null||!path.startsWith("/kk/"))return blocked();
    String p=path.substring(4);if(!FILES.contains(p))return blocked();
    try{return new WebResourceResponse(mime(p),"UTF-8",getAssets().open("kk/"+p));}catch(IOException e){return blocked();}
   }
   @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();}
  });
  setContentView(web);
  web.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());}return insets;});
  web.loadUrl(START);
 }
 private final class PrintBridge{
  @JavascriptInterface public void playOrderSound(){runOnUiThread(()->{if(web==null||!localMain(web.getUrl()))return;try{if(tone==null)tone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,75);tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK,350);}catch(RuntimeException ignored){}});}
  @JavascriptInterface public void openBrowserAdmin(){runOnUiThread(()->{if(web==null||!localMain(web.getUrl()))return;try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://kontorkaffe.github.io/admin/")));}catch(android.content.ActivityNotFoundException ignored){}});}
  @JavascriptInterface public void printCards(){runOnUiThread(()->{if(web==null||!localMain(web.getUrl()))return;PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);if(pm!=null)pm.print("KontorKaffe medlemskort",web.createPrintDocumentAdapter("KontorKaffe medlemskort"),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());});}
 }
 @Override protected void onDestroy(){if(tone!=null){tone.release();tone=null;}if(web!=null){web.removeJavascriptInterface("KontorKaffeNative");web.destroy();web=null;}super.onDestroy();}
}
