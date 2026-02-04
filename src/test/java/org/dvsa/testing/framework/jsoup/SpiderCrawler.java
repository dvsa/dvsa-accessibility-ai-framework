package org.dvsa.testing.framework.jsoup;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.dvsa.testing.framework.config.AppConfig;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.dvsa.testing.framework.PageCrawlerAnswerBotTest.getCookies;
import static org.dvsa.testing.framework.axe.AXEScanner.scan;
import static org.dvsa.testing.framework.browser.PlayWrightWaits.waitAndEnterText;


public class SpiderCrawler {

    private static final Logger LOGGER = LogManager.getLogger(SpiderCrawler.class);

    public static Document request(String url, ArrayList<String> visitedURL) {
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
        try {
            if (UrlValidator.isURLValid(url)) {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .ignoreHttpErrors(true)
                        .cookies(getCookies())
                        .timeout(500000)
                        .get();

                int statusCode = doc.connection().response().statusCode();

                if (statusCode == 200) {
                    LOGGER.info("Title: {}", doc.title());
                    LOGGER.info("Link: {}", url);

                    visitedURL.add(url);
                    return doc;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }


    public static void crawler(int level, String url, ArrayList<String> visited, Page page) {
        if (level >= 10 || visited.contains(url)) {
            return;
        }

        visited.add(url);

        Document doc = request(url, visited);
        if (doc != null) {
            LOGGER.info("Crawling: {}", url);

            // Navigate to the page first for AnswerBot interaction
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
            Page tab1 = page.context().pages().get(0);
            tab1.bringToFront();
            
            // Use AnswerBot to fill forms and click buttons on the current page
            try {
                // Instead of duplicating logic, reference SpiderCrawler.handleRadioButton and SpiderCrawler.handleTextInput where needed in other classes.
                // Scan the page after AnswerBot interaction
                scan(page);
                
                // Small delay to ensure page is ready after interactions
                page.waitForTimeout(1000);
                
            } catch (Exception e) {
                LOGGER.warn("AnswerBot interaction failed on {}: {}", url, e.getMessage());
            }

            for (Element link : doc.select("a[href]")) {
                String formattedLink = link.absUrl("href");
                if (!visited.contains(formattedLink) && formattedLink.contains(AppConfig.getString("domain"))
                        && !formattedLink.contains("logout") && !formattedLink.contains("csv") && !formattedLink.contains("download"))
                {
                    crawler(level + 1, formattedLink, visited, page);
                }
            }
        }
    }

    /**
     * Get random item from list (adapted from AnswerBot)
     */
    private static String getRandomFromList(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}