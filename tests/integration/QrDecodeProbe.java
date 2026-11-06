import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.QRCodeReader;

/** Test-only QR reader, independent of the product encoder/rendering. Never logs payload. */
public final class QrDecodeProbe {
    public static void main(String[] args) {
        try {
            BinaryBitmap bitmap;
            if(args.length==2 && args[0].equals("png")) {
                BufferedImage image=ImageIO.read(Path.of(args[1]).toFile());
                int w=image.getWidth(),h=image.getHeight();
                bitmap=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,image.getRGB(0,0,w,h,null,0,w))));
            } else {
                List<String> rows=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8)).lines().toList();
                if(rows.isEmpty() || rows.size()>177 || rows.stream().anyMatch(s->s.length()!=rows.size() || !s.matches("[01]+"))) throw new IllegalArgumentException();
                int scale=6, n=rows.size(),side=(n+8)*scale; int[] pixels=new int[side*side]; Arrays.fill(pixels,0xFFFFFF);
                for(int y=0;y<n;y++) for(int x=0;x<n;x++) if(rows.get(y).charAt(x)=='1')
                    for(int dy=0;dy<scale;dy++) for(int dx=0;dx<scale;dx++) pixels[((y+4)*scale+dy)*side+(x+4)*scale+dx]=0;
                bitmap=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(side,side,pixels)));
            }
            String payload=new QRCodeReader().decode(bitmap,Map.of(DecodeHintType.CHARACTER_SET,"UTF-8",DecodeHintType.TRY_HARDER,true)).getText();
            System.out.print(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))));
        } catch(Exception e) { System.err.print("QR decode failed: "+e.getClass().getSimpleName()); System.exit(1); }
    }
}
