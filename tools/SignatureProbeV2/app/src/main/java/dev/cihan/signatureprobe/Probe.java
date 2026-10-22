package dev.cihan.signatureprobe;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.security.MessageDigest;
import java.security.cert.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Probe {
 static final String GMS="com.google.android.gms", STORE="com.android.vending", GMAIL="com.google.android.gm";
 static final String BRIDGE="com.huawei.signature.diff.SignatureService";
 static final String[] PKGS={GMS,STORE,"com.google.android.gsf",GMAIL,"app.revanced.android.gms","android"};
 static final String PLAY_SHA="F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83";
 static byte[] fromHex(String s) {
  byte[] r=new byte[s.length()/2];
  for(int i=0;i<r.length;i++)r[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
  return r;
 }
 static String sha(byte[] b) throws Exception {
  byte[] d=MessageDigest.getInstance("SHA-256").digest(b);
  StringBuilder s=new StringBuilder();
  for(byte x:d)s.append(String.format(Locale.US,"%02X",x&255));
  return s.toString();
 }
 static String cert(Signature s) {
  try {
   String out=sha(s.toByteArray());
   X509Certificate c=(X509Certificate)CertificateFactory.getInstance("X.509")
     .generateCertificate(new ByteArrayInputStream(s.toByteArray()));
   return out+"; konu="+c.getSubjectX500Principal().getName();
  } catch(Exception e){return "Sertifika hatası: "+e.getClass().getSimpleName();}
 }
 static String scan(Context context) {
  PackageManager pm=context.getPackageManager();
  StringBuilder b=new StringBuilder();
  b.append("PACKAGE MANAGER SIGNATURE PROBE v2.0\n");
  b.append("Tarama: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append("\n");
  b.append("Cihaz: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" Android ").append(Build.VERSION.RELEASE).append("\n");
  b.append("İşlem: ").append(android.app.Application.getProcessName()).append("\n");
  b.append("SINIR: Bu yalnızca test uygulamasının gördüğü PackageManager sonucudur. Gmail'in gördüğünü veya firmware entegrasyonunu kanıtlamaz.\n\n");
  for(String pkg:PKGS) {
   b.append("=== ").append(pkg).append(" ===\n");
   try {
    PackageInfo p=pm.getPackageInfo(pkg,PackageManager.GET_SIGNATURES|PackageManager.GET_SIGNING_CERTIFICATES);
    b.append("Sürüm: ").append(p.versionName).append("\n");
    if(p.signatures!=null)for(Signature s:p.signatures)b.append("GET_SIGNATURES: ").append(cert(s)).append("\n");
    if(p.signingInfo!=null){
     b.append("Çoklu imzalayan: ").append(p.signingInfo.hasMultipleSigners()).append("\n");
     Signature[] signers=p.signingInfo.getApkContentsSigners();
     if(signers!=null)for(Signature s:signers)b.append("GET_SIGNING_CERTIFICATES: ").append(cert(s)).append("\n");
    }
    b.append("hasSigningCertificate(Play Store referansı): ")
      .append(pm.hasSigningCertificate(pkg,fromHex(PLAY_SHA),PackageManager.CERT_INPUT_SHA256)).append("\n");
   } catch(PackageManager.NameNotFoundException ex){b.append("Kurulu değil veya görünmüyor\n");
   } catch(Exception ex){b.append("Hata: ").append(ex.getClass().getSimpleName()).append("\n");}
   b.append("\n");
  }
  b.append("=== PackageManager.checkSignatures ===\n");
  for(String[] pair:new String[][]{{GMS,STORE},{GMS,"com.google.android.gsf"},{GMS,GMAIL},{STORE,GMAIL}}) {
   int v=pm.checkSignatures(pair[0],pair[1]);
   b.append(pair[0]).append(" / ").append(pair[1]).append(": ")
     .append(v==PackageManager.SIGNATURE_MATCH?"MATCH":v==PackageManager.SIGNATURE_NO_MATCH?"NO_MATCH":v==PackageManager.SIGNATURE_UNKNOWN_PACKAGE?"UNKNOWN_PACKAGE":"kod "+v).append("\n");
  }
  b.append("\n=== Gmail görünürlük ===\n");
  try {
   PackageInfo p=pm.getPackageInfo(GMAIL,0);
   b.append("Normal sorgu: ").append(p.versionName).append("\n");
  } catch(Exception e){b.append("Normal sorgu: bulunamadı\n");}
  try {
   PackageInfo p=pm.getPackageInfo(GMAIL,PackageManager.MATCH_UNINSTALLED_PACKAGES);
   b.append("MATCH_UNINSTALLED_PACKAGES: kayıt mevcut, installed=")
    .append(p.applicationInfo!=null&&(p.applicationInfo.flags&ApplicationInfo.FLAG_INSTALLED)!=0).append("\n");
  } catch(Exception e){b.append("MATCH_UNINSTALLED_PACKAGES: bulunamadı\n");}
  boolean visible=false;
  try{for(PackageInfo p:pm.getInstalledPackages(0))if(GMAIL.equals(p.packageName))visible=true;}catch(Exception ignored){}
  b.append("getInstalledPackages listesinde Gmail: ").append(visible).append("\n");
  b.append("Launch Intent: ").append(pm.getLaunchIntentForPackage(GMAIL)!=null).append("\n\n");
  b.append("=== Huawei Signature Bridge bileşenleri ===\n");
  try {
   ServiceInfo si=pm.getServiceInfo(new ComponentName(GMS,BRIDGE),0);
   b.append("Servis tanımlı, enabled=").append(si.enabled).append(", exported=").append(si.exported)
    .append(", permission=").append(si.permission).append("\n");
   ResolveInfo r=pm.resolveService(new Intent("com.huawei.signature.diff").setPackage(GMS),0);
   b.append("resolveService: ").append(r!=null&&r.serviceInfo!=null?r.serviceInfo.name:"yok").append("\n");
  }catch(Exception ex){b.append("Servis bulunamadı/erişilemiyor\n");}
  for(ComponentName c:new ComponentName[]{
   new ComponentName(GMS,"com.huawei.signature.diff.InitProvider"),
   new ComponentName(GMS,"com.huawei.signature.diff.InitReceiver")}) {
    try {
     if(c.getClassName().endsWith("InitProvider"))pm.getProviderInfo(c,0);else pm.getReceiverInfo(c,0);
     b.append(c.getClassName()).append(": mevcut\n");
    } catch(Exception ex){b.append(c.getClassName()).append(": görünmüyor\n");}
  }
  b.append("\nİsteğe bağlı Binder ve ikinci işlem düğmeleri ek ölçüm yapar.\n");
  b.append("Bağlanabilir servis bulunması PackageManager'ın onu kullandığını kanıtlamaz.\n");
  return b.toString();
 }
 static String secondary(Context c) {
  StringBuilder b=new StringBuilder("=== İKİNCİ İŞLEM ===\n");
  b.append("İşlem: ").append(android.app.Application.getProcessName()).append(", UID=").append(android.os.Process.myUid()).append("\n");
  for(String pkg:new String[]{GMS,STORE,GMAIL}){
   try{
    PackageInfo p=c.getPackageManager().getPackageInfo(pkg,PackageManager.GET_SIGNING_CERTIFICATES);
    Signature[] s=p.signingInfo.getApkContentsSigners();
    b.append(pkg).append(": ").append(s.length>0?sha(s[0].toByteArray()):"imza yok").append("\n");
   }catch(Exception e){b.append(pkg).append(": bulunamadı / hata\n");}
  }
  b.append("Not: Aynı UID içindeki farklı Android işlemidir, Gmail işlemi değildir.\n");
  return b.toString();
 }
}
