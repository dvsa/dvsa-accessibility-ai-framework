package org.dvsa.testing.framework.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;


public class DomainValidator {
    
    private static final Logger LOGGER = LogManager.getLogger(DomainValidator.class);


    public static boolean isSameDomain(String urlStr, String baseDomain, boolean allowSubdomains) {
        try {
            java.net.URI uri = new java.net.URI(urlStr);
            String host = uri.getHost();

            if (host == null) return false;

            host = host.toLowerCase();
            String base = baseDomain.toLowerCase();

            return host.equals(base) || (allowSubdomains && host.contains(base));
        } catch (Exception e) {
            return false;
        }
    }

    public static String extractDomain(String url) {
        try {
            if (url == null) {
                return null;
            }
            
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (MalformedURLException e) {
            LOGGER.warn("Malformed URL: {}", url);
            return null;
        }
    }
}