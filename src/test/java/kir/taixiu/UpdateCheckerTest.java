package kir.taixiu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test
    void comparesSemanticVersions() {
        assertEquals(1, ReleaseVersion.compare("v1.1.0", "1.0.2"));
        assertEquals(0, ReleaseVersion.compare("v1.0.2", "1.0.2"));
        assertEquals(-1, ReleaseVersion.compare("1.0.1", "v1.0.2"));
        assertEquals(1, ReleaseVersion.compare("2.0.0", "1.9.9"));
        assertEquals(0, ReleaseVersion.compare("v1.0.2", "V1.0.2"));
    }

    @Test
    void parsesLatestReleaseResponse() {
        String json = "{\"tag_name\":\"v1.1.0\",\"html_url\":\"https://github.com/rogteam/KirTaiXiu/releases/tag/v1.1.0\"}";
        assertEquals("v1.1.0", ReleaseVersion.tagFromJson(json));
        assertEquals("https://github.com/rogteam/KirTaiXiu/releases/tag/v1.1.0", ReleaseVersion.urlFromJson(json, "fallback"));
    }

    @Test
    void parsesEmptyOrNullJsonGracefully() {
        assertTrue(ReleaseVersion.tagFromJson(null).isBlank());
        assertTrue(ReleaseVersion.tagFromJson("{}").isBlank());
        assertEquals("fallback", ReleaseVersion.urlFromJson("{}", "fallback"));
    }
}
