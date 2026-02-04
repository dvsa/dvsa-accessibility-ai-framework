package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.playwright.AxeBuilder;

import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.ai.BedrockAgentAnalyser;
import org.dvsa.testing.framework.ai.BedrockRecommendation;

import java.io.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
            
            // Capture screenshot if violations are found and not already captured for this URL
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

    BedrockAgentAnalyser analyser = new BedrockAgentAnalyser(client);


    public void generateFinalReport() {
        if (allViolations.isEmpty()) {
            LOGGER.info("No accessibility issues found; skipping report generation.");
            return;
        }

        try {
            LOGGER.info("Generating report with Bedrock recommendations...");

            BedrockAgentAnalyser analyser = new BedrockAgentAnalyser(client);

            Map<String, BedrockRecommendation> kbMap = getKnowledgeBaseMap();

            List<BedrockRecommendation> recommendationsList =
                    analyser.analyseViolationsWithBedrock(getAllViolations(), kbMap);

            if (recommendationsList == null || recommendationsList.isEmpty()) {
                LOGGER.warn("No AI recommendations received from Bedrock.");
                return;
            }

            recommendationsList = recommendationsList.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (recommendationsList.isEmpty()) {
                LOGGER.warn("All Bedrock recommendations were null; skipping report generation.");
                return;
            }

            Map<String, BedrockRecommendation> recommendationMap = recommendationsList.stream()
                    .filter(r -> r.ruleId() != null && !r.ruleId().isBlank())
                    .collect(Collectors.toMap(
                            BedrockRecommendation::ruleId,
                            r -> r,
                            (r1, r2) -> r1 // keep the first in case of duplicates
                    ));

            if (recommendationMap.isEmpty()) {
                LOGGER.warn("No valid AI recommendations with ruleId found; skipping report generation.");
                return;
            }

            String htmlContent = generateHtmlReport(getAllViolations(), recommendationMap, kbMap, getPageScreenshots());
            bufferedFileWriter(htmlContent);

            LOGGER.info("Accessibility report generated successfully with AI recommendations.");

        } catch (JsonProcessingException e) {
            LOGGER.error(" Failed to parse Bedrock recommendations JSON", e);
            LOGGER.debug("Raw JSON received: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error while generating report", e);
        }
    }


    private Map<String, BedrockRecommendation> getKnowledgeBaseMap() {
        Map<String, BedrockRecommendation> kbMap = new HashMap<>();

        try {
            InputStream is = getClass().getResourceAsStream("/axe-kb.json");
            if (is == null) {
                LOGGER.warn("Knowledge base JSON not found; returning empty map.");
                return kbMap;
            }

            String kbJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(kbJson);

            if (!root.isArray()) {
                LOGGER.warn("Knowledge base JSON root is not an array; returning empty map.");
                return kbMap;
            }

            for (JsonNode node : root) {
                try {
                    BedrockRecommendation kbRec = BedrockRecommendation.builder()
                        .ruleId(node.has("ruleId") ? node.get("ruleId").asText() : null)
                        .issue(node.has("title") ? node.get("title").asText() : "")
                        .recommendation(node.has("recommendedFix") ? node.get("recommendedFix").asText() : "")
                        .reference(node.has("govUkReference") ? node.get("govUkReference").asText() : "")
                        .example(node.has("exampleFix") ? node.get("exampleFix").asText() : "")
                        .build();
                    kbMap.put(kbRec.ruleId(), kbRec);
                    String normalizedId = normalizeRuleId(kbRec.ruleId());
                    if (!normalizedId.equals(kbRec.ruleId())) {
                        BedrockRecommendation normalizedRec = BedrockRecommendation.builder()
                            .ruleId(normalizedId)
                            .issue(kbRec.issue())
                            .recommendation(kbRec.recommendation())
                            .reference(kbRec.reference())
                            .example(kbRec.example())
                            .build();
                        kbMap.put(normalizedId, normalizedRec);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse KB entry: {}", node.toString(), e);
                }
            }

            LOGGER.info("Loaded {} rules from knowledge base", kbMap.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load knowledge base JSON", e);
        }

        return kbMap;
    }


    private String normalizeRuleId(String ruleId) {
        return switch (ruleId) {
            case "image-alt" -> "AXE_IMAGE_ALT_NOT_REPEATED";
            case "heading-order" -> "AXE_HEADING_ORDER";
            case "banner" -> "AXE_ONE_BANNER_LANDMARK";
            case "region" -> "AXE_CONTENT_WITHIN_LANDMARKS";
            case "heading-disorder" -> "AXE_HEADING_DISCERNIBLE_TEXT";
            case "main-role" -> "AXE_MAIN_LANDMARK";
            case "label" -> "AXE_FORM_LABELS";
            default -> ruleId; // fallback: use KB ID as-is
        };
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