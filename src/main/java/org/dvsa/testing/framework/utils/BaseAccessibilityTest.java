package org.dvsa.testing.framework.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.axe.AXEScanner;
import org.dvsa.testing.framework.browser.DriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * The base class for all accessibility tests.
 * Handles driver lifecycle and final report generation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseAccessibilityTest {
    protected static final Logger LOGGER = LogManager.getLogger(BaseAccessibilityTest.class);

    protected static Object driver;

    @BeforeAll
    public void globalSetup() {
        AXEScanner.getAllViolations().clear();
        AXEScanner.getPageScreenshots().clear();

        String framework = System.getProperty("test.framework", "playwright");
        String browser = System.getProperty("browser", "chrome");

        LOGGER.info("Starting {} Accessibility Test Suite on {}...", framework, browser);

        driver = DriverManager.init(framework, browser);
    }

    @AfterAll
    public void globalTearDown() {
        try {
            LOGGER.info("All tests finished. Triggering AI Analysis and Report...");
            AXEScanner.generateFinalReport();
        } catch (Exception e) {
            LOGGER.error("Failed to generate report in Base Class: {}", e.getMessage());
        } finally {
            DriverManager.quit();
        }
    }
}