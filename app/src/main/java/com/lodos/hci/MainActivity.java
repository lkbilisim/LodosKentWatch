package com.lodos.hci;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline Bluetooth HCI packet inspector for DSMART A8U.
 * It cannot capture other apps' live traffic; only imports Android HCI logs.
 */
public final class MainActivity extends Activity {
    private static final String TARGET = "0C:AF:6F:E2:C4:46";
    private static final int PICK = 41, SAVE = 42;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status, body;
    private Button open, save;
    private CheckBox other;
    private String report = "";
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(LinearLayout root,String value,int size) {
        TextView t=new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(27,44,65));
        t.setPadding(0,dp(7),0,dp(7)); root.addView(t); return t;
    }
    private Button button(LinearLayout root,String label,android.view.View.OnClickListener listener) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(label); b.setOnClickListener(listener);
        root.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(20));
        root.setBackgroundColor(Color.rgb(246,249,252));
        text(root,"⌚ Lodos Saat • HCI Analizörü",23);
        text(root,"DSMART A8U  •  "+TARGET,14);
        text(root,"Reklamsız, üyeliksiz, çevrimdışı. Saat bağlantısına müdahale etmez. Android'in oluşturduğu Bluetooth HCI günlüğünü okur.",13);
        status=text(root,"Hazır. Bluetooth HCI günlük dosyasını seç.",14);
        button(root,"Geliştirici seçeneklerini aç",v->{
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}
            catch(Exception e){Toast.makeText(this,"Ayarlar > Geliştirici seçenekleri",Toast.LENGTH_LONG).show();}
        });
        text(root,"Bluetooth HCI izleme günlüğünü aç; gerekirse Bluetooth'u yeniden başlat. SMART-TIME PRO ile normal işlemler yap, Android hata raporu al. Sonra btsnoop_hci.log veya hata raporu ZIP dosyasını seç.",13);
        other=new CheckBox(this);other.setText("Diğer / MAC'i belirlenemeyen cihazlardan örnek de göster (özel veri içerebilir)");
        other.setChecked(false);root.addView(other);
        open=button(root,"HCI / hata raporu ZIP dosyası seç",v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);
        });
        save=button(root,"Raporu TXT olarak kaydet",v->{
            if(report.isEmpty())return;
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE,"LodosSaat_A8U_HCI_rapor.txt");startActivityForResult(i,SAVE);
        });save.setEnabled(false);
        button(root,"Raporu kopyala",v->{
            if(report.isEmpty())return;
            ClipboardManager m=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            m.setPrimaryClip(ClipData.newPlainText("LodosSaat HCI",report));
            Toast.makeText(this,"Rapor kopyalandı",Toast.LENGTH_SHORT).show();
        });
        body=text(root,"Analiz sonucunda Bluetooth WRITE/NOTIFY/READ paketleri listelenecek.",12);
        body.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
    @Override protected void onActivityResult(int req,int res,Intent intent){
        super.onActivityResult(req,res,intent);
        if(res!=RESULT_OK || intent==null || intent.getData()==null)return;
        Uri uri=intent.getData();
        if(req==PICK){
            open.setEnabled(false);save.setEnabled(false);status.setText("HCI inceleniyor…");
            boolean includeOthers=other.isChecked();
            new Thread(()->{
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("Dosya açılamadı.");
                    String result=parse(in,includeOthers);
                    ui.post(()->{report=result;body.setText(result);status.setText("Analiz tamamlandı");
                        open.setEnabled(true);save.setEnabled(true);});
                }catch(Exception e){
                    ui.post(()->{body.setText(e.getMessage());status.setText("Okuma hatası");
                        open.setEnabled(true);});
                }
            },"hci-analysis").start();
        }else if(req==SAVE && !report.isEmpty()){
            try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new IOException("Yazma izni alınamadı.");
                out.write(report.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"Rapor kaydedildi",Toast.LENGTH_SHORT).show();
            }catch(IOException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        }
    }

    static String parse(InputStream source,boolean other) throws IOException {
        BufferedInputStream in=new BufferedInputStream(source,65536);
        in.mark(8);byte[] first=new byte[8];
        int n=in.read(first);in.reset();
        if(n>=8 && Arrays.equals(first,new byte[]{'b','t','s','n','o','o','p',0}))
            return parseSnoop(in,other);
        if(n>=2 && (first[0]&255)==31 && (first[1]&255)==139)
            return parseSnoop(new GZIPInputStream(in),other);
        if(n>=2 && first[0]=='P' && first[1]=='K'){
            ZipInputStream zip=new ZipInputStream(in); ZipEntry e;
            while((e=zip.getNextEntry())!=null){
                String name=e.getName().toLowerCase(Locale.ROOT);
                if(!e.isDirectory() && name.contains("btsnoop") &&
                  (name.endsWith(".log")||name.endsWith(".cfa")||name.endsWith(".bin")||name.endsWith(".gz"))){
                    if(name.endsWith(".gz"))return parseSnoop(new GZIPInputStream(zip),other);
                    return parseSnoop(zip,other);
                }
                zip.closeEntry();
            }
            throw new IOException("ZIP'de btsnoop_hci.log bulunamadı. Bazı üreticiler HCI kaydını hata raporuna eklemiyor.");
        }
        throw new IOException("Ham btsnoop_hci.log, GZIP veya btsnoop içeren ZIP dosyası seç.");
    }
    static String parseSnoop(InputStream source,boolean other) throws IOException {
        DataInputStream in=new DataInputStream(source);
        byte[] magic=new byte[8];in.readFully(magic);
        if(!Arrays.equals(magic,new byte[]{'b','t','s','n','o','o','p',0}))throw new IOException("Geçersiz btsnoop başlığı");
        int version=in.readInt(),datalink=in.readInt();
        if(version!=1 || datalink!=1002)
            throw new IOException("Desteklenen HCI formatı v1 / UART(1002). Bulunan: "+version+"/"+datalink);
        Decoder dec=new Decoder(other);
        int count=0;
        while(count<1000000){
            int original;
            try{original=in.readInt();}catch(EOFException e){break;}
            int size=in.readInt(),flags=in.readInt();in.readInt();long timestamp=in.readLong();
            if(size<0 || size>1048576)throw new IOException("Geçersiz HCI kayıt uzunluğu");
            byte[] data=new byte[size];in.readFully(data);count++;
            if(original==size)dec.accept(data,flags,timestamp);
        }
        StringBuilder b=new StringBuilder("LODOS SAAT — HCI PROTOKOL RAPORU\n");
        b.append("Hedef: DSMART A8U / ").append(TARGET).append('\n');
        b.append("Toplam HCI: ").append(count).append("\nHedef bağlantı: ").append(dec.connections);
        b.append("\nSaat ATT paketi: ").append(dec.hits);
        b.append("\nDiğer / tanımsız ATT: ").append(dec.unmatched).append("\n\n");
        if(dec.connections==0)b.append("UYARI: Hedef MAC bağlantı olayı bulunamadı. Kayıt bağlantıdan sonra başlamış olabilir veya saat farklı BLE adresi kullanabilir.\n\n");
        if(dec.hits==0)b.append("Hedef saat için doğrulanmış ATT WRITE/NOTIFY/READ paketi bulunamadı.\n\n");
        b.append(dec.log);
        if(dec.skipped>0)b.append("\nRapor sınırı nedeniyle atlanan satır: ").append(dec.skipped).append('\n');
        b.append("\nBilgi: Ham veriler komut anlamlarını tek başına kesinleştirmez.\n");
        return b.toString();
    }

    static int u(byte[] x,int p){return x[p]&255;}
    static int le16(byte[] x,int p){return u(x,p)|(u(x,p+1)<<8);}
    static String hex(byte[] x,int from){
        StringBuilder b=new StringBuilder();
        for(int i=from;i<x.length && i<from+400;i++){
            if(b.length()>0)b.append(' ');
            b.append(String.format(Locale.ROOT,"%02X",u(x,i)));
        }
        if(x.length-from>400)b.append(" …");return b.toString();
    }
    static final long BTS_EPOCH=0x00dcddb30f2f8000L;
    static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm:ss.SSS",Locale.ROOT).withZone(ZoneId.systemDefault());
    static String time(long micro){
        if(micro<BTS_EPOCH)return "??:??:??";
        long t=micro-BTS_EPOCH;
        try{return CLOCK.format(Instant.ofEpochSecond(t/1000000L,(t%1000000L)*1000L));}
        catch(Exception e){return "??:??:??";}
    }

    static final class Reassembly{
        final int size;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Reassembly(int size,byte[] first){this.size=size;add(first);}
        void add(byte[] part){if(bytes.size()+part.length<=65535)bytes.write(part,0,part.length);}
        boolean ready(){return bytes.size()>=size;}
        byte[] data(){return Arrays.copyOf(bytes.toByteArray(),size);}
    }
    static final class Decoder{
        final Map<Integer,String> devices=new HashMap<>();
        final Map<Integer,Reassembly> pending=new HashMap<>();
        final boolean other;final StringBuilder log=new StringBuilder();
        int hits=0,unmatched=0,connections=0,skipped=0;
        Decoder(boolean other){this.other=other;}
        void accept(byte[] hci,int flags,long t){
            if(hci.length<4)return;
            if(u(hci,0)==4){
                if(u(hci,1)==0x3e && hci.length>=15 &&
                   (u(hci,3)==1 || u(hci,3)==10) && u(hci,4)==0){
                    int handle=le16(hci,5)&0xfff;
                    String mac=String.format(Locale.ROOT,"%02X:%02X:%02X:%02X:%02X:%02X",
                        u(hci,14),u(hci,13),u(hci,12),u(hci,11),u(hci,10),u(hci,9));
                    devices.put(handle,mac);if(TARGET.equals(mac))connections++;
                }
                if(u(hci,1)==5 && hci.length>=6){
                    int handle=le16(hci,4)&0xfff;devices.remove(handle);pending.remove(handle);
                }
                return;
            }
            if(u(hci,0)!=2 || hci.length<9)return;
            int header=le16(hci,1),handle=header&0xfff,pb=(header>>12)&3;
            int size=le16(hci,3);if(size>hci.length-5)return;
            byte[] frag=Arrays.copyOfRange(hci,5,5+size);
            if(pb==1){
                Reassembly r=pending.get(handle);
                if(r!=null){r.add(frag);if(r.ready()){pending.remove(handle);att(handle,flags,t,r.data());}}
                return;
            }
            if(frag.length<4 || le16(frag,2)!=4)return;
            int len=le16(frag,0);byte[] att=Arrays.copyOfRange(frag,4,frag.length);
            if(att.length>=len)att(handle,flags,t,Arrays.copyOf(att,len));
            else pending.put(handle,new Reassembly(len,att));
        }
        void att(int handle,int flags,long t,byte[] data){
            if(data.length==0)return;
            int op=u(data,0);String kind;
            switch(op){
                case 0x12:kind="WRITE_REQ";break;
                case 0x52:kind="WRITE_CMD";break;
                case 0x1b:kind="NOTIFY";break;
                case 0x1d:kind="INDICATE";break;
                case 0x0a:kind="READ_REQ";break;
                case 0x0b:kind="READ_RSP";break;
                case 0x13:kind="WRITE_RSP";break;
                default:return;
            }
            boolean hasHandle=(op==0x12||op==0x52||op==0x1b||op==0x1d||op==0x0a);
            if(hasHandle && data.length<3)return;
            boolean target=TARGET.equals(devices.get(handle));
            if(!target && !other){unmatched++;return;}
            if(target)hits++;else unmatched++;
            if(log.length()>350000){skipped++;return;}
            String direction=(flags&1)==0?"TELEFON→SAAT":"SAAT→TELEFON";
            int offset=hasHandle?3:1;
            log.append(time(t)).append(' ').append(direction).append(' ').append(kind)
              .append(String.format(Locale.ROOT," conn=0x%03X",handle));
            if(hasHandle)log.append(String.format(Locale.ROOT," attr=0x%04X",le16(data,1)));
            if(!target)log.append(" [DİĞER/TANIMSIZ]");
            log.append(" data=").append(hex(data,offset)).append('\n');
        }
    }
}
