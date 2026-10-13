package com.lodoskent.watch;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Standalone BLE client, no proprietary application code or Internet access. */
public final class MainActivity extends Activity {
    private static final String TARGET_MAC="0C:AF:6F:E2:C4:46";
    private static final String TARGET_NAME="DSMART A8U";
    private static final UUID NUS=UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_WRITE=UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_NOTIFY=UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID AE=UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb");
    private static final UUID AE_NOTIFY=UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY=UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL=UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int REQUEST_BLE=100,REQUEST_FILE=101;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayDeque<BluetoothGattCharacteristic> toSubscribe=new ArrayDeque<>();
    private final SmableProtocol.Framer decoder=new SmableProtocol.Framer();
    private final SimpleDateFormat clock=new SimpleDateFormat("HH:mm:ss",Locale.forLanguageTag("tr-TR"));
    private final StringBuilder log=new StringBuilder();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic nusRx;
    private BluetoothGattCharacteristic nusTx;
    private boolean scanning=false,connected=false,nusReady=false;
    private int sequence=0;
    private TextView status,detail,logView;
    private Button connectButton,disconnectButton,powerButton;

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(LinearLayout root,String s,int size,int color){
        TextView t=new TextView(this);
        t.setText(s);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(0,dp(6),0,dp(6));
        root.addView(t);return t;
    }
    private Button action(LinearLayout root,String text,View.OnClickListener l){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);
        b.setOnClickListener(l);
        root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setPadding(dp(18),dp(20),dp(18),dp(22));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,249,253));
        label(root,"⌚ LodosKentWatch",25,Color.rgb(29,55,82));
        label(root,"DSMART A8U  ·  Reklamsız  ·  Üyeliksiz  ·  Çevrimdışı",14,Color.DKGRAY);
        status=label(root,"Saat bağlı değil",18,Color.rgb(10,105,100));
        detail=label(root,"Model: DSMART A8U\nMAC: "+TARGET_MAC,13,Color.DKGRAY);
        connectButton=action(root,"Bluetooth ile saati bul ve bağlan",v->startScan());
        disconnectButton=action(root,"Bağlantıyı kes",v->disconnect());
        powerButton=action(root,"Deneysel: Pil sorgusu gönder (salt okunur)",v->readPower());
        label(root,"Pil komutu, SMART-TIME PRO'nun çok modelli SDK'sinden çözüldü; bu saatin desteklediği henüz kanıtlanmadı. Diğer ayarları değiştirmez.",12,Color.DKGRAY);
        action(root,"Günlüğü TXT kaydet",v->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,"LodosKentWatch_A8U_log.txt");
            startActivityForResult(i,REQUEST_FILE);
        });
        action(root,"Günlüğü kopyala",v->{
            ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("LodosKentWatch",log.toString()));
            Toast.makeText(this,"Günlük kopyalandı",Toast.LENGTH_SHORT).show();
        });
        label(root,"CANLI SAAT HABERLEŞMESİ / HEX",14,Color.rgb(29,55,82));
        logView=label(root,"",12,Color.DKGRAY);logView.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        log("Başlatıldı. Sadece kendi saatine bağlanır; otomatik komut göndermez.");
        controls();
    }
    private void log(String message){
        String line=clock.format(new Date())+"  "+message+"\n";
        handler.post(()->{
            log.append(line);
            if(log.length()>36000)log.delete(0,log.length()-30000);
            if(logView!=null)logView.setText(log.toString());
        });
    }
    private void status(String message){handler.post(()->{if(status!=null)status.setText(message);});}
    private void controls(){
        handler.post(()->{
            connectButton.setEnabled(!scanning && !connected);
            disconnectButton.setEnabled(scanning || gatt!=null);
            powerButton.setEnabled(connected && nusReady && nusRx!=null);
        });
    }
    private String[] perms(){
        if(Build.VERSION.SDK_INT>=31)return new String[]{
            Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT};
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }
    private boolean hasPermissions(){
        for(String p:perms())if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;
        return true;
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] result){
        super.onRequestPermissionsResult(req,p,result);
        if(req==REQUEST_BLE){
            if(hasPermissions())startScan();
            else status("Bluetooth izni gerekli. Android uygulama izinlerinden izin ver.");
        }
    }
    private void startScan(){
        if(scanning || connected)return;
        if(!hasPermissions()){
            requestPermissions(perms(),REQUEST_BLE);return;
        }
        BluetoothManager manager=(BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null){status("Telefon Bluetooth LE desteklemiyor.");return;}
        try{
            if(!adapter.isEnabled()){status("Önce Bluetooth'u aç.");return;}
            scanner=adapter.getBluetoothLeScanner();
            if(scanner==null){status("Android Bluetooth tarayıcısı yok.");return;}
            scanning=true;status("DSMART A8U aranıyor…");log("Tarama başladı.");
            controls();
            scanner.startScan(null,new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            handler.postDelayed(()->{
                if(scanning){stopScan();status("Saat görünmedi. SMART-TIME PRO aktifse bağlantısını kesip yeniden dene.");}
            },15000L);
        }catch(SecurityException | IllegalStateException e){
            scanning=false;status("Bluetooth tarama hatası.");log(e.toString());controls();
        }
    }
    private final ScanCallback scanCallback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult result){
            BluetoothDevice d=result.getDevice();if(d==null)return;
            try{
                String mac=d.getAddress(),name=d.getName();
                boolean matches=TARGET_MAC.equalsIgnoreCase(mac) || TARGET_NAME.equalsIgnoreCase(name);
                if(matches && scanning){
                    stopScan();
                    log("Saat bulundu: "+name+" ("+mac+"), RSSI="+result.getRssi());
                    handler.post(()->connect(d));
                }
            }catch(SecurityException e){log("Tarama izni hatası: "+e.getMessage());}
        }
        @Override public void onScanFailed(int code){
            scanning=false;status("Tarama başarısız: "+code);log("Tarama kodu="+code);controls();
        }
    };
    private void stopScan(){
        if(!scanning)return;
        scanning=false;
        try{if(scanner!=null && hasPermissions())scanner.stopScan(scanCallback);}
        catch(SecurityException ignored){}
        controls();
    }
    private void connect(BluetoothDevice device){
        closeGatt();
        status("Saate bağlanılıyor…");
        try{
            gatt=device.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null)status("GATT bağlantısı başlatılamadı.");
        }catch(SecurityException | IllegalStateException e){
            status("Bağlantı hatası.");log(e.toString());
        }
        controls();
    }
    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt current,int error,int state){
            if(gatt!=null && gatt!=current)return;
            if(error==BluetoothGatt.GATT_SUCCESS && state==BluetoothProfile.STATE_CONNECTED){
                connected=true;status("Bağlandı; servisler keşfediliyor…");log("GATT CONNECTED");
                try{if(!current.discoverServices())log("discoverServices başarısız");}
                catch(SecurityException e){log(e.toString());}
            }else if(state==BluetoothProfile.STATE_DISCONNECTED || error!=0){
                connected=false;nusReady=false;nusRx=null;
                status("Bağlantı kapandı (kod "+error+")");log("GATT DISCONNECTED: "+error);
            }
            controls();
        }
        @Override public void onServicesDiscovered(BluetoothGatt current,int code){
            if(current!=gatt)return;
            if(code!=BluetoothGatt.GATT_SUCCESS){status("Servis keşfi başarısız: "+code);return;}
            BluetoothGattService service=current.getService(NUS);
            nusRx=service==null?null:service.getCharacteristic(NUS_WRITE);
            nusTx=service==null?null:service.getCharacteristic(NUS_NOTIFY);
            BluetoothGattService ae=current.getService(AE);
            BluetoothGattCharacteristic aeTx=ae==null?null:ae.getCharacteristic(AE_NOTIFY);
            StringBuilder found=new StringBuilder("GATT servisleri:");
            for(BluetoothGattService s:current.getServices())found.append("\n").append(s.getUuid());
            log(found.toString());
            log("Nordic RX="+(nusRx!=null)+"; TX="+(nusTx!=null)+"; AE02="+(aeTx!=null));
            status("Bağlı • servisler bulundu");
            handler.post(()->detail.setText("Model: DSMART A8U • MAC: "+TARGET_MAC+
                 "\nNordic UART: "+(service!=null?"VAR":"YOK")+
                 "  /  AE00: "+(ae!=null?"VAR":"YOK")));
            toSubscribe.clear();if(nusTx!=null)toSubscribe.addLast(nusTx);
            if(aeTx!=null)toSubscribe.addLast(aeTx);
            subscribeNext(current);
            BluetoothGattService battery=current.getService(BATTERY);
            if(battery!=null){
                BluetoothGattCharacteristic c=battery.getCharacteristic(BATTERY_LEVEL);
                if(c!=null)log("Standart pil servisi bulundu; otomatik sorgu yapılamazken gösterilmeyecek.");
            }
        }
        @Override public void onDescriptorWrite(BluetoothGatt current,BluetoothGattDescriptor descriptor,int code){
            if(current!=gatt)return;
            log("CCCD "+descriptor.getCharacteristic().getUuid()+" status="+code);
            if(NUS_NOTIFY.equals(descriptor.getCharacteristic().getUuid()))
                nusReady=code==BluetoothGatt.GATT_SUCCESS;
            subscribeNext(current);controls();
        }
        @Override public void onCharacteristicChanged(BluetoothGatt current,BluetoothGattCharacteristic c,byte[] value){
            if(current==gatt)received(c.getUuid(),value);
        }
        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt current,BluetoothGattCharacteristic c){
            if(Build.VERSION.SDK_INT<33 && current==gatt)received(c.getUuid(),c.getValue());
        }
        @Override public void onCharacteristicWrite(BluetoothGatt current,BluetoothGattCharacteristic c,int code){
            if(current==gatt)log("GATT write tamamlandı: "+code);
        }
    };
    private void subscribeNext(BluetoothGatt current){
        while(!toSubscribe.isEmpty()){
            BluetoothGattCharacteristic c=toSubscribe.removeFirst();
            BluetoothGattDescriptor d=c.getDescriptor(CCCD);
            if(d==null){log("CCCD yok: "+c.getUuid());continue;}
            try{
                if(!current.setCharacteristicNotification(c,true)){log("Bildirim kaydı başarısız");continue;}
                boolean started;
                if(Build.VERSION.SDK_INT>=33)
                    started=current.writeDescriptor(d,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
                else{
                    //noinspection deprecation
                    d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //noinspection deprecation
                    started=current.writeDescriptor(d);
                }
                if(started)return;
                log("CCCD yazma başlamadı: "+c.getUuid());
            }catch(SecurityException e){log("CCCD izni: "+e.getMessage());}
        }
        status(connected?"GATT bağlı • Nordic dinleme "+(nusReady?"açık":"doğrulanmadı"):"Bağlı değil");
        controls();
    }
    private void received(UUID source,byte[] bytes){
        if(bytes==null)return;
        byte[] copy=Arrays.copyOf(bytes,bytes.length);
        String name=NUS_NOTIFY.equals(source)?"Nordic TX":AE_NOTIFY.equals(source)?"AE02":source.toString();
        log("RX "+name+" ["+copy.length+"]: "+SmableProtocol.hex(copy));
        if(NUS_NOTIFY.equals(source)){
            List<byte[]> frames=decoder.accept(copy);
            for(byte[] frame:frames)log("FRAME "+SmableProtocol.describe(frame));
        }
    }
    private void readPower(){
        if(!connected||!nusReady||nusRx==null||gatt==null){status("Pil sorgusu için Nordic bağlantı hazır değil.");return;}
        byte[] p=SmableProtocol.make(sequence++,SmableProtocol.POWER,SmableProtocol.READ,null);
        log("TX Nordic READ POWER: "+SmableProtocol.hex(p));
        try{
            boolean started;
            if(Build.VERSION.SDK_INT>=33)
                started=gatt.writeCharacteristic(nusRx,p,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
            else{
                //noinspection deprecation
                nusRx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                //noinspection deprecation
                nusRx.setValue(p);
                //noinspection deprecation
                started=gatt.writeCharacteristic(nusRx);
            }
            if(!started)log("Yazma işlemi başlatılamadı.");
        }catch(SecurityException e){log("Yazma izin hatası: "+e.getMessage());}
    }
    private void disconnect(){
        stopScan();connected=false;nusReady=false;
        try{if(gatt!=null)gatt.disconnect();}catch(SecurityException ignored){}
        closeGatt();status("Bağlantı kesildi.");controls();
    }
    private void closeGatt(){
        BluetoothGatt old=gatt;gatt=null;
        if(old!=null)old.close();
        toSubscribe.clear();decoder.reset();nusRx=null;nusTx=null;
        connected=false;nusReady=false;
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_FILE||result!=RESULT_OK||data==null)return;
        Uri uri=data.getData();if(uri==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(uri)){
            if(out==null)throw new IllegalStateException("Dosya açılamadı");
            out.write(log.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"Rapor kaydedildi",Toast.LENGTH_SHORT).show();
        }catch(Exception e){log("Dosya hatası: "+e.getMessage());}
    }
    @Override protected void onDestroy(){
        disconnect();super.onDestroy();
    }
}
