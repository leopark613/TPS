package com.thira.tps.rps.viewer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.thira.tps.document.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportViewerControllerTest {
    private final GeneralViewerService service=mock(GeneralViewerService.class);
    private final ReportViewerController controller=new ReportViewerController(service,new CanonicalJson());
    private final String template=UUID.randomUUID().toString(),revision=UUID.randomUUID().toString();
    private MockHttpServletRequest body(String json) { var request=new MockHttpServletRequest();request.setContent(json.getBytes(StandardCharsets.UTF_8));return request; }
    @Test void exactBodyPassesCanonicalIdsAndNullableSelection() throws Exception {
        controller.preview(body("{\"templateId\":\""+template+"\",\"revisionId\":\""+revision+"\",\"reportId\":null}"));
        verify(service).preview(template,revision,null);
    }
    @Test void callerCannotInjectSourceSiteOrQuerySql() {
        assertThatThrownBy(()->controller.preview(body("{\"templateId\":\""+template+"\",\"revisionId\":\""+revision+"\",\"reportId\":\"r\",\"sourceSiteId\":\"other\"}"))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(service);
    }
    @Test void invalidReportSelectionTypeAndMissingKeysFailBeforeService() {
        assertThatThrownBy(()->controller.preview(body("{\"templateId\":\""+template+"\",\"revisionId\":\""+revision+"\",\"reportId\":12}"))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_REQUEST");
        assertThatThrownBy(()->controller.preview(body("{}"))).isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_REQUEST");verifyNoInteractions(service);
    }
}
