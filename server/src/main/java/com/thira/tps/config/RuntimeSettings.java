package com.thira.tps.config;

import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.Getter;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.thira.tps.document.ApiException;

@Getter
@Component
public class RuntimeSettings {
    private final String siteId;
    private final List<String> enabledModules;
    private final String developerPassword;
    public RuntimeSettings(Environment env) {
        if (!Arrays.asList(env.getActiveProfiles()).contains("local"))
            throw new IllegalStateException("This slice requires the local profile; production authentication is not configured.");
        siteId=env.getProperty("tps.site-id", "TPS_DEV");
        if(siteId.isBlank() || siteId.getBytes(StandardCharsets.UTF_8).length>40)
            throw new IllegalStateException("TPS_SITE_ID must contain 1..40 UTF-8 bytes.");
        developerPassword=env.getProperty("tps.dev-password", "");
        if(developerPassword.length()<12) throw new IllegalStateException("TPS_DEV_PASSWORD must be supplied (at least 12 characters).");
        enabledModules=Arrays.stream(env.getProperty("tps.enabled-modules", "RPS,LPS").split(",")).map(String::trim).distinct().toList();
        if(enabledModules.isEmpty() || !Set.of("RPS","LPS").containsAll(enabledModules))
            throw new IllegalStateException("TPS_ENABLED_MODULES accepts RPS,LPS only.");
        if(!"127.0.0.1".equals(env.getProperty("server.address")))
            throw new IllegalStateException("Local development must bind to 127.0.0.1.");
        if(!"TPS".equals(env.getProperty("TPS_DB_NAME", "TPS")))
            throw new IllegalStateException("This slice requires the local TPS database.");
        String host=env.getProperty("TPS_DB_HOST","127.0.0.1"),port=env.getProperty("TPS_DB_PORT","1433"),trust=env.getProperty("TPS_DB_TRUST_SERVER_CERTIFICATE","false");
        if(!host.matches("[A-Za-z0-9.-]+") || !port.matches("[0-9]{1,5}") || Integer.parseInt(port)<1 || Integer.parseInt(port)>65535 || !Set.of("true","false").contains(trust))
            throw new IllegalStateException("Invalid local database endpoint settings.");
        String expected="jdbc:sqlserver://"+host+":"+port+";databaseName=TPS;encrypt=true;trustServerCertificate="+trust+";loginTimeout=8;applicationName=TPS";
        if(!expected.equals(env.getProperty("spring.datasource.url",expected)))
            throw new IllegalStateException("Use TPS_DB settings; overriding the protected datasource URL is not supported in this local slice.");
        if(Boolean.parseBoolean(env.getProperty("TPS_DB_TRUST_SERVER_CERTIFICATE", "false")) &&
           !"10.121.13.159".equals(env.getProperty("TPS_DB_HOST")))
            throw new IllegalStateException("Certificate exception is restricted to the authorized local TPS server.");
        if(env.getProperty("TPS_DB_USER", "").isBlank() || env.getProperty("TPS_DB_PASSWORD", "").isEmpty())
            throw new IllegalStateException("Local database credentials must be supplied at runtime.");
    }
    public void checkModule(String module) {
        if(!Set.of("RPS","LPS").contains(module)) throw new ApiException(400,"INVALID_MODULE","업무 영역을 확인하세요.");
        if(!enabledModules.contains(module)) throw new ApiException(403,"MODULE_DISABLED","사용하지 않는 업무 영역입니다.");
    }
}
