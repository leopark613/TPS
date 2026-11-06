package com.thira.tps.document;

import java.util.List;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<ApiDtos.Diagnostic> diagnostics;
    private final String currentHeadEtag;
    public ApiException(int status, String code, String message) { this(status, code, message, List.of(), null); }
    public ApiException(int status, String code, String message, List<ApiDtos.Diagnostic> diagnostics, String etag) {
        super(message); this.status=status; this.code=code; this.diagnostics=diagnostics; this.currentHeadEtag=etag;
    }
}
