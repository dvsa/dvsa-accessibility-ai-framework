package org.dvsa.testing.lib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.*;


public class SpiderCrawler {
    private static final Logger LOGGER = LogManager.getLogger(SpiderCrawler.class);

    public static Document request(String url, ArrayList<String> visitedURL) {
        try {
            if (UrlValidator.isURLValid(url)) {
                Document doc = Jsoup.connect(url).get();
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
        if (level > 3 | visited.contains(url)) {
            return;
        }

        visited.add(url);

        Document doc = request(url, visited);
        if (doc != null) {
            for (Element link : doc.select("a[href]")) {
                String formattedLink = link.absUrl("href");
                if (!visited.contains(formattedLink))
                    crawler(level + 1, formattedLink, visited);
            }
        }
    }
}