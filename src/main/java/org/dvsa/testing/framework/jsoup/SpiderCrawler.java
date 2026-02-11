package org.dvsa.testing.framework.jsoup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.utils.DomainValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

import static org.dvsa.testing.framework.axe.AXEScanner.scan;


public class SpiderCrawler {

    private static final Logger LOGGER = LogManager.getLogger(SpiderCrawler.class);
    private static final int MAX_CRAWL_DEPTH = 10;
    private static final int MAX_URLS_PER_DOMAIN = 1000;

    private static final Pattern EXCLUDE_PATTERN = Pattern.compile(
            ".*\\.(pdf|zip|gz|tar|mp3|mp4|avi|png|jpg|jpeg|gif|css|js|csv|docx|xlsx|exe|dmg)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            ".*(logout|signout|delete|remove|cancel|auth|login).*",
            Pattern.CASE_INSENSITIVE
    );

  
    public static void crawler(int level, String url, Set<String> visited, Page page) {
        String baseDomain = DomainValidator.extractDomain(url);
        if (baseDomain == null) {
            LOGGER.error("Cannot extract domain from starting URL: {}", url);
            return;
        }

        crawlerWithDomain(level, url, visited, page, baseDomain, false);
    }

  
    public static void crawlerWithDomain(int level, String url, Set<String> visited, Page page, String baseDomain, boolean allowSubdomains) {
    if (level >= MAX_CRAWL_DEPTH || visited.size() >= MAX_URLS_PER_DOMAIN) {
        return;
    }

    String normalizedUrl = normaliseUrl(url);
    
    if (!DomainValidator.isSameDomain(normalizedUrl, baseDomain, allowSubdomains) || !visited.add(normalizedUrl)) {
        return;
    }

    page.context().onPage(newPage -> {
        try {
            newPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            String newTabUrl = newPage.url();
            if (!DomainValidator.isSameDomain(newTabUrl, baseDomain, allowSubdomains)) {
                LOGGER.warn("Closing unauthorized external popup: {}", newTabUrl);
                newPage.close();
            }
        } catch (Exception e) {
            LOGGER.error("Error handling new page event", e);    
        }
    });

    LOGGER.info("Crawling [Level {}]: {}", level, normalizedUrl);
    
    try {
        page.navigate(normalizedUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));

        String currentUrl = page.url();
        if (!DomainValidator.isSameDomain(currentUrl, baseDomain, allowSubdomains)) {
            LOGGER.debug("Redirected outside domain scope: {}", currentUrl);
            return;
        }

        page.evaluate("() => { document.querySelectorAll('a[target]').forEach(l => l.removeAttribute('target')); }");

        scan(page);

        Document doc = Jsoup.parse(page.content(), normalizedUrl);
        var links = doc.select("a[href]");
        
        for (Element link : links) {
            String nextUrl = link.absUrl("href");
            if (shouldFollow(nextUrl, baseDomain, visited, allowSubdomains)) {
                crawlerWithDomain(level + 1, nextUrl, visited, page, baseDomain, allowSubdomains);
            }
        }
        
    } catch (Exception e) {
         LOGGER.error("Failed to crawl URL [Level {}]: {}", level, normalizedUrl, e);
    }
}

    private static String normaliseUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return "";
        }
        
        try {
            URI uri = new URI(urlString.trim());
            String normalized = new URI(
                uri.getScheme(), 
                uri.getAuthority(), 
                uri.getPath() != null ? uri.getPath() : "/", 
                null, 
                null
            ).toString().toLowerCase();
            
            return normalized.endsWith("/") && normalized.length() > 1 
                ? normalized.substring(0, normalized.length() - 1) 
                : normalized;
                
        } catch (Exception e) {
            LOGGER.debug("Failed to normalize URL: {}", urlString, e);
            return urlString.toLowerCase().trim();
        }
    }

    private static boolean shouldFollow(String url, String baseDomain, Set<String> visited, boolean allowSubdomains) {
    if (url == null || url.trim().isEmpty()) return false;
    if (!url.startsWith("http")) return false; 

    if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) {
        return false;
    }

    String normalized = normaliseUrl(url);
    if (visited.contains(normalized)) {
        return false;
    }

    String pathOnly = normalized.split("\\?")[0];
    if (EXCLUDE_PATTERN.matcher(pathOnly).matches()) return false;
    if (ACTION_PATTERN.matcher(normalized).matches()) return false;

    return true;
}
}