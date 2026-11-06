package com.thira.tps.rps.viewer;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.Decoder;
import com.thira.tps.document.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class QrEncoderTest {
    private final QrEncoder encoder=new QrEncoder();
    @ParameterizedTest @ValueSource(strings={"000012","검사용_모터|LOT-0007|000012"," line 1\nline 2 ",""+"TPS-DEMO-001|LOT-0007|000012","  "})
    void independentlyDecodesActualMatrixWithoutTrimming(String payload) throws Exception {
        var code=encoder.encode(payload);
        BitMatrix matrix=new BitMatrix(code.getModuleCount());
        for(int y=0;y<code.getModuleCount();y++) for(int x=0;x<code.getModuleCount();x++) if(code.getMatrix().get(y).charAt(x)=='1') matrix.set(x,y);
        assertThat(new Decoder().decode(matrix).getText()).isEqualTo(payload);
        assertThat(code.getModuleCount()).isEqualTo(17+4*code.getVersion());
        assertThat(code.getQuietZoneModules()).isEqualTo(4);assertThat(code.getErrorCorrection()).isEqualTo("M");assertThat(code.getEncoding()).isEqualTo("UTF-8");
    }
    @Test void rejectsEmptyAndNullRatherThanReusingOldMatrix() {
        encoder.encode("valid");
        assertThatThrownBy(()->encoder.encode("")).isInstanceOf(ApiException.class).extracting("code").isEqualTo("QR_PAYLOAD_EMPTY");
        assertThatThrownBy(()->encoder.encode(null)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("QR_PAYLOAD_EMPTY");
    }
    @Test void capacityChecksEncodedBytesAndEncoderLimit() {
        assertThatThrownBy(()->encoder.encode("가".repeat(1100))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("QR_CAPACITY_EXCEEDED");
        assertThatThrownBy(()->encoder.encode("a".repeat(2400))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("QR_CAPACITY_EXCEEDED");
    }
    @Test void numericModeMayExceedByteModeCapacity() throws Exception {
        String payload="1".repeat(4000);
        var actual=encoder.encode(payload);
        var direct=com.google.zxing.qrcode.encoder.Encoder.encode(payload,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
            java.util.Map.of(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8"));
        assertThat(actual.getVersion()).isEqualTo(direct.getVersion().getVersionNumber());
        independentlyDecodesActualMatrixWithoutTrimming(payload);
    }
}
