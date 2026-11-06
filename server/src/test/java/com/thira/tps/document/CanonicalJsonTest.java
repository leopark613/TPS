package com.thira.tps.document;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CanonicalJsonTest {
    private final CanonicalJson json=new CanonicalJson();
    @Test void canonicalOrderAndNumberRepresentationAreStable() {
        var a=json.parse("{\"z\":[3,1],\"a\":{\"y\":1.0,\"b\":\"한글\"}}");
        var b=json.parse("{\"a\":{\"b\":\"한글\",\"y\":1e0},\"z\":[3,1]}");
        assertThat(json.canonical(a)).isEqualTo("{\"a\":{\"b\":\"한글\",\"y\":1},\"z\":[3,1]}");
        assertThat(json.hash(json.canonical(a))).isEqualTo(json.hash(json.canonical(b)));
        assertThat(json.hash(json.canonical(a))).isNotEqualTo(json.hash(json.canonical(json.parse("{\"a\":{\"b\":\"한글\",\"y\":1},\"z\":[1,3]}"))));
    }
    @ParameterizedTest @ValueSource(strings={"{\"x\":1,\"x\":2}","{} {}","{\"x\":NaN}","{\"x\":\"\\uD800\"}","["})
    void ambiguousOrMalformedJsonIsRejected(String input) { assertThatThrownBy(()->json.parse(input)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(400); }
    @ParameterizedTest @ValueSource(strings={"9007199254740992","-9007199254740992","0.1","9007199254740991.1","1e50"})
    void unsafeNumbersAreRejectedWithoutDoubleRounding(String input) { assertThatThrownBy(()->json.parse(input)).isInstanceOf(ApiException.class).extracting("code").isEqualTo("UNSAFE_NUMBER"); }
    @Test void boundsAreAccepted() { assertThat(json.canonical(json.parse("[-9007199254740991,9007199254740991,-0.0]"))).isEqualTo("[-9007199254740991,9007199254740991,0]"); }
    @Test void invalidUtf8IsRejected() { assertThatThrownBy(()->json.parse(new byte[]{'{','"','x','"',':','"',(byte)0xc3,(byte)0x28,'"','}'})).isInstanceOf(ApiException.class).extracting("status").isEqualTo(400); }
    @Test void sizeAndDepthAreBounded() {
        assertThatThrownBy(()->json.parse(new byte[CanonicalJson.MAX_BYTES+1])).isInstanceOf(ApiException.class).extracting("status").isEqualTo(413);
        assertThatThrownBy(()->json.parse("[".repeat(65)+"0"+"]".repeat(65))).isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
    }
    @Test void uuidRejectsJavaPermissiveShortForm() { assertThatThrownBy(()->CanonicalJson.uuid("1-1-1-1-1")).isInstanceOf(ApiException.class); }
}
