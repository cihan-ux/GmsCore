package dev.cihan.signatureprobe;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
 private TextView view;
 private String report="";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private static final int EXPORT=33;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout col=new LinearLayout(this);
  col.setOrientation(LinearLayout.VERTICAL);
  col.setPadding(20,20,20,20);
  col.setBackgroundColor(Color.rgb(245,247,251));
  TextView title=new TextView(this);
  title.setText("Signature Probe v2.0");
  title.setTextSize(22);
  title.setTextColor(Color.BLACK);
  col.addView(title);
  Button scan=button("Yeniden tara",col);
  Button bridge=button("Huawei servisine bağlan",col);
  Button secondary=button("İkinci işlemde tara",col);
  Button save=button("TXT kaydet",col);
  Button share=button("Paylaş",col);
  ScrollView scroll=new ScrollView(this);
  col.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  view=new TextView(this);
  view.setTextSize(12);
  view.setTextIsSelectable(true);
  view.setPadding(8,8,8,8);
  view.setTextColor(Color.BLACK);
  scroll.addView(view);
  setContentView(col);
  scan.setOnClickListener(v->scan());
  bridge.setOnClickListener(v->testBridge(bridge));
  secondary.setOnClickListener(v->testSecondary(secondary));
  save.setOnClickListener(v->{
   Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
   i.setType("text/plain");
   i.addCategory(Intent.CATEGORY_OPENABLE);
   i.putExtra(Intent.EXTRA_TITLE,"signature-probe-v2-report.txt");
   startActivityForResult(i,EXPORT);
  });
  share.setOnClickListener(v->{
   Intent i=new Intent(Intent.ACTION_SEND);
   i.setType("text/plain");
   i.putExtra(Intent.EXTRA_TEXT,report);
   startActivity(Intent.createChooser(i,"Raporu paylaş"));
  });
  scan();
 }

 private Button button(String title,LinearLayout parent) {
  Button b=new Button(this);
  b.setText(title);
  b.setAllCaps(false);
  parent.addView(b);
  return b;
 }
 private void scan(){report=Probe.scan(this);view.setText(report);}
 private void append(String message){report+="\n"+message+"\n";view.setText(report);}

 private void testBridge(Button trigger){
  trigger.setEnabled(false);
  final boolean[] finished={false}, bound={false};
  final ServiceConnection[] conn={null};
  java.util.function.Consumer<String> done=text->{
   if(finished[0])return;
   finished[0]=true;
   if(bound[0])try{unbindService(conn[0]);}catch(Exception ignored){}
   trigger.setEnabled(true);
   append("=== HUAWEI SERVİS BAĞLANTISI ===\n"+text+
    "\nBaşarılı bağlantı PackageManager firmware entegrasyonunu KANITLAMAZ. querySignature çağrılmadı.");
  };
  conn[0]=new ServiceConnection(){
   @Override public void onServiceConnected(ComponentName name, IBinder binder) {
    try {done.accept("bindService: BAŞARILI; Binder descriptor: "+binder.getInterfaceDescriptor());}
    catch(Exception ex){done.accept("Binder descriptor hatası: "+ex.getClass().getSimpleName());}
   }
   @Override public void onServiceDisconnected(ComponentName name){done.accept("Bağlantı kesildi");}
   @Override public void onNullBinding(ComponentName name){done.accept("Null Binder döndü");}
  };
  try {
   Intent i=new Intent("com.huawei.signature.diff")
    .setComponent(new ComponentName(Probe.GMS,Probe.BRIDGE));
   bound[0]=bindService(i,conn[0],Context.BIND_AUTO_CREATE);
   if(!bound[0])done.accept("bindService: false");
   else handler.postDelayed(()->done.accept("6 saniyede yanıt yok"),6000);
  }catch(Exception e){done.accept("bindService hatası: "+e.getClass().getSimpleName());}
 }

 private void testSecondary(Button trigger){
  trigger.setEnabled(false);
  final boolean[] finished={false}, bound={false};
  final ServiceConnection[] conn={null};
  java.util.function.Consumer<String> done=text->{
   if(finished[0])return;
   finished[0]=true;
   if(bound[0])try{unbindService(conn[0]);}catch(Exception ignored){}
   trigger.setEnabled(true);
   append(text);
  };
  Messenger local=new Messenger(new Handler(Looper.getMainLooper(),msg->{
   if(msg.what==SecondProcessService.REPLY){
    done.accept(msg.getData().getString("text","Yanıt boş"));
    return true;
   }
   return false;
  }));
  conn[0]=new ServiceConnection(){
   @Override public void onServiceConnected(ComponentName name, IBinder binder){
    try {
     Message m=Message.obtain(null,SecondProcessService.REQUEST);
     m.replyTo=local;
     new Messenger(binder).send(m);
    }catch(Exception e){done.accept("İkinci işlem sorgu hatası: "+e.getClass().getSimpleName());}
   }
   @Override public void onServiceDisconnected(ComponentName name){done.accept("İkinci işlem bağlantısı kesildi");}
  };
  try{
   bound[0]=bindService(new Intent(this,SecondProcessService.class),conn[0],Context.BIND_AUTO_CREATE);
   if(!bound[0])done.accept("İkinci işleme bağlanılamadı");
   else handler.postDelayed(()->done.accept("İkinci işlem zaman aşımı"),6000);
  }catch(Exception e){done.accept("İkinci işlem hata: "+e.getClass().getSimpleName());}
 }

 @Override protected void onActivityResult(int req,int code,Intent data){
  super.onActivityResult(req,code,data);
  if(req!=EXPORT||code!=RESULT_OK||data==null)return;
  Uri uri=data.getData();
  if(uri==null)return;
  try(OutputStream s=getContentResolver().openOutputStream(uri)){
   if(s==null)throw new IllegalStateException("Çıktı açılamadı");
   s.write(report.getBytes(StandardCharsets.UTF_8));
   Toast.makeText(this,"Rapor kaydedildi",Toast.LENGTH_SHORT).show();
  }catch(Exception e){
   Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
  }
 }
}
