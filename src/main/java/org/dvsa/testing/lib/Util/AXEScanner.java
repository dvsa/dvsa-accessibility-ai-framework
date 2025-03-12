package org.dvsa.testing.lib.Util;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.utilities.axeresults.AxeResults;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AXEScanner {

    private static final Logger LOGGER = LogManager.getLogger(AXEScanner.class);

    private final List<String> tags;

    public List<String> impact = new ArrayList<>();

    public AXEScanner() {
        tags = Optional.ofNullable(System.getProperty("standards.scan"))
                .map(Collections::singletonList)
                .orElse(List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa"));
    }

    public void scan(Page page) {
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