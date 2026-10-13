package com.lodoskent.watch;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;

public final class WatchService extends Service {
    public interface Listener { void changed(); }
    public static final class Device {
        public final String address,name;public final int rssi;
        Device(String a,String n,int r){address=a;name=n;rssi=r;}
    }
    public static final class State {
        public final String phase,detail,deviceName,serviceInfo,bondStatus;
        public final int battery,heartRate,rssi;
        public final boolean ready;
        public final List<Device> devices;
        State(String p,String d,String n,String info,String bond,int b,int h,int r,boolean ok,List<Device> list){
            phase=p;detail=d;deviceName=n;serviceInfo=info;bondStatus=bond;battery=b;heartRate=h;rssi=r;ready=ok;devices=list;
        }
    }
    public final class LocalBinder extends Binder { public WatchService service(){return WatchService.this;} }
    private final IBinder binder=new LocalBinder();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<Listener> listeners=new ArrayList<>();
    private final LinkedHashMap<String,Device> nearby=new LinkedHashMap<>();
    private final ArrayDeque<String> log=new ArrayDeque<>();
    private final ArrayDeque<Op> ops=new ArrayDeque<>();
    private final Random jitter=new Random();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BluetoothGatt gatt;
    private Runnable scanStop,retry,watchdog,opWatchdog,bondWatchdog;
    private Op currentOp;
    private int epoch,attempts,battery=-1,heartRate=-1,rssi=-127;
    private boolean manualStop,scanning,ready,foreground,pairPending;
    private String address="",name="",phase="HAZIR",detail="Cihaz aranabilir",serviceInfo="—",bondStatus="Eşleştirme bilinmiyor";
    private static final UUID BAT=UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private static final UUID HR=UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID UART=UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID AE02=UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb");
    private static final class Op {
        static final int SUB=1,READ=2,RSSI=3;
        final int type;final BluetoothGattCharacteristic charac;
        Op(int t,BluetoothGattCharacteristic c){type=t;charac=c;}
    }
    private final BroadcastReceiver btReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())){
                BluetoothDevice device;
                if(Build.VERSION.SDK_INT>=33)device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
                else device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device==null||address.isEmpty()||!allowed())return;
                try{if(!address.equalsIgnoreCase(device.getAddress()))return;}
                catch(SecurityException e){return;}
                int bs=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.BOND_NONE);
                if(bs==BluetoothDevice.BOND_BONDING){bondStatus="Android eşleştirmesi sürüyor";status("EŞLEŞTİRİLİYOR","Sistem onayı bekleniyor");}
                else if(bs==BluetoothDevice.BOND_BONDED){
                    bondStatus="Android eşleştirmesi tamam";append(bondStatus);
                    if(pairPending){pairPending=false;cancelBondWatchdog();scheduleRetry(350,"Android eşleştirmesi tamamlandı");}
                }else if(bs==BluetoothDevice.BOND_NONE){
                    bondStatus="Android eşleştirmesi yok";append("Android eşleştirmesi kurulmadı");
                    if(pairPending){pairPending=false;cancelBondWatchdog();scheduleRetry(500,"BLE veri bağlantısı deneniyor");}
                }
                refresh();return;
            }
            if(!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction()))return;
            int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
            if(state==BluetoothAdapter.STATE_OFF||state==BluetoothAdapter.STATE_TURNING_OFF){
                stopScan();closeGatt();cancelRetry();status("BEKLEME","Bluetooth kapalı");
            }else if(state==BluetoothAdapter.STATE_ON&&!manualStop)scheduleRetry(1500,"Bluetooth açıldı");
        }
    };
    @Override public void onCreate(){
        super.onCreate();BluetoothManager m=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=m==null?null:m.getAdapter();
        address=getSharedPreferences("watch",MODE_PRIVATE).getString("target","");
        manualStop=getSharedPreferences("watch",MODE_PRIVATE).getBoolean("stop",false);
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(btReceiver,filter);
        append("Bağlantı servisi başlatıldı");
    }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(!foreground){
            try{
                NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("watch_ble","LodosKentWatch bağlantısı",NotificationManager.IMPORTANCE_LOW));
                PendingIntent click=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                Notification n=new Notification.Builder(this,"watch_ble").setContentTitle("LodosKentWatch")
                    .setContentText("Saat bağlantısı takip ediliyor").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentIntent(click).setOngoing(true).build();
                if(Build.VERSION.SDK_INT>=34)startForeground(10,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                else startForeground(10,n);
                foreground=true;
            }catch(RuntimeException ex){append("Ön plan hatası "+ex.getClass().getSimpleName());stopSelf();return START_NOT_STICKY;}
        }
        if(!manualStop&&!address.isEmpty())ui.post(this::ensure);
        return START_STICKY;
    }
    @Override public void onDestroy(){
        manualStop=true;cancelRetry();cancelBondWatchdog();pairPending=false;stopScan();closeGatt();
        try{unregisterReceiver(btReceiver);}catch(RuntimeException ignored){}
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    public void addListener(Listener l){if(!listeners.contains(l))listeners.add(l);l.changed();}
    public void removeListener(Listener l){listeners.remove(l);}
    public State snapshot(){return new State(phase,detail,name,serviceInfo,bondStatus,battery,heartRate,rssi,ready,new ArrayList<>(nearby.values()));}
    public String diagnostics(){
        StringBuilder s=new StringBuilder("LodosKentWatch v2.0.0-alpha3\nDurum: "+phase+"\n"+detail+"\n");
        s.append("Servisler: ").append(serviceInfo).append("\nEşleştirme: ").append(bondStatus).append("\n");
        for(String e:log)s.append(e).append('\n');return s.toString();
    }
    private void append(String m){
        log.addLast(System.currentTimeMillis()+" "+m);while(log.size()>150)log.removeFirst();refresh();
    }
    private void refresh(){
        ui.post(()->{for(Listener l:new ArrayList<>(listeners))l.changed();});
    }
    private void status(String p,String d){phase=p;detail=d;append(p+" "+d);}
    private boolean allowed(){
        return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
            &&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);
    }
    private boolean enabled(){
        if(adapter==null){status("HATA","Bluetooth adaptörü yok");return false;}
        if(!allowed()){status("İZİN","Bluetooth izinleri gerekli");return false;}
        try {if(!adapter.isEnabled()){status("BEKLEME","Bluetooth kapalı");return false;}}
        catch(SecurityException e){status("İZİN","Bluetooth izni gerekli");return false;}
        return true;
    }
    public void select(String a){
        if(a==null||!BluetoothAdapter.checkBluetoothAddress(a))return;
        if(a.equalsIgnoreCase(address)&&ready&&gatt!=null){
            append("Saat zaten bağlı; mevcut GATT oturumu korunuyor");return;
        }
        address=a;attempts=0;manualStop=false;pairPending=false;cancelBondWatchdog();
        getSharedPreferences("watch",MODE_PRIVATE).edit().putString("target",a).putBoolean("stop",false).apply();
        cancelRetry();stopScan();closeGatt();scheduleRetry(250,"Saat seçildi");
    }
    /** Android pairing controls link security, NOT the separate voice/audio profile.
     * Never tear down an already healthy GATT connection to check bond state. */
    public void selectWithBond(String a){
        if(a==null||!BluetoothAdapter.checkBluetoothAddress(a)||!enabled())return;
        boolean existing=a.equalsIgnoreCase(address)&&gatt!=null&&ready;
        manualStop=false;pairPending=false;cancelBondWatchdog();
        address=a;
        getSharedPreferences("watch",MODE_PRIVATE).edit().putString("target",a).putBoolean("stop",false).apply();
        try{
            BluetoothDevice d=adapter.getRemoteDevice(a);
            int bs=d.getBondState();
            if(bs==BluetoothDevice.BOND_BONDED){
                bondStatus="Android eşleştirmesi zaten var";
                append("Android BLE eşleştirmesi var. Arama/ses ayrı Bluetooth profili gerektirebilir.");
                if(!existing){cancelRetry();stopScan();closeGatt();scheduleRetry(250,"Kayıtlı cihaz");}
                // Preserve the existing connection if it is already up.
                return;
            }
            if(existing){
                // A device may connect to a GATT service without OS bonding.
                append("BLE veri bağlantısı zaten açık; sistem eşleştirmesi opsiyonel.");
                return;
            }
            cancelRetry();stopScan();closeGatt();
            if(bs==BluetoothDevice.BOND_BONDING){
                pairPending=true;bondStatus="Sistem eşleştirmesi sürüyor";
                status("EŞLEŞTİRİLİYOR","Android onayı bekleniyor");startBondWatchdog();
            }else{
                pairPending=d.createBond();
                if(pairPending){
                    bondStatus="Android sistem eşleştirmesi istendi";
                    status("EŞLEŞTİRİLİYOR","Telefon veya saat onayı bekleniyor");
                    startBondWatchdog();
                }else{
                    bondStatus="Saat sistem eşleştirmesini başlatmadı";
                    scheduleRetry(500,"BLE veri bağlantısı deneniyor");
                }
            }
        }catch(RuntimeException e){
            pairPending=false;bondStatus="Sistem eşleştirmesi başarısız";
            append("createBond hatası: "+e.getClass().getSimpleName());
            if(!existing)scheduleRetry(500,"BLE veri bağlantısı deneniyor");
        }
    }
    private void startBondWatchdog(){
        cancelBondWatchdog();
        bondWatchdog=()->{bondWatchdog=null;if(pairPending){
            pairPending=false;bondStatus="Android eşleştirmesi zaman aşımına uğradı";
            scheduleRetry(500,"Yalnız BLE bağlantısı deneniyor");
        }};
        ui.postDelayed(bondWatchdog,30000);
    }
    private void cancelBondWatchdog(){if(bondWatchdog!=null)ui.removeCallbacks(bondWatchdog);bondWatchdog=null;}
    public void reconnect(){
        if(address.isEmpty()){scan();return;}
        attempts=0;manualStop=false;
        getSharedPreferences("watch",MODE_PRIVATE).edit().putBoolean("stop",false).apply();
        cancelRetry();stopScan();closeGatt();scheduleRetry(250,"Elle bağlantı");
    }
    public void disconnect(){
        manualStop=true;pairPending=false;cancelBondWatchdog();getSharedPreferences("watch",MODE_PRIVATE).edit().putBoolean("stop",true).apply();
        cancelRetry();stopScan();closeGatt();status("DURDURULDU","Otomatik bağlantı kapalı");
    }
    public void scan(){
        manualStop=false;getSharedPreferences("watch",MODE_PRIVATE).edit().putBoolean("stop",false).apply();
        cancelRetry();pairPending=false;cancelBondWatchdog();stopScan();nearby.clear();
        // Scan must not arbitrarily drop the currently established GATT link.
        if(ready&&gatt!=null){status("GATT HAZIR","Saat bağlı; bağlantıyı koruyarak cihazları tarıyor");}
        else closeGatt();
        beginScan(false);
    }
    private void ensure(){
        if(manualStop||pairPending||scanning||gatt!=null||!enabled())return;
        if(address.isEmpty()||attempts>=2){beginScan(!address.isEmpty());return;}
        try {connect(adapter.getRemoteDevice(address));}
        catch(RuntimeException ex){scheduleRetry(delay(),"Kayıtlı cihaz bulunamadı");}
    }
    private void beginScan(boolean recovering){
        if(manualStop||scanning||!enabled())return;
        try {
            scanner=adapter.getBluetoothLeScanner();if(scanner==null){scheduleRetry(delay(),"Tarayıcı yok");return;}
            scanning=true;status("ARANIYOR",recovering?"Kayıtlı saat aranıyor":"Yakındaki saatler aranıyor");
            scanCallback=new ScanCallback(){
                @Override public void onScanResult(int type,ScanResult result){ui.post(()->accept(result));}
                @Override public void onBatchScanResults(List<ScanResult> list){for(ScanResult r:list)ui.post(()->accept(r));}
                @Override public void onScanFailed(int error){ui.post(()->{stopScan();if(ready&&gatt!=null)status("GATT HAZIR","Tarama hatası: "+error+", bağlantı korundu");else scheduleRetry(delay(),"Tarama hatası: "+error);});}
            };
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            scanStop=()->{stopScan();if(ready&&gatt!=null)status("GATT HAZIR","Tarama bitti, mevcut BLE bağlantısı korundu");else if(!address.isEmpty())scheduleRetry(delay(),"Saat bulunamadı");else status("HAZIR","Tarama bitti");};
            ui.postDelayed(scanStop,12000);
        }catch(RuntimeException e){stopScan();scheduleRetry(delay(),"Tarama başlatılamadı");}
    }
    private void accept(ScanResult result){
        if(!scanning||result==null||result.getDevice()==null)return;
        try{
            BluetoothDevice d=result.getDevice();
            String a=d.getAddress(),n=d.getName();
            if((n==null||n.isEmpty())&&result.getScanRecord()!=null)n=result.getScanRecord().getDeviceName();
            if(n==null)n="";
            boolean match=!address.isEmpty()&&address.equalsIgnoreCase(a);
            String upper=n.toUpperCase(Locale.ROOT);
            if(!match&&!upper.contains("DSMART")&&!upper.contains("A8")&&!upper.contains("WATCH"))return;
            nearby.put(a,new Device(a,n.isEmpty()?"İsimsiz saat":n,result.getRssi()));refresh();
            if(match&&gatt==null&&!pairPending){name=n;stopScan();connect(d);}
        }catch(RuntimeException e){append("Tarama sonucu okunamadı");}
    }
    private void stopScan(){
        if(scanStop!=null)ui.removeCallbacks(scanStop);scanStop=null;
        if(scanner!=null&&scanCallback!=null)try{scanner.stopScan(scanCallback);}catch(RuntimeException ignored){}
        scanner=null;scanCallback=null;scanning=false;refresh();
    }
    private void connect(BluetoothDevice d){
        if(manualStop||!enabled())return;
        closeGatt();int id=++epoch;ready=false;serviceInfo="—";
        try {
            name=d.getName()==null?name:d.getName();status("BAĞLANIYOR",name);
            gatt=d.connectGatt(this,false,callback(id),BluetoothDevice.TRANSPORT_LE);
            if(gatt==null){scheduleRetry(delay(),"GATT başlatılamadı");return;}
            watchdog=()->{if(id==epoch){closeGatt();scheduleRetry(delay(),"GATT bağlantı zaman aşımı");}};
            ui.postDelayed(watchdog,18000);
        }catch(RuntimeException e){closeGatt();scheduleRetry(delay(),"GATT bağlantı hatası");}
    }
    private BluetoothGattCallback callback(final int id){
        return new BluetoothGattCallback(){
            @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){ui.post(()->{
                if(!current(id,g))return;
                if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){
                    cancelWatchdog();status("SERVİSLER","Servis keşfediliyor");
                    watchdog=()->{if(current(id,g)){closeGatt();scheduleRetry(delay(),"Servis keşfi zaman aşımı");}};
                    ui.postDelayed(watchdog,15000);
                    try{if(!g.discoverServices()){closeGatt();scheduleRetry(delay(),"Keşif başlatılamadı");}}
                    catch(RuntimeException e){closeGatt();scheduleRetry(delay(),"Servis keşfi hatası");}
                }else{
                    append("GATT ayrıldı: "+status+" / "+state);closeGatt();scheduleRetry(delay(),"Bağlantı kesildi");
                }
            });}
            @Override public void onServicesDiscovered(BluetoothGatt g,int status){ui.post(()->{
                if(!current(id,g))return;cancelWatchdog();
                if(status!=BluetoothGatt.GATT_SUCCESS){closeGatt();scheduleRetry(delay(),"Keşif hatası "+status);return;}
                configure(g);
            });}
            @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int code){ui.post(()->{
                if(current(id,g))finish(Op.SUB,code);
            });}
            @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int code){
                if(Build.VERSION.SDK_INT<33){byte[] data=c.getValue();ui.post(()->readResult(id,g,c,data,code));}
            }
            @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v,int code){
                if(Build.VERSION.SDK_INT>=33){byte[] data=v==null?new byte[0]:v.clone();ui.post(()->readResult(id,g,c,data,code));}
            }
            @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){
                if(Build.VERSION.SDK_INT<33){byte[] data=c.getValue();ui.post(()->receive(id,g,c,data));}
            }
            @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){
                if(Build.VERSION.SDK_INT>=33){byte[] data=v==null?new byte[0]:v.clone();ui.post(()->receive(id,g,c,data));}
            }
            @Override public void onReadRemoteRssi(BluetoothGatt g,int value,int code){ui.post(()->{
                if(!current(id,g))return;if(code==BluetoothGatt.GATT_SUCCESS)rssi=value;finish(Op.RSSI,code);
            });}
        };
    }
    private boolean current(int id,BluetoothGatt g){return id==epoch&&g==gatt;}
    private void configure(BluetoothGatt g){
        ops.clear();currentOp=null;int count=0,channels=0;
        StringBuilder report=new StringBuilder();
        for(BluetoothGattService svc:g.getServices()){
            count++;report.append(svc.getUuid()).append(" {");
            for(BluetoothGattCharacteristic c:svc.getCharacteristics()){
                report.append(c.getUuid()).append(":props=").append(c.getProperties()).append("; ");
            }
            report.append("} ");
            for(BluetoothGattCharacteristic c:svc.getCharacteristics()){
                if(c.getUuid().equals(BAT)&&(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)
                    ops.add(new Op(Op.READ,c));
                if((c.getUuid().equals(HR)||c.getUuid().equals(UART)||c.getUuid().equals(AE02))
                    && c.getDescriptor(CCCD)!=null
                    && (c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0){
                    ops.add(new Op(Op.SUB,c));channels++;
                }
            }
        }
        serviceInfo=count+" servis, "+channels+" bildirim adayı (UART + AE02 dahil)";
        append("GATT servis UUID: "+report);
        attempts=0;ready=true;status("GATT HAZIR","Servisler keşfedildi. Üretici protokolü doğrulanmadı.");
        pump();ui.postDelayed(()->{if(ready&&gatt!=null){ops.add(new Op(Op.RSSI,null));pump();}},15000);
    }
    private void pump(){
        if(gatt==null||currentOp!=null||ops.isEmpty())return;
        Op op=ops.poll();currentOp=op;boolean started=false;
        try{
            if(op.type==Op.RSSI)started=gatt.readRemoteRssi();
            else if(op.type==Op.READ)started=gatt.readCharacteristic(op.charac);
            else if(op.type==Op.SUB){
                BluetoothGattDescriptor desc=op.charac.getDescriptor(CCCD);
                boolean enabled=gatt.setCharacteristicNotification(op.charac,true);
                int prop=op.charac.getProperties();
                byte[] value=(prop&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0
                  ?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                if(enabled&&desc!=null){
                    if(Build.VERSION.SDK_INT>=33)started=gatt.writeDescriptor(desc,value)==BluetoothGatt.GATT_SUCCESS;
                    else{desc.setValue(value);started=gatt.writeDescriptor(desc);}
                }
            }
        }catch(RuntimeException e){append("İşlem hatası: "+e.getClass().getSimpleName());}
        if(!started){currentOp=null;pump();return;}
        int id=epoch;
        opWatchdog=()->{if(id==epoch&&currentOp==op){closeGatt();scheduleRetry(delay(),"GATT işlem zaman aşımı");}};
        ui.postDelayed(opWatchdog,8000);
    }
    private void finish(int type,int code){
        if(currentOp==null||currentOp.type!=type)return;
        if(opWatchdog!=null)ui.removeCallbacks(opWatchdog);opWatchdog=null;
        if(currentOp.type==Op.SUB){
            append("Bildirim CCCD "+currentOp.charac.getUuid()+" : "+(code==BluetoothGatt.GATT_SUCCESS?"etkin":"HATA "+code));
        }
        currentOp=null;if(code!=BluetoothGatt.GATT_SUCCESS)append("GATT hata: "+code);
        pump();refresh();
    }
    private void readResult(int id,BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v,int code){
        if(!current(id,g))return;if(code==BluetoothGatt.GATT_SUCCESS)parse(c,v);
        finish(Op.READ,code);
    }
    private void receive(int id,BluetoothGatt g,BluetoothGattCharacteristic c,byte[] data){
        if(current(id,g))parse(c,data);
    }
    private void parse(BluetoothGattCharacteristic c,byte[] data){
        if(data==null)return;
        if(c.getUuid().equals(BAT)&&data.length>0){int b=data[0]&255;if(b<=100)battery=b;}
        else if(c.getUuid().equals(HR)&&data.length>=2){
            boolean wide=(data[0]&1)!=0;int hr=wide?(data.length>=3?((data[2]&255)<<8)|(data[1]&255):-1):(data[1]&255);
            if(hr>=25&&hr<=240)heartRate=hr;
        }else{
            StringBuilder hex=new StringBuilder();for(int i=0;i<Math.min(data.length,32);i++)hex.append(String.format(Locale.US,"%02X",data[i]));
            append("Ham veri ["+c.getUuid()+"] "+hex+" (üretici protokolü doğrulanmadı)");
        }
        refresh();
    }
    private int delay(){attempts=Math.min(attempts+1,24);return Math.min(120000,2000*(1<<Math.min(6,attempts-1)))+jitter.nextInt(1000);}
    private void scheduleRetry(int ms,String why){
        if(manualStop)return;cancelRetry();status("YENİDEN BAĞLANACAK",why+" ("+(ms/1000)+" sn)");
        retry=()->{retry=null;ensure();};ui.postDelayed(retry,ms);
    }
    private void cancelRetry(){if(retry!=null)ui.removeCallbacks(retry);retry=null;}
    private void cancelWatchdog(){if(watchdog!=null)ui.removeCallbacks(watchdog);watchdog=null;}
    private void closeGatt(){
        ++epoch;cancelWatchdog();
        if(opWatchdog!=null)ui.removeCallbacks(opWatchdog);opWatchdog=null;
        ops.clear();currentOp=null;ready=false;
        BluetoothGatt old=gatt;gatt=null;
        if(old!=null){try{old.disconnect();}catch(RuntimeException ignored){}try{old.close();}catch(RuntimeException ignored){}}
        refresh();
    }
}
