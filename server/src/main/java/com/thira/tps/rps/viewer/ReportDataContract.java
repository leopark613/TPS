package com.thira.tps.rps.viewer;

import java.util.*;
import com.thira.tps.document.*;
import tools.jackson.databind.JsonNode;

/** Versioned, fixed local example. This is not a StoredQuery SQL execution API. */
public final class ReportDataContract {
    public static final String DATASET_ID="rps-report-list";
    public static final List<String> FIELDS=List.of("reportId","reportName","reportClassId");
    private ReportDataContract() {}
    public static JsonNode dataset(CanonicalJson json) {
        return json.parse("{\"id\":\"rps-report-list\",\"queryRef\":{\"id\":\"ReportList\",\"classId\":\"TPS_LOCAL_SAMPLE\",\"version\":\"1\"},\"ordinal\":0,\"fields\":[{\"id\":\"reportId\",\"path\":[\"reportId\"],\"type\":\"string\"},{\"id\":\"reportName\",\"path\":[\"reportName\"],\"type\":\"string\"},{\"id\":\"reportClassId\",\"path\":[\"reportClassId\"],\"type\":\"string\"}]}");
    }
    public static boolean supportedDataset(JsonNode dataset) {
        if(!DATASET_ID.equals(dataset.path("id").asString()) || dataset.path("ordinal").asLong(-1)!=0 || !querySupported(dataset.path("queryRef"))) return false;
        JsonNode fields=dataset.path("fields");
        if(fields.size()!=FIELDS.size()) return false;
        Set<String> found=new HashSet<>();
        for(JsonNode field:fields) {
            String id=field.path("id").asString();
            if(!FIELDS.contains(id) || !found.add(id) || !"string".equals(field.path("type").asString()) || field.path("path").size()!=1 || !id.equals(field.path("path").path(0).asString())) return false;
        }
        return true;
    }
    public static boolean querySupported(JsonNode query) {
        return "ReportList".equals(query.path("id").asString()) && "TPS_LOCAL_SAMPLE".equals(query.path("classId").asString()) && "1".equals(query.path("version").asString());
    }
    public static void checkOwner(String owner,JsonNode document) {
        if("RPS".equals(owner)) return;
        for(JsonNode dataset:document.path("datasets")) {
            if("ReportList".equals(dataset.path("queryRef").path("id").asString()) && "TPS_LOCAL_SAMPLE".equals(dataset.path("queryRef").path("classId").asString()))
                throw new ApiException(422,"QUERY_MODULE_MISMATCH","보고서 목록 예제는 RPS 영역에서만 연결할 수 있습니다.");
        }
    }
    public static boolean supportedBinding(JsonNode element,JsonNode binding) {
        String type=element.path("type").asString();
        String property="tps.text".equals(type)?"text":"tps.qr".equals(type)?"payload":null;
        JsonNode source=binding.path("source"),evaluation=binding.path("evaluation");
        return property!=null && property.equals(binding.path("property").asString()) && "field".equals(source.path("kind").asString()) &&
            DATASET_ID.equals(source.path("datasetId").asString()) && FIELDS.contains(source.path("fieldId").asString()) &&
            "row".equals(evaluation.path("scope").asString()) && "beforePrint".equals(evaluation.path("event").asString()) &&
            Set.of("empty","error").contains(binding.path("nullPolicy").asString());
    }
    public static boolean supportedQrProperties(JsonNode properties) {
        return "QR".equals(properties.path("symbology").asString()) && "UTF-8".equals(properties.path("encoding").asString()) &&
            "M".equals(properties.path("errorCorrection").asString()) && properties.path("quietZoneModules").isIntegralNumber() && properties.path("quietZoneModules").asLong()==4 &&
            "auto".equals(properties.path("versionMode").asString()) && "tps.qr:1".equals(properties.path("defaultsContract").asString()) &&
            (!properties.has("payload") || properties.path("payload").isString());
    }
}
