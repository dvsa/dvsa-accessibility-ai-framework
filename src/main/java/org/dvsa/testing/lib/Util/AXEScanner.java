package org.dvsa.testing.lib.Util;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.utilities.axeresults.AxeResults;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AXEScanner {

    private static final Logger LOGGER = LogManager.getLogger(AXEScanner.class);

    public static void scan(Page page) {
        List<String> tags = Optional.ofNullable(System.getProperty("standards.scan"))
                .map(Collections::singletonList)
                .orElse(List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa"));

        AxeResults axeResponse = new AxeBuilder(page)
                .withTags(tags)
                .analyze();

        if (axeResponse.getViolations().isEmpty()) {
            LOGGER.info("No accessibility issues found.");
        } else {
            LOGGER.info("Found {} {} ", axeResponse.getViolations().size(), " accessibility issues.");
            LOGGER.info("Issue founds {} ", axeResponse.getViolations());
        }
    }
}