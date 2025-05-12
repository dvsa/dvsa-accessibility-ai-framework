package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.playwright.AxeBuilder;

import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.dvsa.testing.framework.axe.HtmlReportGenerator.generateHtmlReport;

public class AXEScanner {

    private static final Logger LOGGER = LogManager.getLogger(AXEScanner.class);
    private static final ConcurrentHashMap<Rule, String> allViolations = new ConcurrentHashMap<>();

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
            for (Rule rule : axeResponse.getViolations()) {
                if (!allViolations.containsKey(rule)) {
                    allViolations.put(rule, page.url());
                }
            }
        }
    }

    public static void generateFinalReport() {
        if (!allViolations.isEmpty()) {
            bufferedFileWriter(generateHtmlReport(allViolations));
        }
    }

    public static void bufferedFileWriter(String content) {
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss-"));
        String fileName = "accessibility.html";
        String folderName = "Reports/";

        File dir = new File(folderName);
        if (!dir.exists() && dir.mkdir()) {
            LOGGER.info("Created directory: {}", "Reports");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(folderName + dateTime + fileName, true))) {
            writer.write(content);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }
}