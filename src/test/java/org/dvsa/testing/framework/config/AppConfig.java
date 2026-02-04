package org.dvsa.testing.framework.config;

import java.io.IOException;
import java.io.InputStream;
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
}