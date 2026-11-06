package com.thira.tps.rps.viewer;

import java.util.List;
import lombok.*;
import com.thira.tps.document.ApiDtos;
import tools.jackson.databind.JsonNode;

public final class ReportViewerDtos {
    private ReportViewerDtos() {}
    @Data @NoArgsConstructor @AllArgsConstructor public static class ReportRow {
        private String reportId,reportName,reportClassId;
    }
    @Data @AllArgsConstructor public static class ReportList {
        private JsonNode dataset;
        private List<ReportRow> rows;
        private boolean truncated;
        private String label;
    }
    @Data @AllArgsConstructor public static class Qr {
        private List<String> matrix;
        private int moduleCount,quietZoneModules,version;
        private String errorCorrection,encoding;
    }
    @Data @AllArgsConstructor public static class PreviewElement {
        private String id,type;
        private JsonNode frame,properties;
        private Qr qr;
    }
    @Data @AllArgsConstructor public static class Preview {
        private String templateId,revisionId,bodyHash;
        private JsonNode queryRef;
        private String executedAt,reportId,resultHash,units;
        private JsonNode page;
        private List<PreviewElement> elements;
        private List<ApiDtos.Diagnostic> diagnostics;
    }
}
