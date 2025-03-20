package org.dvsa.testing.lib.Util;

import com.deque.html.axecore.playwright.AxeBuilder;

import com.deque.html.axecore.playwright.Reporter;
import com.deque.html.axecore.results.AxeResults;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AXEScanner {

    private static final Logger LOGGER = LogManager.getLogger(AXEScanner.class);

    public static void scan(Page page) {
        List<String> tags = Optional.ofNullable(System.getProperty("standards.scan"))
                .map(Collections::singletonList)
                .orElse(List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa"));

        AxeBuilder axeBuilder = new AxeBuilder(page).withTags(tags);

        try {
            AxeResults axeResults = axeBuilder.analyze();

            if (axeResults.getViolations().isEmpty()) {
                LOGGER.info("No accessibility issues found.");
            } else {
                LOGGER.info("Found {} {} ", axeResults.getViolations().size(), " accessibility issues.");
                LOGGER.info("Issue founds {} ", axeResults.getViolations());
            }

            new Reporter().JSONStringify(axeResults, "axe-results.json");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}