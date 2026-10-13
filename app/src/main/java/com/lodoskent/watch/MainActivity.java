package com.lodoskent.watch;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int ORANGE=0xffe66d17, INK=0xff1f2937;
    private LinearLayout main;
    private WatchService service;
    private boolean bound;
    private final WatchService.Listener listener=this::render;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n, IBinder binder){
            service=((WatchService.LocalBinder)binder).service();bound=true;service.addListener(listener);render();
        }
        public void onServiceDisconnected(ComponentName n){bound=false;service=null;render();}
    };
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);
        main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(22,32,22,24);main.setBackgroundColor(0xfff4f6fa);
        scroll.addView(main);setContentView(scroll);render();
    }
    @Override public void onStart(){
        super.onStart();bindService(new Intent(this,WatchService.class),connection,BIND_AUTO_CREATE);
    }
    @Override public void onStop(){
        if(service!=null)service.removeListener(listener);
        if(bound)unbindService(connection);bound=false;service=null;super.onStop();
    }
    private boolean granted(){
        if(Build.VERSION.SDK_INT>=31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void askPermissions(){
        List<String> p=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31){
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_SCAN);
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),101);else startBackgroundConnection();
    }
    @Override public void onRequestPermissionsResult(int id,String[] p,int[] g){
        super.onRequestPermissionsResult(id,p,g);
        if(id==101){if(granted())startBackgroundConnection();else toast("Bluetooth izinleri gerekli");render();}
    }
    private void startBackgroundConnection(){
        if(!granted()){askPermissions();return;}
        Intent intent=new Intent(this,WatchService.class);
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);}
        catch(RuntimeException e){toast("Servis hatası: "+e.getClass().getSimpleName());}
    }
    private void act(Runnable task){
        if(!granted()){askPermissions();return;}
        startBackgroundConnection();
        if(service==null){toast("Servis hazırlanıyor; tekrar deneyin");return;}
        task.run();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private TextView label(String s,int size,boolean strong){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(INK);
        if(strong)t.setTypeface(null,1);t.setPadding(4,8,4,8);main.addView(t);return t;
    }
    private void button(String title,Runnable click){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ORANGE));b.setTextColor(Color.WHITE);
        main.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->click.run());
    }
    private void render(){
        if(main==null)return;main.removeAllViews();
        label("LodosKentWatch",30,true);
        label("Daniel Klein / DSMART A8U • Bağlantı test sürümü",14,false);
        WatchService.State s=service==null?null:service.snapshot();
        label("Bağlantı: "+(s==null?"Servis bekleniyor":s.phase),20,true);
        label(s==null?"Henüz işlem yapılmadı":s.detail,15,false);
        label("Saat: "+(s==null?"—":s.deviceName)+"\nGATT: "+(s!=null&&s.ready?"HAZIR":"Hazır değil")+
                "\nPil: "+(s!=null&&s.battery>=0?s.battery+"%":"Veri yok")+
                "\nNabız: "+(s!=null&&s.heartRate>0?s.heartRate+" bpm":"Veri yok")+
                "\nRSSI: "+(s!=null&&s.rssi>-127?s.rssi+" dBm":"Veri yok")+
                "\nServis/kanal: "+(s==null?"—":s.serviceInfo),16,false);
        if(!granted())button("Bluetooth izinlerini ver",this::askPermissions);
        button("Bağlantı servisini başlat",this::startBackgroundConnection);
        button("Saatleri tara",()->act(()->service.scan()));
        button("Yeniden bağlan",()->act(()->service.reconnect()));
        button("Bağlantıyı durdur",()->{if(service!=null)service.disconnect();});
        label("Bulunan cihazlar",20,true);
        if(s!=null){for(WatchService.Device d:s.devices){
            button(d.name+" ("+d.rssi+" dBm)",()->act(()->service.select(d.address)));
        }}
        label("Veri güvenliği",20,true);
        label("Üretici protokolü henüz doğrulanmadı. Adım, uyku, oksijen, tansiyon, arama ve bildirim eşitleme özellikleri şu anda etkin değildir. Burada gösterilen değerler yalnız standart GATT karakteristiklerinden okunur.",14,false);
        button("Tanılama kaydını paylaş",()->{
            if(service==null){toast("Servis hazırlanıyor");return;}
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,service.diagnostics());
            startActivity(Intent.createChooser(send,"Bağlantı raporu"));
        });
    }
}
