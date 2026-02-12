package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.playwright.AxeBuilder;

import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.ai.BedrockAgentAnalyser;
import org.dvsa.testing.framework.ai.BedrockRecommendation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.dvsa.testing.framework.axe.HtmlReportGenerator.generateHtmlReport;

public class AXEScanner {

    private static final Logger LOGGER = LogManager.getLogger(AXEScanner.class);
    private static final ConcurrentHashMap<Rule, String> allViolations = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> pageScreenshots = new ConcurrentHashMap<>(); // URL -> screenshot path

    public AXEScanner() throws Exception {
    }

    public static void scan(Page page) {
        List<String> tags = Optional.ofNullable(System.getProperty("standards.scan"))
                .map(Collections::singletonList)
                .orElse(List.of("wcag22a", "wcag22aa", "wcag412", "wcag143", "cat.**", "best-practice"));

        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(500); // 500ms "settle" time to allow JS context to stabilize

            String currentUrl = page.url();

            if (currentUrl == null || currentUrl.startsWith("about:") || currentUrl.isEmpty()) {
                LOGGER.warn("Skipping scan: Invalid page state ({})", currentUrl);
                return;
            }

            AxeResults axeResponse;
            int retries = 3;
            while (true) {
                try {
                    axeResponse = new AxeBuilder(page)
                            .withTags(tags)
                            .analyze();
                    break;
                } catch (RuntimeException e) {
                    if (e.getMessage().contains("unable to inject") && --retries > 0) {
                        LOGGER.warn("Axe injection failed. Retrying in 1s... (Attempts left: {})", retries);
                        page.waitForTimeout(1000);
                    } else {
                        throw e;
                    }
                }
            }

            if (axeResponse != null && !axeResponse.getViolations().isEmpty()) {
                if (!pageScreenshots.containsKey(currentUrl)) {
                    String filename = captureScreenshot(page, currentUrl);
                    if (filename != null) {
                        pageScreenshots.put(currentUrl, filename);
                        LOGGER.info("Saved page screenshot for URL: {}", currentUrl);
                    }
                }

                for (Rule rule : axeResponse.getViolations()) {
                    allViolations.put(rule, currentUrl);
                }
            } else {
                LOGGER.info("No violations found on {}", currentUrl);
            }

        } catch (Exception e) {
            LOGGER.error("Accessibility scan failed on {}: {}", page.url(), e.getMessage());
        }
    }

    public static ConcurrentHashMap<Rule, String> getAllViolations() {
        return allViolations;
    }

    public static ConcurrentHashMap<String, String> getPageScreenshots() {
        return pageScreenshots;
    }


    private static String captureScreenshot(Page page, String url) {
        try {
            String screenshotDir = "target/reports/screenshots/";
            Path dirPath = Paths.get(screenshotDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                LOGGER.info("Created screenshot directory: {}", screenshotDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
            String sanitizedUrl = url.replaceAll("[^a-zA-Z0-9.-]", "_")
                    .replaceAll("_{2,}", "_")
                    .substring(0, Math.min(50, url.length()));
            String filename = timestamp + "_" + sanitizedUrl + ".png";
            String fullPath = screenshotDir + filename;

            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(fullPath))
                    .setFullPage(true));

            return filename;

        } catch (Exception e) {
            LOGGER.error("Failed to capture screenshot for {}: {}", url, e.getMessage());
            return null;
        }
    }

    public static void generateFinalReport() {
        if (allViolations.isEmpty()) {
            LOGGER.info("No accessibility issues found; skipping report generation.");
            return;
        }

        try {
            LOGGER.info("Generating report with Bedrock recommendations & Live Scraped Context...");

            BedrockAgentAnalyser analyser = new BedrockAgentAnalyser();

            Map<String, BedrockRecommendation> recommendationMap = analyser.analyseViolations(allViolations);

            if (recommendationMap == null || recommendationMap.isEmpty()) {
                LOGGER.warn("No AI recommendations received from Bedrock. Falling back to basic report.");
                generateBasicReport();
                return;
            }

            LOGGER.info("DEBUG: Keys in Bedrock Map: {}", recommendationMap.keySet());
            LOGGER.info("DEBUG: Keys in Violations Map: {}", allViolations.keySet().stream().map(Rule::getId).toList());

            String htmlContent = HtmlReportGenerator.generateHtmlReport(
                    allViolations,
                    recommendationMap,
                    new HashMap<>(),
                    pageScreenshots
            );

            bufferedFileWriter(htmlContent);

            LOGGER.info("Accessibility report generated successfully with live scraped AI guidance.");

        } catch (Exception e) {
            LOGGER.error("Failed to generate AI report: {}", e.getMessage(), e);
            generateBasicReport();
        }
    }

    static void generateBasicReport() {
        try {
            Map<String, BedrockRecommendation> emptyRecommendations = new HashMap<>();
            Map<String, BedrockRecommendation> emptyKbMap = new HashMap<>();

            String htmlContent = generateHtmlReport(getAllViolations(), emptyRecommendations, emptyKbMap, getPageScreenshots());
            bufferedFileWriter(htmlContent);

            LOGGER.info("Basic accessibility report generated successfully (without AI recommendations).");
        } catch (Exception e) {
            LOGGER.error("Failed to generate basic report", e);
        }
    }

    static void bufferedFileWriter(String content) {
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss-"));
        String fileName = "accessibility.html";
        String folderName = "target/reports/";

        File dir = new File(folderName);
        if (!dir.exists() && dir.mkdirs()) {
            LOGGER.info("Created directory: {}", "target/reports");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(folderName + dateTime + fileName, true))) {
            writer.write(content);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }
}