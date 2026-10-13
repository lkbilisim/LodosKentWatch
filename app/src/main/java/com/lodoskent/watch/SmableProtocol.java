package com.lodoskent.watch;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Independent implementation inferred from SMART-TIME PRO classes3.dex.
 * The A8U's use of this multi-device protocol is not yet confirmed.
 */
public final class SmableProtocol {
    private SmableProtocol() {}
    public static final int POWER = 0x0203;
    public static final int READ = 0x10;
    public static byte[] make(int seq,int key,int flag,byte[] payload) {
        byte[] p=payload==null?new byte[0]:payload;
        if(p.length>0xffff-3)throw new IllegalArgumentException("Payload too long");
        byte[] v=new byte[9+p.length];
        v[0]=(byte)0xab;v[1]=(byte)(seq&1);
        int length=p.length+3;
        v[2]=(byte)(length>>>8);v[3]=(byte)length;
        v[6]=(byte)(key>>>8);v[7]=(byte)key;v[8]=(byte)flag;
        System.arraycopy(p,0,v,9,p.length);
        int crc=crc(v,6,v.length);
        v[4]=(byte)(crc>>>8);v[5]=(byte)crc;
        return v;
    }
    public static int crc(byte[] data,int start,int end) {
        int crc=0;
        for(int i=start;i<end;i++){
            crc^=data[i]&255;
            for(int j=0;j<8;j++)
                crc=(crc&1)!=0?(crc>>>1)^0xa001:crc>>>1;
        }
        return crc&65535;
    }
    public static String hex(byte[] data){
        if(data==null)return "(null)";
        StringBuilder sb=new StringBuilder();
        for(byte b:data){
            if(sb.length()>0)sb.append(' ');
            sb.append(String.format(Locale.ROOT,"%02X",b&255));
        }
        return sb.toString();
    }
    public static String describe(byte[] p){
        if(p.length<9 || (p[0]&255)!=0xab)return "Unknown packet";
        int length=((p[2]&255)<<8)|(p[3]&255);
        int stored=((p[4]&255)<<8)|(p[5]&255);
        int actual=crc(p,6,p.length);
        int key=((p[6]&255)<<8)|(p[7]&255);
        return String.format(Locale.ROOT,"key=0x%04X flag=0x%02X reply=%s nack=%s length=%d CRC=%s",
            key,p[8]&255,(p[1]&16)!=0,(p[1]&32)!=0,length,
            stored==actual?"OK":"INVALID");
    }
    public static final class Framer {
        private final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        public void reset(){buffer.reset();}
        public List<byte[]> accept(byte[] fragment){
            List<byte[]> frames=new ArrayList<>();
            if(fragment==null || fragment.length==0)return frames;
            if(buffer.size()+fragment.length>32768)buffer.reset();
            buffer.write(fragment,0,fragment.length);
            byte[] data=buffer.toByteArray();int i=0;
            while(data.length-i>=6){
                if((data[i]&255)!=0xab){i++;continue;}
                int n=((data[i+2]&255)<<8)|(data[i+3]&255);
                if(n<3 || n>16384){i++;continue;}
                int total=n+6;if(data.length-i<total)break;
                byte[] frame=new byte[total];System.arraycopy(data,i,frame,0,total);
                frames.add(frame);i+=total;
            }
            buffer.reset();buffer.write(data,i,data.length-i);
            return frames;
        }
    }
}