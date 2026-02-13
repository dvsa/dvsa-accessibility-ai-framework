package org.dvsa.testing.framework.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GovUkScraper {
    private static final Logger LOGGER = LogManager.getLogger(GovUkScraper.class);
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String getLiveGuidance(String path) {
        if (path == null || path.isEmpty()) return "";

        return cache.computeIfAbsent(path, p -> {
            try {
                String targetUrl = resolveGovUkUrl(p);

                LOGGER.info("Fetching live GOV.UK guidance from: {}", targetUrl);
                Document doc = Jsoup.connect(targetUrl)
                        .userAgent("Accessibility-Audit-Tool/1.0")
                        .timeout(10000)
                        .get();

                Element accessibilityHeading = doc.select("h2:contains(Accessibility)").first();

                StringBuilder content = new StringBuilder();
                if (accessibilityHeading != null) {
                    Element next = accessibilityHeading.nextElementSibling();
                    while (next != null && !next.tagName().equalsIgnoreCase("h2")) {
                        content.append(next.text()).append("\n");
                        next = next.nextElementSibling();
                    }
                } else {
                    Element mainContent = doc.select("main").first();
                    if (mainContent != null) {
                        content.append(mainContent.select("p, ul").text());
                    } else {
                        return "Guidance available at: " + targetUrl;
                    }
                }

                return content.toString().isEmpty() ? "Guidance available at: " + targetUrl : content.toString();

            } catch (org.jsoup.HttpStatusException e) {
                LOGGER.error("404 Error: Path '{}' not found on GOV.UK. URL tried: {}", p, e.getUrl());
                return "Guidance link broken for " + p;
            } catch (Exception e) {
                LOGGER.error("Failed to scrape GOV.UK for path: {}", p, e);
                return "Guidance unavailable for " + p;
            }
        });
    }

    private String resolveGovUkUrl(String path) {
        if (path == null || path.isBlank()) return "";

        String slug = path.toLowerCase().trim();

        List<String> styles = List.of(
                "headings", "typeface", "type-scale", "paragraphs",
                "links", "lists", "colour", "layout", "spacing", "images"
        );

        String category = styles.contains(slug) ? "styles" : "components";

        return String.format("https://design-system.service.gov.uk/%s/%s/", category, slug);
    }
}