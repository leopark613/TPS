package com.thira.tps.rps.viewer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.thira.tps.document.ApiException;
import org.springframework.stereotype.Component;

@Component
public class QrEncoder {
    public ReportViewerDtos.Qr encode(String payload) {
        if(payload==null || payload.isEmpty()) throw new ApiException(422,"QR_PAYLOAD_EMPTY","QR에 연결할 값이 비어 있습니다.");
        // Above all QR modes' maximum capacity. Actual mode/version capacity is
        // decided by ZXing below; numeric data can validly exceed byte-mode limits.
        if(payload.getBytes(StandardCharsets.UTF_8).length>10_000) throw capacity();
        try {
            var code=Encoder.encode(payload,ErrorCorrectionLevel.M,Map.of(EncodeHintType.CHARACTER_SET,"UTF-8"));
            var matrix=code.getMatrix();
            List<String> rows=new ArrayList<>(matrix.getHeight());
            for(int y=0;y<matrix.getHeight();y++) {
                StringBuilder row=new StringBuilder(matrix.getWidth());
                for(int x=0;x<matrix.getWidth();x++) row.append(matrix.get(x,y)==1?'1':'0');
                rows.add(row.toString());
            }
            return new ReportViewerDtos.Qr(List.copyOf(rows),matrix.getWidth(),4,code.getVersion().getVersionNumber(),"M","UTF-8");
        } catch(WriterException ex) { throw capacity(); }
    }
    private static ApiException capacity() { return new ApiException(422,"QR_CAPACITY_EXCEEDED","QR에 담을 수 있는 데이터 크기를 초과했습니다."); }
}
