package org.dvsa.testing.framework.jsoup;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import org.dvsa.testing.framework.config.AppConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.dvsa.testing.framework.PageCrawlerAnswerBotTest.getCookies;
import static org.dvsa.testing.framework.axe.AXEScanner.scan;


public class SpiderCrawler {

    private static final Logger LOGGER = LogManager.getLogger(SpiderCrawler.class);

    private static String extractDomain(String baseUrl) {
        try {
            URL url = new URL(baseUrl);
            return url.getHost();
        } catch (MalformedURLException e) {
            LOGGER.warn("Invalid base URL: {}", baseUrl);
            return null;
        }
    }

    public static void crawler(int level, String url, ArrayList<String> visited, Page page) {
        String baseDomain = extractDomain(url);
        if (baseDomain == null) {
            LOGGER.error("Cannot extract domain from starting URL: {}", url);
            return;
        }
        
        LOGGER.info("Starting crawler with base domain: {}", baseDomain);
        Set<String> visitedSet = new HashSet<>(visited);
        crawlerWithDomain(level, url, visitedSet, page, baseDomain);
        visited.addAll(visitedSet);
    }

    public static void crawlerWithDomain(int level, String url, Set<String> visited, Page page, String baseDomain) {
    // 1. Unified Guard Clause
    if (level >= 10 || !visited.add(url) || !isSameDomain(url, baseDomain)) {
        return;
    }

    LOGGER.info("Crawling [Level {}]: {}", level, url);

    try {
        // 2. Navigate with Playwright (single fetch approach)
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
        scan(page);
        page.waitForTimeout(1000); 
        
        // 3. Get page content and parse with Jsoup (no double-fetching)
        String htmlContent = page.content();
        Document doc = Jsoup.parse(htmlContent, url);
        
        LOGGER.info("Title: {}", doc.title());
        LOGGER.info("Link: {}", url);
        
        // 4. Streamlined Link Extraction
        for (Element link : doc.select("a[href]")) {
            String nextUrl = link.absUrl("href");
            
            if (shouldFollow(nextUrl, baseDomain, visited)) {
                crawlerWithDomain(level + 1, nextUrl, visited, page, baseDomain);
            }
        }
        
    } catch (Exception e) {
        LOGGER.warn("Navigation or interaction failed on {}: {}", url, e.getMessage());
    }
}

private static boolean shouldFollow(String url, String baseDomain, Set<String> visited) {
    return !visited.contains(url) 
        && isSameDomain(url, baseDomain)
        && !url.matches(".*(logout|csv|download|\\.pdf|\\.zip).*"); // Use regex for cleaner filtering
}

private static boolean isSameDomain(String url, String baseDomain) {
    String domain = extractDomain(url);
    return domain != null && domain.equals(baseDomain);
}   
}