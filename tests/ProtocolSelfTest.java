import com.lodoskent.watch.SmableProtocol;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
public final class ProtocolSelfTest {
    public static void main(String[] args) {
        if(SmableProtocol.crc("123456789".getBytes(StandardCharsets.US_ASCII),0,9)!=0xbb3d)
            throw new AssertionError("CRC mismatch");
        byte[] p=SmableProtocol.make(0,SmableProtocol.POWER,SmableProtocol.READ,null);
        if(!"AB 00 00 03 FC A0 02 03 10".equals(SmableProtocol.hex(p)))
            throw new AssertionError("Frame "+SmableProtocol.hex(p));
        SmableProtocol.Framer parser=new SmableProtocol.Framer();
        if(!parser.accept(Arrays.copyOfRange(p,0,3)).isEmpty())throw new AssertionError();
        List<byte[]> frames=parser.accept(Arrays.copyOfRange(p,3,p.length));
        if(frames.size()!=1 || !Arrays.equals(frames.get(0),p))throw new AssertionError();
        System.out.println("CRC, frame, partial stream: PASS");
    }
}