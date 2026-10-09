package de.growcentral.touranlive;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdatePolicyTest {
    @Test public void allowsOwnedHttpsDeployment() throws Exception {
        assertEquals("www.dezender.de", UpdatePolicy.url("https://www.dezender.de/touran/TouranLive-debug.apk").getHost());
        UpdatePolicy.url("https://dezender.de/touran/update.json");
    }
    @Test public void rejectsDowngradeForeignOriginAndPathTricks() {
        for (String url : new String[]{"http://www.dezender.de/touran/a.apk", "https://www.dezender.de.evil.example/touran/a.apk",
                "https://www.dezender.de:8443/touran/a.apk", "https://user@www.dezender.de/touran/a.apk",
                "https://www.dezender.de/other/a.apk", "https://www.dezender.de/touran/../a.apk", "https://www.dezender.de/touran/%2e%2e/a.apk"}) {
            assertThrows(Exception.class, () -> UpdatePolicy.url(url));
        }
    }
    @Test public void requiresExactSha256() {
        String hash = new String(new char[64]).replace('\0', 'A');
        assertEquals(hash.toLowerCase(java.util.Locale.ROOT), UpdatePolicy.checksum(hash));
        for (String s : new String[]{"", "abc", hash + "a", hash.replace('A', 'Z')}) {
            assertThrows(IllegalArgumentException.class, () -> UpdatePolicy.checksum(s));
        }
    }
}
