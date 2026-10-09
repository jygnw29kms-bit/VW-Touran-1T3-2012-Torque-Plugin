package de.growcentral.touranlive;

import java.net.MalformedURLException;
import java.net.URL;

/** Deployment-specific update policy: HTTPS, owned origin and a required SHA-256. */
final class UpdatePolicy {
    static URL url(String value) throws MalformedURLException {
        URL u = new URL(value);
        if (!"https".equals(u.getProtocol()) ||
                !("www.dezender.de".equalsIgnoreCase(u.getHost()) || "dezender.de".equalsIgnoreCase(u.getHost())) ||
                (u.getPort() != -1 && u.getPort() != 443) || u.getUserInfo() != null || u.getRef() != null ||
                !u.getPath().startsWith("/touran/") || u.getPath().contains("..") || u.getPath().contains("%")) {
            throw new MalformedURLException("Update-URL außerhalb des freigegebenen HTTPS-Ursprungs");
        }
        return u;
    }

    static String checksum(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Update benötigt eine gültige SHA-256-Prüfsumme");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
