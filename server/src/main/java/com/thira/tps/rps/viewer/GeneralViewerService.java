package com.thira.tps.rps.viewer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.thira.tps.config.RuntimeSettings;
import com.thira.tps.document.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Names follow the active RPS GeneralViewerService; this slice does no printing. */
@Service
public class GeneralViewerService {
    private final DocumentService documents;
    private final DocumentValidator validator;
    private final ReportQuery query;
    private final RuntimeSettings settings;
    private final CanonicalJson json;
    private final QrEncoder qr;
    public GeneralViewerService(DocumentService documents,DocumentValidator validator,ReportQuery query,RuntimeSettings settings,CanonicalJson json,QrEncoder qr) {
        this.documents=documents;this.validator=validator;this.query=query;this.settings=settings;this.json=json;this.qr=qr;
    }
    public ReportViewerDtos.ReportList reportList() {
        settings.checkModule("RPS");
        try {
            var rows=query.reportList(settings.getRpsReferenceSiteId());
            return new ReportViewerDtos.ReportList(ReportDataContract.dataset(json),List.copyOf(rows.subList(0,Math.min(100,rows.size()))),rows.size()>100,"로컬 보고서 목록 예제");
        } catch(DataAccessException ex) { throw unavailable(); }
    }
    public ReportViewerDtos.Preview preview(String templateId,String revisionId,String reportId) {
        settings.checkModule("RPS");
        var view=documents.historical(CanonicalJson.uuid(templateId),CanonicalJson.uuid(revisionId));
        if(!"RPS".equals(view.getOwnerModule())) throw new ApiException(422,"QUERY_MODULE_MISMATCH","이 미리보기는 RPS 보고서에서만 사용할 수 있습니다.");
        JsonNode doc=view.getDocument();
        // A historical view is read-only by definition; check capabilities independently.
        var checked=validator.check(doc);
        for(JsonNode dataset:doc.get("datasets")) if(!ReportDataContract.querySupported(dataset.get("queryRef")))
            fail("QUERY_NOT_SUPPORTED","/datasets/queryRef","이 조회 정의와 버전은 지원하지 않습니다.");
        if(!checked.isEditable()) fail("PREVIEW_NOT_SUPPORTED","/","현재 미리보기가 지원하지 않는 양식 기능이 있습니다.");
        boolean hasDataset=!doc.get("datasets").isEmpty();
        if(hasDataset) validateReportId(reportId);
        else if(reportId!=null) throw new ApiException(400,"UNEXPECTED_REPORT_ID","데이터를 연결하지 않은 양식에는 조회 행을 지정하지 않습니다.");
        JsonNode section=doc.get("sections").get(0),page=section.get("page"),band=section.get("bands").get(0);
        validateLayout(page,band);
        // Validate every element before executing the query. Unknown properties are preserved
        // by the editor, but are never silently omitted from a successful preview.
        for(JsonNode element:band.get("elements")) validateElement(page,band,element);
        ObjectNode data=hasDataset?bindDataSource(reportId):json.object();
        List<ReportViewerDtos.PreviewElement> result=new ArrayList<>();
        long qrModules=0;
        for(JsonNode element:band.get("elements")) {
            String id=element.get("id").asString(),type=element.get("type").asString();
            ObjectNode properties=(ObjectNode)element.get("properties").deepCopy();
            for(JsonNode binding:element.get("bindings")) {
                String fieldId=binding.get("source").get("fieldId").asString();
                JsonNode value=data.get(fieldId);
                if(value==null) fail("BINDING_FIELD_MISSING","/elements/"+id,"연결한 조회 필드가 없습니다.");
                if(value.isNull()) {
                    if("error".equals(binding.get("nullPolicy").asString())) fail("BINDING_VALUE_REQUIRED","/elements/"+id,"필수 데이터 값이 없습니다.");
                    properties.put(binding.get("property").asString(),"");
                } else if(!value.isString()) fail("BINDING_TYPE_MISMATCH","/elements/"+id,"문자열 데이터가 필요합니다.");
                else properties.put(binding.get("property").asString(),value.asString());
            }
            ReportViewerDtos.Qr encoded=null;
            if("tps.qr".equals(type)) {
                encoded=qr.encode(properties.path("payload").asString(null));
                qrModules+=(long)encoded.getModuleCount()*encoded.getModuleCount();
                if(qrModules>2_000_000) fail("PREVIEW_TOO_COMPLEX","/elements","한 번에 미리 볼 QR 데이터가 너무 많습니다.");
            }
            result.add(new ReportViewerDtos.PreviewElement(id,type,element.get("frame").deepCopy(),properties,encoded));
        }
        return new ReportViewerDtos.Preview(view.getTemplateId(),view.getRevisionId(),view.getBodyHash(),hasDataset?doc.get("datasets").get(0).get("queryRef").deepCopy():null,
            Instant.now().toString(),reportId,HexFormat.of().formatHex(json.hash(json.canonical(data))),"um",page.deepCopy(),List.copyOf(result),List.of());
    }
    /** Only the registered local report metadata SELECT can supply this dataset. */
    public ObjectNode bindDataSource(String reportId) {
        settings.checkModule("RPS"); validateReportId(reportId);
        try {
            var row=query.reportInfo(settings.getRpsReferenceSiteId(),reportId);
            if(row==null) throw new ApiException(404,"REPORT_DATA_NOT_FOUND","선택한 보고서 데이터를 찾을 수 없습니다.");
            ObjectNode value=json.object(); value.put("reportId",row.getReportId());value.put("reportName",row.getReportName());value.put("reportClassId",row.getReportClassId());return value;
        } catch(DataAccessException ex) { throw unavailable(); }
    }
    private static void validateReportId(String id) {
        if(id==null || id.isEmpty() || id.getBytes(StandardCharsets.UTF_8).length>40)
            throw new ApiException(400,"INVALID_REPORT_ID","보고서 데이터 행을 선택하세요.");
    }
    private static void validateLayout(JsonNode page,JsonNode band) {
        if(!band.get("properties").isEmpty()) fail("UNSUPPORTED_PREVIEW_PROPERTY","/sections/bands/properties","현재 미리보기가 지원하지 않는 구간 속성이 있습니다.");
        long contentHeight=page.get("height").asLong()-page.get("margins").get("top").asLong()-page.get("margins").get("bottom").asLong();
        if(band.get("height").asLong()<1 || band.get("height").asLong()>contentHeight)
            fail("BAND_OUT_OF_BOUNDS","/sections/bands/height","구간 높이가 용지 영역을 벗어납니다.");
    }
    private static void validateElement(JsonNode page,JsonNode band,JsonNode element) {
        String type=element.get("type").asString(),path="/elements/"+element.get("id").asString();
        Set<String> keys=switch(type) {
            case "tps.text" -> Set.of("text","fontSizePt","color");
            case "tps.rectangle" -> Set.of("fill","stroke");
            case "tps.qr" -> Set.of("symbology","encoding","errorCorrection","quietZoneModules","versionMode","defaultsContract","payload");
            default -> Set.of();
        };
        JsonNode properties=element.get("properties");
        if(properties.properties().stream().anyMatch(e->!keys.contains(e.getKey())))
            fail("UNSUPPORTED_PREVIEW_PROPERTY",path+"/properties","현재 미리보기가 지원하지 않는 요소 속성이 있습니다.");
        if("tps.text".equals(type)) {
            if(properties.has("text") && !properties.get("text").isString()) fail("INVALID_PREVIEW_PROPERTY",path,"문자 내용을 확인하세요.");
            if(properties.has("fontSizePt")) {
                try {
                    if(!properties.get("fontSizePt").isString()) throw new NumberFormatException();
                    BigDecimal size=new BigDecimal(properties.get("fontSizePt").asString());
                    if(size.compareTo(BigDecimal.valueOf(4))<0 || size.compareTo(BigDecimal.valueOf(200))>0) throw new NumberFormatException();
                } catch(NumberFormatException ex) { fail("INVALID_PREVIEW_PROPERTY",path,"글자 크기는 4~200 pt여야 합니다."); }
            }
        }
        for(String color:List.of("color","fill","stroke")) if(properties.has(color) && (!properties.get(color).isString() || !properties.get(color).asString().matches("#[0-9a-fA-F]{6}")))
            fail("INVALID_PREVIEW_PROPERTY",path,"색상 형식을 확인하세요.");
        JsonNode frame=element.get("frame"),margins=page.get("margins");
        long x=frame.get("x").asLong(),y=frame.get("y").asLong(),w=frame.get("width").asLong(),h=frame.get("height").asLong();
        long availableW=page.get("width").asLong()-margins.get("left").asLong()-margins.get("right").asLong(),availableH=band.get("height").asLong();
        if(x<0 || y<0 || x>availableW || y>availableH || w<1 || h<1 || w>availableW-x || h>availableH-y)
            fail("ELEMENT_OUT_OF_BOUNDS",path+"/frame","요소가 용지 또는 구간 영역을 벗어납니다.");
        if("tps.qr".equals(type) && w!=h) fail("QR_FRAME_NOT_SQUARE",path+"/frame","QR 영역의 가로와 세로를 같게 지정하세요.");
    }
    private static ApiException unavailable() { return new ApiException(503,"REPORT_DATA_UNAVAILABLE","보고서 데이터를 조회할 수 없습니다. 연결 상태를 확인하세요."); }
    private static void fail(String code,String path,String message) { throw new ApiException(422,code,message,List.of(new ApiDtos.Diagnostic("ERROR",code,path,message)),null); }
}
