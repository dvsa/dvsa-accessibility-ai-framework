package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import org.dvsa.testing.framework.ai.BedrockRecommendation;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class HtmlReportGenerator {

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private static String truncateText(String text) {
        if (text == null) return "";
        if (text.length() <= 150) return text;
        return text.substring(0, 150) + "...";
    }

    public static String generateHtmlReport(
            ConcurrentHashMap<Rule, String> rules,
            Map<String, BedrockRecommendation> bedrockRecommendationMap,
            Map<String, BedrockRecommendation> kbMap,
            Map<String, String> pageScreenshots
    ) {
        StringBuilder htmlReport = new StringBuilder();
        Set<String> seenRulesIds = new HashSet<>();

        htmlReport.append("<!DOCTYPE html>\n")
                .append("<html><head><meta charset='UTF-8'><title>Accessibility Report</title>")
                .append("<style>")
                .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; line-height: 1.5; color: #333; }")
                .append("h1 { color: #0b0c0c; }")
                .append("table { width: 100%; border-collapse: collapse; margin-bottom: 40px; table-layout: fixed; }")
                .append("th, td { border: 1px solid #bfc1c3; padding: 12px; text-align: left; vertical-align: top; word-wrap: break-word; }")
                .append("th { background-color: #f3f2f1; font-weight: bold; }")
                .append(".screenshot-img { max-width: 100%; height: auto; cursor: pointer; border: 1px solid #ddd; max-height: 150px; transition: 0.3s; }")
                .append(".screenshot-img:hover { opacity: 0.7; }")
                .append(".impact-badge { padding: 4px 8px; font-weight: bold; color: white; border-radius: 3px; text-transform: uppercase; font-size: 11px; }")
                .append(".ai-fix-box { background-color: #f8f8f8; border-left: 4px solid #005ea5; padding: 10px; margin-top: 10px; }")
                .append("code { font-family: 'Courier New', monospace; background: #eef; padding: 2px 4px; display: block; white-space: pre-wrap; margin-top: 5px; border: 1px solid #ccc;}")
                .append(".download-btn { display: inline-block; background-color: #00823b; color: white; padding: 8px 12px; text-decoration: none; border-radius: 3px; font-size: 12px; margin-top: 10px; font-weight: bold; }")
                .append(".download-btn:hover { background-color: #005a27; }")
                .append(".modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.9); overflow: auto; }")
                .append(".modal-content { margin: auto; display: block; width: 80%; max-width: 1200px; padding: 40px 0; }")
                .append("</style>")
                .append("<script>")
                .append("function openModal(src) { document.getElementById('screenshotModal').style.display='block'; document.getElementById('modalImage').src=src; }")
                .append("window.onclick = function(event) { if (event.target == document.getElementById('screenshotModal')) { document.getElementById('screenshotModal').style.display = 'none'; } }")
                .append("</script>")
                .append("</head><body>")
                .append("<h1>Accessibility Report (AI Augmented)</h1>")
                .append("<table><tr>")
                .append("<th style='width: 12%;'>Source URL</th>")
                .append("<th style='width: 18%;'>Screenshot</th>")
                .append("<th style='width: 10%;'>Rule ID</th>")
                .append("<th style='width: 15%;'>Failing HTML</th>")
                .append("<th style='width: 8%;'>Impact</th>")
                .append("<th style='width: 37%;'>AI Audit & Fix (GOV.UK Scraped)</th>")
                .append("</tr>");

        Map<String, String> impactColors = Map.of(
                "critical", "#d4351c",
                "serious", "#f47738",
                "minor", "#1d70b8",
                "moderate", "#4c2c92"
        );

        for (Map.Entry<Rule, String> ruleEntry : rules.entrySet()) {
            Rule rule = ruleEntry.getKey();
            String pageUrl = ruleEntry.getValue();

            if (!seenRulesIds.add(rule.getId())) continue;

            String impactColor = impactColors.getOrDefault(rule.getImpact(), "#505a5f");

            BedrockRecommendation br = bedrockRecommendationMap.get(rule.getId());
            if (br == null) br = kbMap.get(rule.getId());

            if (br == null || br.example() == null || br.example().isEmpty()) {
                String fallbackExample = getManualGdsFallback(rule.getId());
                br = BedrockRecommendation.builder()
                        .ruleId(rule.getId())
                        .issue(rule.getDescription())
                        .recommendation(br != null ? br.recommendation() : "Follow GOV.UK Design System patterns.")
                        .example(fallbackExample)
                        .reference("https://design-system.service.gov.uk/")
                        .build();
            }

            for (CheckedNode node : rule.getNodes()) {
                // --- SCREENSHOT LOOKUP (Option 1: By URL) ---
                String rawFilename = pageScreenshots.get(pageUrl);

                // Smart fallback for URL key matching
                if (rawFilename == null && pageUrl != null) {
                    rawFilename = pageScreenshots.entrySet().stream()
                            .filter(e -> e.getKey().replaceAll("/$", "").equalsIgnoreCase(pageUrl.replaceAll("/$", "")))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse(null);
                }

                String screenshotHtml = getScreenshotHtml(rawFilename, rule.getId());

                htmlReport.append("<tr>")
                        .append("<td><a href='").append(pageUrl).append("' target='_blank' style='font-size: 11px;'>").append(pageUrl).append("</a></td>")
                        .append("<td>").append(screenshotHtml).append("</td>")
                        .append("<td><strong>").append(rule.getId()).append("</strong></td>")
                        .append("<td><code>").append(escapeHtml(truncateText(node.getHtml()))).append("</code></td>")
                        .append("<td><span class='impact-badge' style='background-color:").append(impactColor).append(";'>")
                        .append(rule.getImpact()).append("</span></td>")
                        .append("<td>")
                        .append("<strong>Issue:</strong> ").append(escapeHtml(br.issue())).append("<br><br>")
                        .append("<strong>Guidance:</strong> ").append(escapeHtml(br.recommendation())).append("<br>");

                if (br.example() != null && !br.example().isEmpty()) {
                    String encodedFix = URLEncoder.encode(br.example(), StandardCharsets.UTF_8).replace("+", "%20");
                    htmlReport.append("<div class='ai-fix-box'>")
                            .append("<strong>Suggested Fix:</strong>")
                            .append("<code>").append(escapeHtml(br.example())).append("</code>")
                            .append("<a href='data:text/html;charset=utf-8,").append(encodedFix)
                            .append("' download='").append(rule.getId()).append("-fix.html' class='download-btn'>")
                            .append("Download Fix Snippet</a>")
                            .append("</div>");
                }

                if (br.reference() != null && !br.reference().isEmpty()) {
                    htmlReport.append("<br><small><strong>Source:</strong> <a href='").append(br.reference()).append("' target='_blank'>GOV.UK Docs</a></small>");
                }
                htmlReport.append("</td></tr>");
            }
        }

        htmlReport.append("</table>")
                .append("<div id='screenshotModal' class='modal' onclick='this.style.display=\"none\"'>")
                .append("<img class='modal-content' id='modalImage'>")
                .append("</div>")
                .append("</body></html>");

        return htmlReport.toString();
    }

    private static String getManualGdsFallback(String ruleId) {
        return switch (ruleId) {
            case "page-has-heading-one" -> "<h1 class='govuk-heading-xl'>Page Title</h1>";
            case "landmark-one-main", "landmark-unique" -> "<main class='govuk-main-wrapper' id='main-content' role='main'>\n  \n</main>";
            case "label" -> "<div class='govuk-form-group'>\n  <label class='govuk-label' for='input-id'>Label</label>\n  <input class='govuk-input' id='input-id' type='text'>\n</div>";
            case "color-contrast" -> "<button class='govuk-button'>Save and continue</button>";
            default -> "";
        };
    }

    private static String getScreenshotHtml(String filename, String ruleId) {
        if (filename == null || filename.isEmpty()) {
            return "<em style='color: #6f777b; font-size: 11px;'>No screenshot for " + ruleId + "</em>";
        }
        String cleanName = filename.contains("/") ? filename.substring(filename.lastIndexOf("/") + 1) : filename;
        if (!cleanName.toLowerCase().endsWith(".png")) cleanName += ".png";
        return String.format(
                "<img src='screenshots/%s' class='screenshot-img' onclick='openModal(this.src)' " +
                        "onerror=\"this.style.display='none'; this.parentElement.innerHTML='Image Error';\" alt='Screenshot' />",
                cleanName
        );
    }
}