package com.thira.tps.document;

import java.time.LocalDateTime;
import lombok.Data;

public final class Rows {
    private Rows() {}
    @Data public static class Template {
        private String siteId, templateId, ownerModule, name, headRevisionId, activeRevisionId, createdBy;
        private long headToken, activeToken;
        private boolean enabled;
        private LocalDateTime createdAt;
    }
    @Data public static class Revision {
        private String siteId, templateId, revisionId, parentRevisionId, schemaVersion, bodyJson,
            hashVersion, validationState, diagnosticsJson, createdBy;
        private byte[] bodyHash;
        private long revisionNo;
        private LocalDateTime createdAt;
    }
    @Data public static class Receipt {
        private String siteId, receiptId, actorId, operation, scopeKind, scopeId, requestKey,
            fingerprintVersion, status, resultJson;
        private byte[] fingerprint;
        private LocalDateTime createdAt, completedAt;
    }
    @Data public static class Listed {
        private String templateId, name, ownerModule, headRevisionId, activeRevisionId;
        private long headToken, revisionNo;
        private LocalDateTime createdAt, lastSavedAt;
    }
}
