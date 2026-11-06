package com.thira.tps.rps.viewer;

import com.thira.tps.document.CanonicalJson;
import tools.jackson.databind.node.*;

final class ReportFixtures {
    private ReportFixtures() {}
    static ObjectNode document(CanonicalJson json,boolean dataset) throws Exception {
        ObjectNode document;
        try(var in=ReportFixtures.class.getResourceAsStream("/native-document.json")) { document=(ObjectNode)json.parse(in.readAllBytes()); }
        ((ObjectNode)document.at("/sections/0/bands/0/elements/0/properties")).remove("futureStyle");
        ((ArrayNode)document.get("requiredFeatures")).add(json.parse("{\"id\":\"tps.qr\",\"version\":\"1\"}"));
        ((ArrayNode)document.at("/sections/0/bands/0/elements")).add(json.parse("{\"id\":\"qr1\",\"type\":\"tps.qr\",\"typeVersion\":\"1\",\"frame\":{\"x\":10000,\"y\":40000,\"width\":30000,\"height\":30000},\"properties\":{\"symbology\":\"QR\",\"encoding\":\"UTF-8\",\"errorCorrection\":\"M\",\"quietZoneModules\":4,\"versionMode\":\"auto\",\"defaultsContract\":\"tps.qr:1\",\"payload\":\"TPS\"},\"bindings\":[],\"children\":[]}"));
        if(dataset) {
            ((ArrayNode)document.get("datasets")).add(ReportDataContract.dataset(json));
            ((ObjectNode)document.at("/sections/0/bands/0")).put("datasetId",ReportDataContract.DATASET_ID);
            ((ArrayNode)document.at("/sections/0/bands/0/elements/0/bindings")).add(binding(json,"text","reportName","error"));
            ((ArrayNode)document.at("/sections/0/bands/0/elements/1/bindings")).add(binding(json,"payload","reportId","error"));
        }
        return document;
    }
    static ObjectNode binding(CanonicalJson json,String property,String field,String policy) {
        return (ObjectNode)json.parse("{\"property\":\""+property+"\",\"source\":{\"kind\":\"field\",\"datasetId\":\"rps-report-list\",\"fieldId\":\""+field+"\"},\"evaluation\":{\"scope\":\"row\",\"event\":\"beforePrint\"},\"nullPolicy\":\""+policy+"\"}");
    }
}
