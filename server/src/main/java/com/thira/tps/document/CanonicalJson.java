package com.thira.tps.document;

import java.math.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.*;

@Component
public class CanonicalJson {
    public static final int MAX_BYTES=1024*1024;
    public static final String HASH_VERSION="tps-canonical-json-v1";
    private static final BigDecimal SAFE=BigDecimal.valueOf(9007199254740991L);
    private final JsonMapper mapper=JsonMapper.builder(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(MAX_BYTES).maxNumberLength(100).build()).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

    public JsonNode parse(byte[] bytes) {
        if(bytes.length>MAX_BYTES) throw new ApiException(413,"REQUEST_TOO_LARGE","문서 크기는 1 MiB 이하여야 합니다.");
        try {
            String input=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode node=mapper.readTree(input);
            if(node==null || node.isMissingNode()) throw new ApiException(400,"INVALID_JSON","JSON 본문이 필요합니다.");
            return normalize(node,0);
        } catch(ApiException ex) { throw ex; }
        catch(Exception ex) { throw new ApiException(400,"INVALID_JSON","JSON 형식·중복 키·깊이를 확인하세요."); }
    }
    public JsonNode parse(String json) { return parse(json.getBytes(StandardCharsets.UTF_8)); }
    public String write(Object value) { return mapper.writeValueAsString(value); }
    public <T> T read(String json,Class<T> type) { return mapper.readValue(json,type); }
    public String canonical(JsonNode node) { return mapper.writeValueAsString(normalize(node,0)); }
    public JsonNode normalize(JsonNode node,int depth) {
        if(depth>64) throw new ApiException(400,"INVALID_JSON","문서의 중첩 깊이가 너무 큽니다.");
        if(node.isObject()) {
            ObjectNode out=mapper.createObjectNode();
            TreeMap<String,JsonNode> fields=new TreeMap<>(); node.properties().forEach(entry -> fields.put(entry.getKey(),entry.getValue()));
            fields.forEach((key,value) -> { unicode(key); out.set(key,normalize(value,depth+1)); }); return out;
        }
        if(node.isArray()) { ArrayNode out=mapper.createArrayNode(); for(JsonNode value:node) out.add(normalize(value,depth+1)); return out; }
        if(node.isNumber()) {
            BigDecimal decimal=node.decimalValue();
            if(decimal.abs().compareTo(SAFE)>0 || decimal.stripTrailingZeros().scale()>0)
                throw new ApiException(422,"UNSAFE_NUMBER","숫자는 안전한 정수 범위여야 합니다. 소수 속성은 문자열을 사용하세요.");
            return LongNode.valueOf(decimal.longValueExact());
        }
        if(node.isString()) unicode(node.asString());
        return node;
    }
    private static void unicode(String text) {
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)) { if(++i>=text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new ApiException(400,"INVALID_UNICODE","잘못된 문자 인코딩입니다."); }
            else if(Character.isLowSurrogate(c)) throw new ApiException(400,"INVALID_UNICODE","잘못된 문자 인코딩입니다.");
        }
    }
    public byte[] hash(String canonical) {
        try { return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public ObjectNode object() { return mapper.createObjectNode(); }
    public String text(JsonNode value,String key) {
        JsonNode node=value.get(key);
        if(node==null || !node.isString() || node.asString().isEmpty()) throw new ApiException(400,"INVALID_REQUEST","필수 문자열을 확인하세요: "+key);
        return node.asString();
    }
    public static String uuid(String raw) {
        try { if(raw==null || !raw.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException(); return UUID.fromString(raw).toString(); }
        catch(IllegalArgumentException ex) { throw new ApiException(400,"INVALID_ID","올바른 식별자가 필요합니다."); }
    }
}
