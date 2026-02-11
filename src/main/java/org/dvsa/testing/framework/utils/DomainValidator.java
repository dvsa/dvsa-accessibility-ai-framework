package org.dvsa.testing.framework.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;


public class DomainValidator {
    
    private static final Logger LOGGER = LogManager.getLogger(DomainValidator.class);

   
    public static boolean isSameDomain(String url, String baseDomain, boolean allowSubdomains) {
        try {
            if (url == null || baseDomain == null) {
                return false;
            }
            
            if (url.startsWith("/") || url.startsWith("#")) {
                return true;
            }
            
            URL urlObj = new URL(url);
            String host = urlObj.getHost();
            
            if (host == null) {
                return false;
            }
            
            if (host.equalsIgnoreCase(baseDomain)) {
                return true;
            }
            
            if (allowSubdomains && host.endsWith("." + baseDomain)) {
                return true;
            }
            
            return false;
        } catch (MalformedURLException e) {
            LOGGER.warn("Malformed URL: {}", url);
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


    public static boolean isSubdomain(String subdomain, String parentDomain) {
        if (subdomain == null || parentDomain == null) {
            return false;
        }
        
        return subdomain.endsWith("." + parentDomain) && !subdomain.equals(parentDomain);
    }
}