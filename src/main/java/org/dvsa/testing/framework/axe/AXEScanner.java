package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.playwright.AxeBuilder;

import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.ai.BedrockAgentAnalyser;
import org.dvsa.testing.framework.mcp.integration.McpEnhancedBedrockAnalyser;
import org.dvsa.testing.framework.ai.BedrockRecommendation;

import java.io.*;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.dvsa.testing.framework.axe.HtmlReportGenerator.generateHtmlReport;

public class AXEScanner {

    private static final Logger LOGGER = LogManager.getLogger(AXEScanner.class);
    private static final ConcurrentHashMap<Rule, String> allViolations = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> pageScreenshots = new ConcurrentHashMap<>(); // URL -> screenshot path
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AXEScanner() throws Exception {
    }

    public static void scan(Page page) {
        List<String> tags = Optional.ofNullable(System.getProperty("standards.scan"))
                .map(Collections::singletonList)
                .orElse(List.of("wcag22a", "wcag22aa", "wcag412", "wcag143", "cat.**", "best-practice"));

        AxeResults axeResponse = new AxeBuilder(page)
                .withTags(tags)
                .analyze();

        if (axeResponse.getViolations().isEmpty()) {
            LOGGER.info("No accessibility issues found.");
        } else {
            LOGGER.info("Found {} {} ", axeResponse.getViolations().size(), " accessibility issues.");
            LOGGER.info("Issue founds {} ", axeResponse.getViolations());
            
            String currentUrl = page.url();
            if (!pageScreenshots.containsKey(currentUrl)) {
                String screenshotPath = captureScreenshot(page, currentUrl);
                if (screenshotPath != null) {
                    pageScreenshots.put(currentUrl, screenshotPath);
                    LOGGER.info("Screenshot captured for {}: {}", currentUrl, screenshotPath);
                }
            }
            
            for (Rule rule : axeResponse.getViolations()) {
                    allViolations.putIfAbsent(rule, page.url());
            }
        }
    }

    public static ConcurrentHashMap<Rule, String> getAllViolations() {
        return allViolations;
    }
    
    public static ConcurrentHashMap<String, String> getPageScreenshots() {
        return pageScreenshots;
    }
    
    /**
     * Capture a full page screenshot for accessibility violations
     */
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

    public void generateFinalReport() {
        if (allViolations.isEmpty()) {
            LOGGER.info("No accessibility issues found; skipping report generation.");
            return;
        }

        try {
            LOGGER.info("Generating report with Bedrock recommendations...");

            BedrockAgentAnalyser analyser = new McpEnhancedBedrockAnalyser(client);

            // Knowledge base is stored in S3 and accessed by Bedrock agent
            Map<String, BedrockRecommendation> emptyKbMap = new HashMap<>();

            List<BedrockRecommendation> recommendationsList =
                    analyser.analyseViolationsWithBedrock(getAllViolations(), emptyKbMap);

            if (recommendationsList == null || recommendationsList.isEmpty()) {
                LOGGER.warn("No AI recommendations received from Bedrock. Generating basic report...");
                generateBasicReport();
                return;
            }

            recommendationsList = recommendationsList.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (recommendationsList.isEmpty()) {
                LOGGER.warn("All Bedrock recommendations were null. Generating basic report...");
                generateBasicReport();
                return;
            }

            Map<String, BedrockRecommendation> recommendationMap = recommendationsList.stream()
                    .filter(r -> r.ruleId() != null && !r.ruleId().isBlank())
                    .collect(Collectors.toMap(
                            BedrockRecommendation::ruleId,
                            r -> r,
                            (r1, r2) -> r1 
                    ));

            if (recommendationMap.isEmpty()) {
                LOGGER.warn("No valid AI recommendations with ruleId found. Generating basic report...");
                generateBasicReport();
                return;
            }

            String htmlContent = generateHtmlReport(getAllViolations(), recommendationMap, emptyKbMap, getPageScreenshots());
            bufferedFileWriter(htmlContent);

            LOGGER.info("Accessibility report generated successfully with AI recommendations.");

        } catch (JsonProcessingException e) {
            LOGGER.error(" Failed to parse Bedrock recommendations JSON", e);
            LOGGER.debug("Raw JSON received: {}", e.getMessage());
            LOGGER.info("Generating basic report due to Bedrock JSON parsing error...");
            generateBasicReport();
        } catch (Exception e) {
            LOGGER.error("Unexpected error while generating report", e);
            LOGGER.info("Generating basic report due to Bedrock error...");
            generateBasicReport();
        }
    }

   
    private void generateBasicReport() {
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

    public void bufferedFileWriter(String content) {
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