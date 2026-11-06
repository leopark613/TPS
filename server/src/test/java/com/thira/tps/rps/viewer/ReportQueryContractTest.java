package com.thira.tps.rps.viewer;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReportQueryContractTest {
    @Test void mapperHasOnlyBoundLocalReadsAndFiveSecondTimeout() throws Exception {
        var configuration=new Configuration();
        try(var input=getClass().getResourceAsStream("/mapper/ReportQuery.xml")) {
            new XMLMapperBuilder(input,configuration,"mapper/ReportQuery.xml",configuration.getSqlFragments()).parse();
        }
        String prefix="com.thira.tps.rps.viewer.ReportQuery.";
        var list=configuration.getMappedStatement(prefix+"reportList");
        var info=configuration.getMappedStatement(prefix+"reportInfo");
        assertThat(list.getTimeout()).isEqualTo(5);assertThat(info.getTimeout()).isEqualTo(5);
        var listSql=list.getBoundSql(Map.of("sourceSiteId","private-site"));
        assertThat(listSql.getSql()).contains("TOP(101)","dbo.RPS_REPORT","SITEID=?").doesNotContain("private-site","XMLDATA","MESSAGESET","STOREDQUERY");
        assertThat(listSql.getParameterMappings()).extracting("property").containsExactly("sourceSiteId");
        var infoSql=info.getBoundSql(Map.of("sourceSiteId","private-site","reportId","x' OR 1=1 --"));
        assertThat(infoSql.getSql()).contains("SITEID=?","REPORTID=?").doesNotContain("OR 1=1");
        assertThat(infoSql.getParameterMappings()).extracting("property").containsExactly("sourceSiteId","reportId");
    }
}
