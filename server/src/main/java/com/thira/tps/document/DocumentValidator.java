package com.thira.tps.document;

import java.io.IOException;
import java.util.*;
import com.networknt.schema.*;
import com.networknt.schema.dialect.Dialects;
import com.thira.tps.rps.viewer.ReportDataContract;
import lombok.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class DocumentValidator {
    private final Schema schema;
    private final CanonicalJson json;
    private static final Set<String> BASIC=Set.of("tps.text","tps.rectangle","tps.qr");
    public DocumentValidator(CanonicalJson json) throws IOException {
        this.json=json;
        try(var input=new ClassPathResource("schema/document.schema.json").getInputStream()) {
            schema=SchemaRegistry.withDialect(Dialects.getDraft202012()).getSchema(input);
        }
    }
    @Value public static class Checked {
        JsonNode document;
        String canonical;
        byte[] hash;
        List<ApiDtos.Diagnostic> diagnostics;
        boolean editable;
    }
    public Checked check(JsonNode document) {
        if(document==null || !document.isObject()) throw new ApiException(422,"DOCUMENT_REQUIRED","양식 본문이 필요합니다.");
        JsonNode doc=json.normalize(document,0);
        var structural=schema.validate(doc);
        if(!structural.isEmpty()) {
            var errors=structural.stream().limit(20).map(e -> new ApiDtos.Diagnostic("ERROR","SCHEMA_INVALID",e.getInstanceLocation().toString(),"양식 구조를 확인하세요 ("+e.getKeyword()+").")).toList();
            throw new ApiException(422,"DOCUMENT_INVALID","양식 구조가 올바르지 않습니다.",errors,null);
        }
        if(doc.get("title").asString().length()>200) invalid("TITLE_TOO_LONG","/title","양식 이름은 200자 이하여야 합니다.");
        if(!doc.get("assets").isEmpty()) invalid("ASSET_UPLOAD_NOT_AVAILABLE","/assets","파일 첨부 저장은 다음 구현 단계에서 제공됩니다.");
        Set<String> ids=new HashSet<>(), elements=new HashSet<>();
        Map<String,Set<String>> datasets=new HashMap<>();
        Set<Long> ordinals=new HashSet<>();
        for(JsonNode dataset:doc.get("datasets")) {
            Set<String> fields=new HashSet<>();
            if(datasets.putIfAbsent(dataset.get("id").asString(),fields)!=null) invalid("DUPLICATE_ID","/datasets","데이터 집합 식별자가 중복됩니다.");
            if(!ordinals.add(dataset.get("ordinal").asLong())) invalid("DUPLICATE_ORDINAL","/datasets","데이터 집합 순번이 중복됩니다.");
            for(JsonNode field:dataset.get("fields")) if(!fields.add(field.get("id").asString())) invalid("DUPLICATE_ID","/datasets/fields","필드 식별자가 중복됩니다.");
        }
        List<ApiDtos.Diagnostic> diagnostics=new ArrayList<>();
        boolean basic=doc.get("sections").size()==1 && (doc.get("datasets").isEmpty() ||
            doc.get("datasets").size()==1 && ReportDataContract.supportedDataset(doc.get("datasets").get(0)));
        for(JsonNode feature:doc.get("requiredFeatures")) {
            if(!BASIC.contains(feature.get("id").asString()) || !"1".equals(feature.get("version").asString())) {
                basic=false; warning(diagnostics,"UNSUPPORTED_FEATURE","/requiredFeatures","이 양식에 현재 편집기가 지원하지 않는 기능이 있습니다.");
            }
        }
        int[] elementCount={0};
        for(JsonNode section:doc.get("sections")) {
            unique(ids,section.get("id").asString(),"/sections");
            JsonNode page=section.get("page"), margins=page.get("margins");
            long width=page.get("width").asLong(),height=page.get("height").asLong();
            if(margins.get("left").asLong()+margins.get("right").asLong()>=width || margins.get("top").asLong()+margins.get("bottom").asLong()>=height)
                invalid("INVALID_MARGINS","/sections/page","여백을 제외한 용지 크기가 양수여야 합니다.");
            basic &= width<=2000000 && height<=2000000 && section.get("bands").size()==1;
            for(JsonNode band:section.get("bands")) basic &= band(band,ids,elements,datasets,elementCount,diagnostics);
        }
        Set<String> origins=new HashSet<>();
        for(JsonNode origin:doc.get("provenance").get("elementOrigins")) {
            String id=origin.get("elementId").asString();
            if(!elements.contains(id)) invalid("MISSING_ELEMENT_REFERENCE","/provenance/elementOrigins","원본 위치가 존재하지 않는 요소를 참조합니다.");
            if(!origins.add(id)) invalid("DUPLICATE_ORIGIN","/provenance/elementOrigins","요소 원본 위치가 중복됩니다.");
        }
        if(!basic) warning(diagnostics,"READ_ONLY_DOCUMENT","/","저장된 내용은 보존되며 현재 편집 범위를 넘어 읽기 전용으로 열립니다.");
        String canonical=json.canonical(doc);
        if(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>CanonicalJson.MAX_BYTES)
            throw new ApiException(413,"REQUEST_TOO_LARGE","문서 크기는 1 MiB 이하여야 합니다.");
        return new Checked(doc,canonical,json.hash(canonical),List.copyOf(diagnostics),basic);
    }
    private boolean band(JsonNode band,Set<String> ids,Set<String> elements,Map<String,Set<String>> datasets,int[] count,List<ApiDtos.Diagnostic> diagnostics) {
        unique(ids,band.get("id").asString(),"/sections/bands");
        if(band.has("datasetId") && !datasets.containsKey(band.get("datasetId").asString())) invalid("MISSING_DATASET_REFERENCE","/sections/bands/datasetId","데이터 집합을 찾을 수 없습니다.");
        boolean basic="detail".equals(band.get("kind").asString()) && band.get("childBands").isEmpty() &&
            (!band.has("datasetId") || ReportDataContract.DATASET_ID.equals(band.get("datasetId").asString()));
        for(JsonNode element:band.get("elements")) basic &= element(element,ids,elements,datasets,count,diagnostics);
        for(JsonNode child:band.get("childBands")) band(child,ids,elements,datasets,count,diagnostics);
        return basic;
    }
    private boolean element(JsonNode element,Set<String> ids,Set<String> elements,Map<String,Set<String>> datasets,int[] count,List<ApiDtos.Diagnostic> diagnostics) {
        if(++count[0]>500) invalid("TOO_MANY_ELEMENTS","/sections","요소는 최대 500개까지 저장할 수 있습니다.");
        String id=element.get("id").asString(); unique(ids,id,"/sections/elements"); elements.add(id);
        boolean supported=BASIC.contains(element.get("type").asString()) && "1".equals(element.get("typeVersion").asString());
        if(!supported) warning(diagnostics,"UNSUPPORTED_ELEMENT","/sections/elements","지원하지 않는 요소를 원본 그대로 보존했습니다.");
        boolean basic=supported && element.get("children").isEmpty();
        if("tps.qr".equals(element.get("type").asString()) && !ReportDataContract.supportedQrProperties(element.get("properties"))) basic=false;
        if(element.get("frame").has("rotationMilliDegrees") && element.get("frame").get("rotationMilliDegrees").asLong()!=0) basic=false;
        Set<String> targets=new HashSet<>();
        for(JsonNode binding:element.get("bindings")) {
            if(!targets.add(binding.get("property").asString())) invalid("DUPLICATE_BINDING","/sections/elements/bindings","같은 속성에 여러 데이터 연결을 지정할 수 없습니다.");
            basic &= ReportDataContract.supportedBinding(element,binding);
            JsonNode source=binding.get("source");
            if("field".equals(source.get("kind").asString())) {
                Set<String> fields=datasets.get(source.get("datasetId").asString());
                if(fields==null || !fields.contains(source.get("fieldId").asString())) invalid("MISSING_FIELD_REFERENCE","/sections/elements/bindings","연결한 데이터 필드를 찾을 수 없습니다.");
            }
        }
        for(JsonNode child:element.get("children")) element(child,ids,elements,datasets,count,diagnostics);
        return basic;
    }
    private static void unique(Set<String> ids,String id,String path) { if(!ids.add(id)) invalid("DUPLICATE_ID",path,"섹션·밴드·요소 식별자는 중복될 수 없습니다."); }
    private static void invalid(String code,String path,String message) { throw new ApiException(422,code,message,List.of(new ApiDtos.Diagnostic("ERROR",code,path,message)),null); }
    private static void warning(List<ApiDtos.Diagnostic> ds,String code,String path,String message) { if(ds.stream().noneMatch(d->d.getCode().equals(code))) ds.add(new ApiDtos.Diagnostic("WARNING",code,path,message)); }
}
