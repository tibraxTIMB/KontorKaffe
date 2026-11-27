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
 private static final int WATCH_PERMISSION=702, EXISTING_PDF=703;
 private String pendingWatch="",watchError="",pendingCardRevision="";
 private void launchWatch(String token){try{android.content.Intent i=new android.content.Intent(this,BaristaWatchService.class).putExtra("session",token);startForegroundService(i);}catch(RuntimeException e){watchError="Android tillèt ikkje oppstart. Opna appen og prøv igjen.";}}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==WATCH_PERMISSION){String token=pendingWatch;pendingWatch="";if(g.length>0&&g[0]==android.content.pm.PackageManager.PERMISSION_GRANTED&&!token.isEmpty())launchWatch(token);else watchError="Du må tillata varsling på telefonen.";}}
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
 private android.util.AtomicFile cardArchive(){return new android.util.AtomicFile(new File(getNoBackupFilesDir(),"kontorkaffe-cards-v1.enc"));}
 private static final String CARD_KEY="kontorkaffe_card_archive_v1";
 private javax.crypto.SecretKey archiveKey(boolean create)throws Exception{
  java.security.KeyStore store=java.security.KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(CARD_KEY)){
   if(!create)throw new IOException("Archive key missing");
   javax.crypto.KeyGenerator generator=javax.crypto.KeyGenerator.getInstance("AES","AndroidKeyStore");
   generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(CARD_KEY,android.security.keystore.KeyProperties.PURPOSE_ENCRYPT|android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
   generator.generateKey();
  }
  return (javax.crypto.SecretKey)store.getKey(CARD_KEY,null);
 }
 private boolean validRevision(String r){return r!=null&&r.matches("[a-f0-9]{64}");}
 private String savedRevision(){try(DataInputStream in=new DataInputStream(cardArchive().openRead())){if(in.readInt()!=1)return "";String r=in.readUTF();return validRevision(r)?r:"";}catch(Exception e){return "";}}
 private void saveArchive(byte[] pdf,String revision)throws Exception{
  javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,archiveKey(true));
  cipher.updateAAD(revision.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(pdf);android.util.AtomicFile file=cardArchive();FileOutputStream out=null;
  try{out=file.startWrite();DataOutputStream data=new DataOutputStream(out);data.writeInt(1);data.writeUTF(revision);data.writeInt(iv.length);data.write(iv);data.writeInt(encrypted.length);data.write(encrypted);data.flush();file.finishWrite(out);}
  catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
 }
 private byte[] readArchive(String revision)throws Exception{
  try(DataInputStream in=new DataInputStream(cardArchive().openRead())){
   if(in.readInt()!=1||!revision.equals(in.readUTF()))throw new IOException("Stale archive");
   int ivLength=in.readInt();if(ivLength!=12)throw new IOException("Invalid IV");byte[] iv=new byte[ivLength];in.readFully(iv);
   int size=in.readInt();if(size<21||size>5*1024*1024+16)throw new IOException("Invalid size");byte[] encrypted=new byte[size];in.readFully(encrypted);if(in.read()!=-1)throw new IOException("Invalid archive");
   javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(javax.crypto.Cipher.DECRYPT_MODE,archiveKey(false),new javax.crypto.spec.GCMParameterSpec(128,iv));cipher.updateAAD(revision.getBytes(java.nio.charset.StandardCharsets.US_ASCII));return cipher.doFinal(encrypted);
  }
 }
 private void updateArchiveView(){if(web!=null&&localMain(web.getUrl()))web.evaluateJavascript("window.refreshCardArchive && window.refreshCardArchive()",null);}
 private void chooseCardSheet(String revision){
  if(web==null||!localMain(web.getUrl())||!validRevision(revision))return;
  try{pendingCardRevision=revision;android.content.Intent pick=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(android.content.Intent.CATEGORY_OPENABLE);startActivityForResult(pick,EXISTING_PDF);}
  catch(android.content.ActivityNotFoundException e){pendingCardRevision="";toast("Ingen dokumentveljar tilgjengeleg.");}
 }
 private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_LONG).show();}
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){
  super.onActivityResult(request,result,data);
  if(request!=EXISTING_PDF)return;
  String revision=pendingCardRevision;pendingCardRevision="";
  if(result!=RESULT_OK||data==null||data.getData()==null||web==null||!localMain(web.getUrl())||!validRevision(revision))return;
  android.net.Uri uri=data.getData();
  // Read the chosen document privately, never upload it or grant WebView content access.
  byte[] pdf;
  try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   if(in==null)throw new IOException("No document");
   byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>5*1024*1024)throw new IOException("Too large");}
   pdf=out.toByteArray();if(pdf.length<5||!new String(pdf,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("Not PDF");
  }catch(Exception e){toast("Vel eit gyldig PDF-kortark under 5 MB.");return;}
  // Check the selected local PDF is a single A4-sized page using Android's PDF renderer.
  File tmp=null;
  try{tmp=File.createTempFile("kk-print-", ".pdf", getCacheDir());try(FileOutputStream out=new FileOutputStream(tmp)){out.write(pdf);}
   try(android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(tmp,android.os.ParcelFileDescriptor.MODE_READ_ONLY))){if(renderer.getPageCount()!=1)throw new IOException("One page required");try(android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(0)){if(Math.abs(page.getWidth()-595)>12||Math.abs(page.getHeight()-842)>12)throw new IOException("A4 required");}}
  }catch(Exception e){toast("Kortarket må vera éi ståande A4-side.");return;}finally{if(tmp!=null)tmp.delete();}
  try{saveArchive(pdf,revision);toast("Kortarket er lagra kryptert på telefonen.");updateArchiveView();}
  catch(Exception e){toast("Kortarket kunne ikkje lagrast. Prøv igjen.");}
  finally{java.util.Arrays.fill(pdf,(byte)0);}
 }
 private void printArchive(byte[] pdf){
  PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);if(pm==null){java.util.Arrays.fill(pdf,(byte)0);toast("Utskrift er ikkje tilgjengeleg.");return;}
  pm.print("KontorKaffe – eksisterande kortark",new android.print.PrintDocumentAdapter(){
   @Override public void onLayout(PrintAttributes oldA,PrintAttributes newA,android.os.CancellationSignal cancel,LayoutResultCallback cb,Bundle extras){
    if(cancel.isCanceled()){cb.onLayoutCancelled();return;}cb.onLayoutFinished(new android.print.PrintDocumentInfo.Builder("KontorKaffe_medlemskort.pdf").setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true);
   }
   @Override public void onWrite(android.print.PageRange[] pages,android.os.ParcelFileDescriptor destination,android.os.CancellationSignal cancel,WriteResultCallback cb){
    if(cancel.isCanceled()){cb.onWriteCancelled();return;}try(FileOutputStream out=new FileOutputStream(destination.getFileDescriptor())){out.write(pdf);out.flush();if(cancel.isCanceled())cb.onWriteCancelled();else cb.onWriteFinished(new android.print.PageRange[]{new android.print.PageRange(0,0)});}catch(IOException e){cb.onWriteFailed("Klarte ikkje skriva kortarket.");}
   }
   @Override public void onFinish(){java.util.Arrays.fill(pdf,(byte)0);}
  },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
 }
 private final class PrintBridge{
  @JavascriptInterface public String savedCardSheetRevision(){return savedRevision();}
  @JavascriptInterface public void importCardSheet(String revision){runOnUiThread(()->chooseCardSheet(revision));}
  @JavascriptInterface public void printSavedCardSheet(String revision){runOnUiThread(()->{if(web==null||!localMain(web.getUrl())||!validRevision(revision))return;try{printArchive(readArchive(revision));}catch(Exception e){toast("Kortarket er utdatert eller utilgjengeleg. Vel det gyldige arket på nytt.");updateArchiveView();}});}
  @JavascriptInterface public void deleteSavedCardSheet(){runOnUiThread(()->{if(web==null||!localMain(web.getUrl()))return;cardArchive().delete();try{java.security.KeyStore store=java.security.KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(CARD_KEY);}catch(Exception ignored){}updateArchiveView();});}

  @JavascriptInterface public void startBaristaWatch(String token){runOnUiThread(()->{if(web==null||!localMain(web.getUrl())||!token.matches("[a-f0-9]{64}"))return;watchError="";if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingWatch=token;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},WATCH_PERMISSION);}else if(!getSystemService(android.app.NotificationManager.class).areNotificationsEnabled())watchError="Varsling er slått av i Android.";else launchWatch(token);});}
  @JavascriptInterface public String baristaWatchState(){try{return new org.json.JSONObject().put("running",BaristaWatchService.running).put("status",watchError.isEmpty()?BaristaWatchService.status:watchError).put("lastSuccess",BaristaWatchService.lastSuccess).toString();}catch(Exception e){return "{}";}}
  @JavascriptInterface public void stopBaristaWatch(){runOnUiThread(()->{pendingWatch="";watchError="";if(BaristaWatchService.running)startService(new android.content.Intent(MainActivity.this,BaristaWatchService.class).setAction("stop"));});}
  @JavascriptInterface public void testBaristaWatch(){runOnUiThread(()->{if(BaristaWatchService.running)startService(new android.content.Intent(MainActivity.this,BaristaWatchService.class).setAction("test"));});}

  @JavascriptInterface public void playOrderSound(){runOnUiThread(()->{if(web==null||!localMain(web.getUrl()))return;try{if(tone==null)tone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,75);tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK,350);}catch(RuntimeException ignored){}});}
  @JavascriptInterface public void openBrowserAdmin(){runOnUiThread(()->{if(web==null||!localMain(web.getUrl()))return;try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://kontorkaffe.github.io/admin/")));}catch(android.content.ActivityNotFoundException ignored){}});}
  @JavascriptInterface public void printCards(){runOnUiThread(()->{if(web==null||!localMain(web.getUrl()))return;PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);if(pm!=null)pm.print("KontorKaffe medlemskort",web.createPrintDocumentAdapter("KontorKaffe medlemskort"),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());});}
 }
 @Override protected void onDestroy(){if(tone!=null){tone.release();tone=null;}if(web!=null){web.removeJavascriptInterface("KontorKaffeNative");web.destroy();web=null;}super.onDestroy();}
}
