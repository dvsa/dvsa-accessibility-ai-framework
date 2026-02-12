package org.dvsa.testing.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;


public class AppConfig {
    private static final Properties properties = new Properties();

    static {
        loadProperties();
    }
    
    private static void loadProperties() {
        try (InputStream inputStream = AppConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }
    
    public static String getString(String key) {
        return properties.getProperty(key);
    }
    
    public static String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static String[] getBaseUrls() {
        String rawUrls = properties.getProperty("baseURLs");

        if (rawUrls == null || rawUrls.trim().isEmpty()) {
            return new String[0];
        }

        return Arrays.stream(rawUrls.split(","))
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .toArray(String[]::new);
    }
    
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    public static int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}