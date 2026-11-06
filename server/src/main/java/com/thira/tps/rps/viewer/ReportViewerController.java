package com.thira.tps.rps.viewer;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import com.thira.tps.document.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/tps/rps")
public class ReportViewerController {
    private final GeneralViewerService service;
    private final CanonicalJson json;
    public ReportViewerController(GeneralViewerService service,CanonicalJson json) { this.service=service;this.json=json; }
    @GetMapping("/ReportList") public ReportViewerDtos.ReportList reportList() { return service.reportList(); }
    @PostMapping("/Preview") public ReportViewerDtos.Preview preview(HttpServletRequest request) throws IOException {
        if(request.getContentLengthLong()>CanonicalJson.MAX_BYTES) throw new ApiException(413,"REQUEST_TOO_LARGE","요청 크기를 확인하세요.");
        JsonNode body=json.parse(request.getInputStream().readNBytes(CanonicalJson.MAX_BYTES+1));
        Set<String> keys=Set.of("templateId","revisionId","reportId");
        if(!body.isObject() || body.properties().stream().anyMatch(e->!keys.contains(e.getKey())) || keys.stream().anyMatch(k->!body.has(k)) ||
            !(body.get("reportId").isNull() || body.get("reportId").isString())) throw new ApiException(400,"INVALID_REQUEST","요청 항목을 확인하세요.");
        return service.preview(CanonicalJson.uuid(json.text(body,"templateId")),CanonicalJson.uuid(json.text(body,"revisionId")),body.get("reportId").isNull()?null:body.get("reportId").asString());
    }
}
