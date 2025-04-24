package org.dvsa.testing.framework.jsoup;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.typesafe.config.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.*;

import static org.dvsa.testing.framework.PageCrawlerAnswerBotTest.getCookies;
import static org.dvsa.testing.framework.axe.AXEScanner.scan;


public class SpiderCrawler {
    private static final Config config = ConfigFactory.defaultApplication();
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

        if (level > 2 | visited.contains(url)) {
            return;
        }

        visited.add(url);

        Document doc = request(url, visited);
        if (doc != null) {
            LOGGER.info("Crawling: {}", url);

            for (Element link : doc.select("a[href]")) {
                String formattedLink = link.absUrl("href");
                if (!visited.contains(formattedLink) && formattedLink.contains(config.getString("domain")) && (!formattedLink.contains("logout"))) {
                    crawler(level + 1, formattedLink, visited, page);
                    page.navigate(formattedLink, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
                    Page tab1 = page.context().pages().get(0);
                    tab1.bringToFront();
                    scan(page);
                }
            }
        }
    }
}