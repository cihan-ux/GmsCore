package dev.cihan.signatureprobe;
import android.app.Service;
import android.content.Intent;
import android.os.*;

public final class SecondProcessService extends Service {
 static final int REQUEST=1, REPLY=2;
 private final Messenger incoming=new Messenger(new Handler(Looper.getMainLooper(), m -> {
  if(m.what!=REQUEST||m.replyTo==null)return false;
  Message reply=Message.obtain(null,REPLY);
  Bundle b=new Bundle();b.putString("text",Probe.secondary(this));reply.setData(b);
  try{m.replyTo.send(reply);}catch(RemoteException ignored){}
  return true;
 }));
 @Override public IBinder onBind(Intent i){return incoming.getBinder();}
}
