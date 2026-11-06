package com.thira.tps.rps.viewer;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReportQuery {
    List<ReportViewerDtos.ReportRow> reportList(@Param("sourceSiteId") String sourceSiteId);
    ReportViewerDtos.ReportRow reportInfo(@Param("sourceSiteId") String sourceSiteId,@Param("reportId") String reportId);
}
