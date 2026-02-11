package org.dvsa.testing.framework.mcp.config;

import org.dvsa.testing.framework.config.AppConfig;

/**
 * Configuration for the MCP server
 */
public class McpConfig {
    
    // Server configuration
    public static final String MCP_SERVER_HOST = AppConfig.getString("mcp.server.host", "localhost");
    public static final int MCP_SERVER_PORT = AppConfig.getInt("mcp.server.port", 8080);
    public static final String MCP_SERVER_ENDPOINT = AppConfig.getString("mcp.server.endpoint", "http://" + MCP_SERVER_HOST + ":" + MCP_SERVER_PORT);
    
    // Cache configuration
    public static final int CACHE_TTL_HOURS = AppConfig.getInt("mcp.cache.ttl.hours", 24);
    public static final int CACHE_MAX_SIZE = AppConfig.getInt("mcp.cache.max.size", 1000);
    public static final boolean CACHE_ENABLED = AppConfig.getBoolean("mcp.cache.enabled", true);
    
    // Scraping configuration
    public static final int SCRAPE_TIMEOUT_SECONDS = AppConfig.getInt("mcp.scrape.timeout.seconds", 30);
    public static final int SCRAPE_RETRY_COUNT = AppConfig.getInt("mcp.scrape.retry.count", 3);
    public static final long SCRAPE_DELAY_MS = AppConfig.getInt("mcp.scrape.delay.ms", 1000);
    
    // Source URLs
    public static final String GOVUK_DESIGN_SYSTEM_URL = "https://design-system.service.gov.uk";
    public static final String W3C_WCAG_URL = "https://www.w3.org/WAI/WCAG22";
    public static final String W3C_ARIA_URL = "https://www.w3.org/WAI/ARIA";
    
    // Fallback configuration
    public static final boolean USE_S3_FALLBACK = AppConfig.getBoolean("mcp.s3.fallback.enabled", true);
    public static final String S3_FALLBACK_BUCKET = AppConfig.getString("mcp.s3.fallback.bucket", "accessibility-guidelines-backup");
    public static final String S3_FALLBACK_PREFIX = AppConfig.getString("mcp.s3.fallback.prefix", "guidelines/");
    
    private McpConfig() {
        // Utility class - no instantiation
    }
}