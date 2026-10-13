package com.lodoskent.watch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int ORANGE=0xfff27628, BG=0xfff4f6fa, INK=0xff1e2837, SUB=0xff718096, WHITE=Color.WHITE, BLUE=0xff336bc6;
    private final String[] tabs={"Sağlık","Spor","Cihaz","AI","Ben"};
    private final String[] marks={"♥","◉","▣","✦","●"};
    private LinearLayout body,navbar;
    private ScrollView scroll;
    private WatchService service;
    private int active=2;
    private boolean bound;
    private final WatchService.Listener listener=this::render;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            service=((WatchService.LocalBinder)b).service();bound=true;service.addListener(listener);render();
        }
        @Override public void onServiceDisconnected(ComponentName n){service=null;bound=false;render();}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(BG);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        body=new LinearLayout(this);body.setPadding(dp(18),dp(20),dp(18),dp(18));body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navbar=new LinearLayout(this);navbar.setBackgroundColor(WHITE);navbar.setPadding(dp(4),dp(7),dp(4),dp(5));
        layout.addView(navbar,new LinearLayout.LayoutParams(-1,dp(72)));
        setContentView(layout);render();
    }
    @Override protected void onStart(){super.onStart();bindService(new Intent(this,WatchService.class),connection,BIND_AUTO_CREATE);}
    @Override protected void onStop(){
        if(service!=null)service.removeListener(listener);
        if(bound)unbindService(connection);
        bound=false;service=null;super.onStop();
    }
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable gradient(int a,int b,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(radius));return d;
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextColor(color);t.setTextSize(size);t.setLineSpacing(dp(2),1f);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void gap(LinearLayout parent,int height){
        View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    private LinearLayout panel(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17),dp(17),dp(17),dp(17));panel.setBackground(shape(WHITE,20));
        panel.setElevation(dp(1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(13);body.addView(panel,lp);
        return panel;
    }
    private void heading(String title,String subtitle){
        body.addView(label(title,29,INK,true));
        gap(body,5);body.addView(label(subtitle,14,SUB,false));gap(body,17);
    }
    private void section(LinearLayout p,String title,String note){
        p.addView(label(title,17,INK,true));
        if(note!=null&&!note.isEmpty()){gap(p,5);p.addView(label(note,13,SUB,false));}
    }
    private void button(LinearLayout p,String title,int color,Runnable run){
        Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(51));lp.topMargin=dp(9);p.addView(b,lp);
        b.setOnClickListener(v->run.run());
    }
    private void metric(LinearLayout row,String icon,String title,String value,int tint){
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(shape(WHITE,18));p.setPadding(dp(14),dp(13),dp(12),dp(15));p.setElevation(dp(1));
        TextView h=label(icon+"  "+title,13,tint,true);p.addView(h);gap(p,11);
        p.addView(label(value,25,INK,true));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(117),1);lp.leftMargin=dp(5);lp.rightMargin=dp(5);
        row.addView(p,lp);
    }
    private WatchService.State state(){return service==null?null:service.snapshot();}
    private void render(){
        if(body==null||navbar==null)return;
        int y=scroll.getScrollY();body.removeAllViews();navbar.removeAllViews();
        if(active==0)health();else if(active==1)sport();else if(active==2)device();else if(active==3)summary();else profile();
        for(int i=0;i<tabs.length;i++){
            final int at=i;
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
            TextView symbol=label(marks[i],23,i==active?ORANGE:SUB,false);symbol.setGravity(Gravity.CENTER);item.addView(symbol);
            TextView text=label(tabs[i],11,i==active?ORANGE:SUB,i==active);text.setGravity(Gravity.CENTER);item.addView(text);
            navbar.addView(item,new LinearLayout.LayoutParams(0,-1,1));
            item.setOnClickListener(v->{active=at;scroll.scrollTo(0,0);render();scroll.post(()->scroll.scrollTo(0,0));});
        }
        scroll.post(()->{if(scroll.getScrollY()!=y&&y>0)scroll.scrollTo(0,y);});
    }
    private void health(){
        heading("Sağlık","Saatten gelen, doğrulanmış veriler");
        LinearLayout banner=new LinearLayout(this);banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(19),dp(19),dp(19),dp(19));banner.setBackground(gradient(0xffffba78,ORANGE,22));
        banner.addView(label("♥   Günlük sağlık özeti",19,WHITE,true));gap(banner,9);
        banner.addView(label("Saatin veri protokolü doğrulanmadığı için ölçümler henüz alınamıyor.",14,WHITE,false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);body.addView(banner,lp);
        LinearLayout r=new LinearLayout(this);body.addView(r,new LinearLayout.LayoutParams(-1,-2));
        metric(r,"◉","Adımlar","—",ORANGE);metric(r,"⌖","Mesafe","—",BLUE);gap(body,12);
        r=new LinearLayout(this);body.addView(r,new LinearLayout.LayoutParams(-1,-2));
        metric(r,"♨","Kalori","—",0xffa74ac0);metric(r,"♥","Nabız",state()!=null&&state().heartRate>0?state().heartRate+" bpm":"—",0xffdb5670);
        gap(body,14);
        LinearLayout p=panel();section(p,"Kandaki oksijen / Tansiyon","Ölçüm verisi yok. Değerler varsayımla üretilmez.");
        p=panel();section(p,"Uyku ve geçmiş","Saatin üretici veri komutları doğrulanınca burada yer alacak.");
    }
    private void sport(){
        heading("Spor","Hareket ve aktiviteler");
        LinearLayout p=panel();section(p,"Günlük aktivite","Adım, mesafe, kalori ve antrenman geçmişi için cihaz protokolü bekleniyor.");
        p=panel();section(p,"Aktivite kaydı","Bu test sürümünde saatten spor verisi okunamıyor.");
    }
    private void device(){
        heading("Cihaz","Daniel Klein • DSMART A8U");
        WatchService.State s=state();
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18),dp(17),dp(18),dp(17));hero.setBackground(gradient(0xff374c67,0xff1e293b,21));
        hero.addView(label(s!=null&&s.ready?"●  BLE veri bağlantısı açık":"○  BLE bağlantısı bekleniyor",20,WHITE,true));
        gap(hero,8);hero.addView(label(s==null?"Bağlantı servisi hazırlanıyor":s.detail,13,0xffdce6f4,false));
        gap(hero,10);hero.addView(label("Android eşleştirmesi: "+(s==null?"Bilinmiyor":s.bondStatus),13,WHITE,false));
        gap(hero,4);hero.addView(label("Üretici veri oturumu: Doğrulanmadı",13,0xffffd6b4,false));
        LinearLayout.LayoutParams top=new LinearLayout.LayoutParams(-1,-2);top.bottomMargin=dp(15);body.addView(hero,top);
        LinearLayout p=panel();
        section(p,"Bağlantı işlemleri","İlk eşleştirme ve BLE bağlantısı farklı işlemlerdir.");
        if(!permitted())button(p,"Bluetooth izinlerini ver",ORANGE,this::permissions);
        button(p,"Saatleri tara",ORANGE,()->act(()->service.scan()));
        button(p,"Yeniden bağlan",BLUE,()->act(()->service.reconnect()));
        button(p,"Bağlantıyı durdur",INK,()->{if(service!=null)service.disconnect();});
        p=panel();section(p,"Bulunan saatler","Listeden saati seçince Android eşleştirme seçeneği açılır.");
        if(s==null||s.devices.isEmpty())p.addView(label("Henüz saat bulunmadı.",13,SUB,false));
        else for(WatchService.Device d:s.devices){
            button(p,d.name+"  •  "+d.rssi+" dBm",BLUE,()->choose(d));
        }
        p=panel();
        section(p,"Saat bağlantı bilgileri",
            "Cihaz: "+(s==null?"—":s.deviceName)+
            "\nSinyal: "+(s==null||s.rssi==-127?"—":s.rssi+" dBm")+
            "\nGATT: "+(s!=null&&s.ready?"Hazır":"Hazır değil")+
            "\nServis keşfi: "+(s==null?"—":s.serviceInfo)+
            "\nSistem eşleştirmesi: "+(s==null?"—":s.bondStatus));
        button(p,"Tanılama raporunu paylaş",INK,this::share);
        p=panel();section(p,"SAAT AYARLARI","Kadran, titreşim, uyandırmak için kaldır, otomatik nabız ve bildirim ayarları; üretici komutları doğrulanana kadar güvenli biçimde kapalı.");
    }
    private void choose(WatchService.Device d){
        new AlertDialog.Builder(this)
            .setTitle("DSMART A8U")
            .setMessage("Saatle nasıl bağlantı kurulsun?\n\n“Telefonla eşleştir” Android'in sistem eşleştirmesini ister. Cihaz daha önce eşleşmişse veya BLE eşleştirmesi gerekmiyorsa ayrıca onay penceresi açılmayabilir.")
            .setPositiveButton("Telefonla eşleştir",(x,w)->act(()->service.selectWithBond(d.address)))
            .setNeutralButton("Yalnız BLE bağlan",(x,w)->act(()->service.select(d.address)))
            .setNegativeButton("Vazgeç",(x,w)->x.dismiss()).show();
    }
    private void summary(){
        heading("Lodos AI","Saatin yerel sağlık ve bağlantı özeti");
        LinearLayout p=panel();
        section(p,"Durum özeti","Bağlantı: "+(state()==null?"Hazırlanıyor":state().phase)+
            "\nPil: "+(state()!=null&&state().battery>=0?state().battery+"%":"Veri yok")+
            "\nNabız: "+(state()!=null&&state().heartRate>0?state().heartRate+" bpm":"Veri yok"));
        p=panel();section(p,"Yapay zekâ ve yorumlama","Yanlış sağlık sonucu üretilmemesi için gerçek saat verileri alınmadan yorumlama başlatılmıyor.");
    }
    private void profile(){
        heading("Ben","Uygulama ve gizlilik");
        LinearLayout p=panel();section(p,"LodosKentWatch","2.0.0-alpha2 • geliştirici önizlemesi");
        p=panel();section(p,"Bildirimler ve aramalar","Saatin özel protokolü henüz doğrulanmadığı için saat bildirimlerini ve çağrı kontrollerini taklit etmiyoruz.");
        p=panel();section(p,"Gizlilik","Hesap ve internet erişimi zorunlu değil. Tanılama raporu yalnızca sen paylaşırsan dışarı çıkar.");
        button(p,"Bağlantı raporunu paylaş",BLUE,this::share);
    }
    private boolean permitted(){
        if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED
                &&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void permissions(){
        List<String> missing=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31){
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if(missing.isEmpty())startServiceForWatch();
        else requestPermissions(missing.toArray(new String[0]),91);
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] results){
        super.onRequestPermissionsResult(req,p,results);
        if(req==91){if(permitted())startServiceForWatch();else Toast.makeText(this,"Bluetooth izinleri gerekli",Toast.LENGTH_SHORT).show();render();}
    }
    private void startServiceForWatch(){
        if(!permitted()){permissions();return;}
        try{
            Intent intent=new Intent(this,WatchService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
        }catch(RuntimeException e){Toast.makeText(this,"Bağlantı servisi başlatılamadı",Toast.LENGTH_SHORT).show();}
    }
    private void act(Runnable work){
        if(!permitted()){permissions();return;}
        startServiceForWatch();
        if(service==null){Toast.makeText(this,"Servis hazırlanıyor; tekrar deneyin",Toast.LENGTH_SHORT).show();return;}
        work.run();
    }
    private void share(){
        if(service==null){Toast.makeText(this,"Servis hazır değil",Toast.LENGTH_SHORT).show();return;}
        Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,service.diagnostics());
        startActivity(Intent.createChooser(send,"Tanılama kaydını paylaş"));
    }
}
