package org.dvsa.testing.lib;

import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.lib.Util.AnswerBot;
import org.dvsa.testing.lib.Util.PlayWrightManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.*;

import static org.dvsa.testing.lib.Util.AXEScanner.scan;
import static org.dvsa.testing.lib.Util.AnswerBot.formAutoFill;


public class SpiderCrawler {
    private static final Logger LOGGER = LogManager.getLogger(SpiderCrawler.class);

    public static Document request(String url, ArrayList<String> visitedURL) {
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
        try {
            if (UrlValidator.isURLValid(url)) {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .ignoreHttpErrors(true)
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


    public static void crawler(int level, String url, ArrayList<String> visited) {
        if (level > 20 | visited.contains(url)) {
            return;
        }

        visited.add(url);

        Document doc = request(url, visited);
        if (doc != null) {
            LOGGER.info("Crawling: {}", url);

            for (Element link : doc.select("a[href]")) {
                String formattedLink = link.absUrl("href");
                if (!visited.contains(formattedLink) && formattedLink.contains("dvsacloud")) {
                    crawler(level + 1, formattedLink, visited);
                    var browser = new PlayWrightManager();
                    browser.selectBrowser("chrome");
                    Page page = browser.getPage();
                    page.navigate(formattedLink);
                    formAutoFill(page,formattedLink);
                    scan(page);
                    System.out.println("THE URL: " + formattedLink);
                }
            }
        }
    }
}