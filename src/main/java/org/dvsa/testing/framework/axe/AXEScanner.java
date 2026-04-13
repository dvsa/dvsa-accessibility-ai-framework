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
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.dvsa.testing.framework.axe.HtmlReportGenerator.generateHtmlReport;

public class AXEScanner {

    private static final Logger LOGGER = LogManager.getLogger(AXEScanner.class);
    private static final CopyOnWriteArrayList<ViolationEntry> allViolations = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, String> pageScreenshots = new ConcurrentHashMap<>();

    public record ViolationEntry(Rule rule, String pageUrl) {}

    public AXEScanner() {
    }

    public static void scan(Object driver) {
        if (driver instanceof Page) {
            scanPlaywright((Page) driver);
        } else if (driver instanceof WebDriver) {
            scanSelenium((WebDriver) driver);
        } else {
            throw new IllegalArgumentException("Unsupported driver type: " + driver.getClass().getName());
        }
    }

    private static void scanPlaywright(Page page) {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(500);

            String currentUrl = page.url();
            if (isInvalidUrl(currentUrl)) return;

            AxeResults axeResponse;
            int retries = 3;
            while (true) {
                try {
                    axeResponse = new AxeBuilder(page).withTags(getScanTags()).analyze();
                    break;
                } catch (RuntimeException e) {
                    if (e.getMessage().contains("unable to inject") && --retries > 0) {
                        page.waitForTimeout(1000);
                    } else throw e;
                }
            }
            processResults(currentUrl, axeResponse.getViolations(), page);
        } catch (Exception e) {
            LOGGER.error("Playwright scan failed: {}", e.getMessage());
        }
    }

    private static void scanSelenium(WebDriver driver) {
        try {
            String currentUrl = driver.getCurrentUrl();
            if (isInvalidUrl(currentUrl)) return;

            com.deque.html.axecore.results.Results results = new com.deque.html.axecore.selenium.AxeBuilder()
                    .withTags(getScanTags())
                    .analyze(driver);

            processResults(currentUrl, results.getViolations(), driver);
        } catch (Exception e) {
            LOGGER.error("Selenium scan failed: {}", e.getMessage());
        }
    }

    private static String captureScreenshot(Object driver, String url) {
        try {
            String screenshotDir = "target/reports/screenshots/";
            Files.createDirectories(Paths.get(screenshotDir));

            String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + "_" + url.replaceAll("[^a-zA-Z0-9]", "_").substring(0, Math.min(30, url.length())) + ".png";
            Path path = Paths.get(screenshotDir + filename);

            if (driver instanceof Page) {
                ((Page) driver).screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
            } else if (driver instanceof WebDriver) {
                File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(src.toPath(), path, StandardCopyOption.REPLACE_EXISTING);
            }
            return filename;
        } catch (Exception e) {
            LOGGER.error("Screenshot failed: {}", e.getMessage());
            return null;
        }
    }

    private static List<String> getScanTags() {
        return Optional.ofNullable(System.getProperty("standards.scan"))
                .map(Collections::singletonList)
                .orElse(List.of("wcag22a", "wcag22aa", "wcag412", "wcag143", "cat.**", "best-practice"));
    }

    private static boolean isInvalidUrl(String url) {
        return url == null || url.startsWith("about:") || url.isEmpty();
    }

    public static CopyOnWriteArrayList<ViolationEntry> getAllViolations() {
        return allViolations;
    }

    public static ConcurrentHashMap<String, String> getPageScreenshots() {
        return pageScreenshots;
    }


    public static void generateFinalReport() {
        if (allViolations.isEmpty()) {
            LOGGER.info("No accessibility issues found; skipping report generation.");
            return;
        }

        List<ViolationEntry> snapshot = new ArrayList<>(allViolations);
        LOGGER.info("Generating report for {} total violation instances across {} pages...",
                snapshot.size(),
                snapshot.stream().map(ViolationEntry::pageUrl).distinct().count());

        Map<String, Rule> uniqueRules = snapshot.stream()
                .collect(Collectors.toMap(
                        e -> e.rule().getId(),
                        ViolationEntry::rule,
                        (existing, replacement) -> existing
                ));

        LOGGER.info("Deduplicated to {} unique rule IDs for Bedrock analysis (from {} total violations)",
                uniqueRules.size(), snapshot.size());

        try {
            LOGGER.info("Generating report with Bedrock recommendations & Live Scraped Context...");

            BedrockAgentAnalyser analyser = new BedrockAgentAnalyser();

            Map<String, BedrockRecommendation> recommendationMap = analyser.analyseUniqueViolations(uniqueRules);

            if (recommendationMap == null || recommendationMap.isEmpty()) {
                LOGGER.warn("No AI recommendations received from Bedrock. Falling back to basic report.");
                generateBasicReport(snapshot);
                return;
            }

            LOGGER.info("Bedrock recommendations received for rules: {}", recommendationMap.keySet());

            String htmlContent = HtmlReportGenerator.generateHtmlReport(
                    snapshot,
                    recommendationMap,
                    new HashMap<>(),
                    pageScreenshots
            );

            bufferedFileWriter(htmlContent);

            LOGGER.info("Accessibility report generated successfully with live scraped AI guidance.");

        } catch (Exception e) {
            LOGGER.error("Failed to generate AI report: {}", e.getMessage(), e);
            generateBasicReport(snapshot);
        }
    }

    static void generateBasicReport(List<ViolationEntry> snapshot) {
        try {
            Map<String, BedrockRecommendation> emptyRecommendations = new HashMap<>();
            Map<String, BedrockRecommendation> emptyKbMap = new HashMap<>();

            String htmlContent = generateHtmlReport(snapshot, emptyRecommendations, emptyKbMap, getPageScreenshots());
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

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(folderName + dateTime + fileName, false))) {
            writer.write(content);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }


    private static void processResults(String url, List<Rule> violations, Object driver) {
        if (!violations.isEmpty()) {
            pageScreenshots.computeIfAbsent(url, k -> captureScreenshot(driver, url));

            for (Rule rule : violations) {
                allViolations.add(new ViolationEntry(rule, url));
            }

            LOGGER.info("Found {} violations on {}", violations.size(), url);
        } else {
            LOGGER.info("No violations found on {}", url);
        }
    }
}