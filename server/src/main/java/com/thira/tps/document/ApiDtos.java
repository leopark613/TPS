package com.thira.tps.document;

import java.util.List;
import lombok.*;
import tools.jackson.databind.JsonNode;

/** Transport models: Lombok is intentional; persistence models do not escape the API. */
public final class ApiDtos {
    private ApiDtos() {}
    @Data @NoArgsConstructor @AllArgsConstructor public static class Diagnostic {
        private String severity, code, path, message;
    }
    @Data @NoArgsConstructor public static class TemplateSummary {
        private String templateId, name, ownerModule, headRevisionId, headToken, revisionNo,
            activeRevisionId, createdAt, lastSavedAt;
    }
    @Data @EqualsAndHashCode(callSuper=true) @NoArgsConstructor public static class TemplateView extends TemplateSummary {
        private String revisionId, sequence, bodyHash, headEtag;
        private JsonNode document;
        private List<Diagnostic> diagnostics;
        private boolean editable;
    }
    @Data @NoArgsConstructor public static class RevisionSummary {
        private String revisionId, sequence, bodyHash, createdAt, createdBy;
    }
    @Data @AllArgsConstructor public static class TemplatePage {
        private List<TemplateSummary> items;
        private int page, size;
        private long total;
    }
    @Data @AllArgsConstructor public static class RevisionList { private List<RevisionSummary> items; }
    @Data @AllArgsConstructor public static class Session {
        private String actorId, siteId;
        private List<String> enabledModules;
    }
    @Data @NoArgsConstructor @AllArgsConstructor public static class ResolveResult {
        private String status;
        private TemplateView result;
    }
    @Data @AllArgsConstructor public static class ErrorResponse {
        private String code, message;
        private List<Diagnostic> diagnostics;
        private String currentHeadEtag;
    }
}
