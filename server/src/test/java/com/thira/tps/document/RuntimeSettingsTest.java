package com.thira.tps.document;

import com.thira.tps.config.RuntimeSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class RuntimeSettingsTest {
    private MockEnvironment environment() {
        MockEnvironment env=new MockEnvironment().withProperty("server.address","127.0.0.1").withProperty("tps.dev-password","unit-only-password").withProperty("TPS_DB_USER","test").withProperty("TPS_DB_PASSWORD","unit-only");env.setActiveProfiles("local");return env;
    }
    @Test void missingLocalProfileAndPasswordFailClosed() {
        var env=environment();env.setActiveProfiles("production");assertThatThrownBy(()->new RuntimeSettings(env)).isInstanceOf(IllegalStateException.class);
        env.setActiveProfiles("local");env.setProperty("tps.dev-password","");assertThatThrownBy(()->new RuntimeSettings(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void disabledModuleIsEnforcedServerSide() {
        var env=environment().withProperty("tps.enabled-modules","RPS");var settings=new RuntimeSettings(env);
        assertThatCode(()->settings.checkModule("RPS")).doesNotThrowAnyException();
        assertThatThrownBy(()->settings.checkModule("LPS")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
    }
    @Test void certificateExceptionCannotMoveToAnotherEndpoint() {
        var env=environment().withProperty("TPS_DB_TRUST_SERVER_CERTIFICATE","true").withProperty("TPS_DB_HOST","some-other-host");
        assertThatThrownBy(()->new RuntimeSettings(env)).isInstanceOf(IllegalStateException.class);
        env.setProperty("TPS_DB_HOST","10.121.13.159");assertThatCode(()->new RuntimeSettings(env)).doesNotThrowAnyException();
        env.setProperty("TPS_DB_NAME","MES_LITE");assertThatThrownBy(()->new RuntimeSettings(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void siteIsBoundedByUtf8Bytes() {
        var env=environment().withProperty("tps.site-id","가".repeat(14));assertThatThrownBy(()->new RuntimeSettings(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void reportReferenceSiteIsExplicitBoundedAndDefaultsToAuthenticatedSite() {
        var env=environment().withProperty("tps.site-id","editor-site");
        assertThat(new RuntimeSettings(env).getRpsReferenceSiteId()).isEqualTo("editor-site");
        env.setProperty("TPS_RPS_REFERENCE_SITE_ID","reference-site");
        assertThat(new RuntimeSettings(env).getRpsReferenceSiteId()).isEqualTo("reference-site");
        assertThat(new RuntimeSettings(env).getSiteId()).isEqualTo("editor-site");
        env.setProperty("TPS_RPS_REFERENCE_SITE_ID","가".repeat(14));assertThatThrownBy(()->new RuntimeSettings(env)).isInstanceOf(IllegalStateException.class);
        env.setProperty("TPS_RPS_REFERENCE_SITE_ID","");assertThatThrownBy(()->new RuntimeSettings(env)).isInstanceOf(IllegalStateException.class);
    }
}
