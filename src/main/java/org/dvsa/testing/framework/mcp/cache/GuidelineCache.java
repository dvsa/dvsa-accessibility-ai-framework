package org.dvsa.testing.framework.mcp.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.mcp.models.AccessibilityGuideline;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Cache management for accessibility guidelines
 * Provides TTL-based caching with configurable expiration
 */
public class GuidelineCache {
    private static final Logger LOGGER = LogManager.getLogger(GuidelineCache.class);
    
    private static final int DEFAULT_TTL_HOURS = 24; // Cache for 24 hours
    private static final int FORCE_REFRESH_TTL_DAYS = 7; // Force refresh weekly
    
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    private static class CacheEntry {
        final List<AccessibilityGuideline> guidelines;
        final LocalDateTime cachedAt;
        final int ttlHours;
        
        CacheEntry(List<AccessibilityGuideline> guidelines, int ttlHours) {
            this.guidelines = guidelines;
            this.cachedAt = LocalDateTime.now();
            this.ttlHours = ttlHours;
        }
        
        boolean isExpired() {
            return cachedAt.plus(ttlHours, ChronoUnit.HOURS).isBefore(LocalDateTime.now());
        }
        
        boolean isStale() {
            return cachedAt.plus(FORCE_REFRESH_TTL_DAYS, ChronoUnit.DAYS).isBefore(LocalDateTime.now());
        }
    }
    
    /**
     * Get guidelines from cache or fetch fresh if expired
     */
    public List<AccessibilityGuideline> getOrFetch(String key, Supplier<List<AccessibilityGuideline>> fetcher) {
        CacheEntry entry = cache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            LOGGER.debug("Cache hit for key: {}", key);
            return entry.guidelines;
        }
        
        LOGGER.info("Cache miss or expired for key: {}, fetching fresh data", key);
        
        try {
            List<AccessibilityGuideline> freshGuidelines = fetcher.get();
            cache.put(key, new CacheEntry(freshGuidelines, DEFAULT_TTL_HOURS));
            LOGGER.info("Cached {} guidelines for key: {}", freshGuidelines.size(), key);
            return freshGuidelines;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch fresh guidelines for key: " + key, e);
            
            // Return stale data if available rather than failing
            if (entry != null) {
                LOGGER.warn("Returning stale cached data for key: {}", key);
                return entry.guidelines;
            }
            
            throw new RuntimeException("Failed to fetch guidelines and no cached data available", e);
        }
    }
    
    /**
     * Clear all cache entries
     */
    public void clearAll() {
        int size = cache.size();
        cache.clear();
        LOGGER.info("Cleared {} cache entries", size);
    }
    
    /**
     * Clear only expired entries
     */
    public void clearExpired() {
        List<String> expiredKeys = new ArrayList<>();
        
        cache.forEach((key, entry) -> {
            if (entry.isExpired()) {
                expiredKeys.add(key);
            }
        });
        
        expiredKeys.forEach(cache::remove);
        LOGGER.info("Cleared {} expired cache entries", expiredKeys.size());
    }
}