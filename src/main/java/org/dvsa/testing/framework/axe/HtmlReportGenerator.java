package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import org.dvsa.testing.framework.ai.BedrockRecommendation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HtmlReportGenerator {

    /**
     * Escape HTML characters to display them as text in HTML
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#x27;");
    }

    
    private static String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    
    private static String normalizeRuleId(String ruleId) {
        return switch (ruleId) {
            case "image-alt" -> "AXE_IMAGE_ALT_NOT_REPEATED";
            case "heading-order" -> "AXE_HEADING_ORDER";
            case "banner" -> "AXE_ONE_BANNER_LANDMARK";
            case "landmark-no-duplicate-banner" -> "AXE_ONE_BANNER_LANDMARK";
            case "landmark-unique" -> "AXE_LANDMARKS_UNIQUE";
            case "region" -> "AXE_CONTENT_WITHIN_LANDMARKS";
            case "empty-heading" -> "AXE_HEADING_DISCERNIBLE_TEXT";
            case "heading-disorder" -> "AXE_HEADING_DISCERNIBLE_TEXT";
            case "landmark-one-main" -> "AXE_MAIN_LANDMARK";
            case "main-role" -> "AXE_MAIN_LANDMARK";
            case "label" -> "AXE_FORM_LABELS";
            case "table-headers" -> "AXE_TABLE_HEADERS";
            case "color-contrast" -> "AXE_COLOR_CONTRAST";
            case "link-name" -> "AXE_LINK_NAME";
            case "document-title" -> "AXE_DOCUMENT_TITLE";
            case "html-has-lang" -> "AXE_HTML_HAS_LANG";
            case "skip-link" -> "AXE_SKIP_LINK";
            case "aria-roles" -> "AXE_ARIA_ROLES";
            case "empty-table-header" -> "AXE_EMPTY_TABLE_HEADER";
            case "aria-allowed-attr" -> "AXE_ARIA_ALLOWED_ATTR";
            case "aria-hidden-focus" -> "AXE_ARIA_HIDDEN_FOCUS";
            default -> ruleId; // fallback: use original ID as-is
        };
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
                .append("body { font-family: Arial, sans-serif; margin: 20px; }")
                .append("table { width: 100%; border-collapse: collapse; margin-bottom: 40px; table-layout: fixed; }")
                .append("th, td { border: 1px solid black; padding: 8px; text-align: left; vertical-align: top; word-wrap: break-word; overflow-wrap: break-word; }")
                .append("th { background-color: #f2f2f2; font-weight: bold; }")
                .append("td { max-height: 200px; overflow-y: auto; }")
                .append(".screenshot-cell { width: 200px; text-align: center; }")
                .append(".screenshot-img { max-width: 100%; max-height: 150px; cursor: pointer; transition: transform 0.2s; }")
                .append(".screenshot-img:hover { transform: scale(1.05); }")
                .append(".modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.9); }")
                .append(".modal-content { margin: auto; display: block; width: 90%; max-width: 1200px; margin-top: 2%; }")
                .append(".close { position: absolute; top: 15px; right: 35px; color: #f1f1f1; font-size: 40px; font-weight: bold; cursor: pointer; }")
                .append(".close:hover { color: #bbb; text-decoration: none; }")
                .append(".url-cell { max-width: 250px; word-break: break-all; }")
                .append("td:nth-child(3) { max-width: 300px; word-break: break-all; word-wrap: break-word; }") 
                .append("td:nth-child(4) { max-width: 250px; word-break: break-all; word-wrap: break-word; }")
                .append("td:nth-child(8) { word-break: break-word; }")
                .append("td:nth-child(9) { word-break: break-word; }")
                .append("code { background-color: #f5f5f5; padding: 2px 4px; border: 1px solid #ddd; border-radius: 3px; font-family: 'Courier New', monospace; word-break: break-all; }")
                .append("@media screen and (max-width: 768px) { table { font-size: 12px; } .screenshot-cell { width: 120px; } }")
                .append("</style>")
                .append("</head><body>")
                .append("<h1>Accessibility Report</h1>")
                .append("<h2>Issues Found</h2>")
                .append("<table>")
                .append("<tr>")
                .append("<th style='width: 8%;'>ID</th>")
                .append("<th style='width: 12%;'>Description</th>")
                .append("<th style='width: 12%;'>HTML</th>")
                .append("<th style='width: 12%;'>URL</th>")
                .append("<th style='width: 10%;'>Screenshot</th>")
                .append("<th style='width: 6%;'>Impact</th>")
                .append("<th style='width: 6%;'>Help</th>")
                .append("<th style='width: 16%;'>Failure Summary</th>")
                .append("<th style='width: 18%;'>AI Recommendation</th>")
                .append("</tr>");

        Map<String, String> impactColors = Map.of(
                "critical", "red",
                "serious", "pink",
                "minor", "blue",
                "moderate", "purple",
                "review", "turquoise"
        );

        for (Map.Entry<Rule, String> ruleEntry : rules.entrySet()) {
            Rule rule = ruleEntry.getKey();

            if (!seenRulesIds.add(rule.getId())) continue;

            String color = impactColors.getOrDefault(rule.getImpact(), "grey");

            // Lookup recommendation in KB map, fallback if missing
            BedrockRecommendation br = bedrockRecommendationMap.get(rule.getId());
            
            // If not found with original rule ID, try the normalized version
            if (br == null) {
                String normalizedId = normalizeRuleId(rule.getId());
                br = bedrockRecommendationMap.get(normalizedId);
            }
            
            if (br == null && kbMap != null) {
                br = kbMap.get(rule.getId());
            }
            
            if (br == null) {
                br = BedrockRecommendation.builder()
                    .ruleId(rule.getId())
                    .recommendation("No recommendation available.")
                    .build();
            }

            for (CheckedNode node : rule.getNodes()) {
                String htmlContent = escapeHtml(truncateText(node.getHtml(), 200));
                String urlContent = truncateText(ruleEntry.getValue().toLowerCase(), 80);
                
                String screenshotPath = pageScreenshots.get(ruleEntry.getValue());
                String screenshotHtml = "";
                if (screenshotPath != null) {
                    screenshotHtml = "<img src='screenshots/" + screenshotPath + "' " +
                            "class='screenshot-img' " +
                            "alt='Page screenshot showing accessibility violation' " +
                            "title='Click to view full size' " +
                            "onclick='openModal(this.src)' />";
                } else {
                    screenshotHtml = "<em>No screenshot available</em>";
                }
                
                htmlReport.append("<tr>")
                        .append("<td>").append(rule.getId()).append("</td>")
                        .append("<td>").append(rule.getDescription()).append("</td>")
                        .append("<td>").append(htmlContent).append("</td>")
                        .append("<td>").append(urlContent).append("</td>")
                        .append("<td class='screenshot-cell'>").append(screenshotHtml).append("</td>")
                        .append("<td style='background-color:").append(color).append(";'>")
                        .append(rule.getImpact()).append("</td>")
                        .append("<td>");
                if (rule.getHelpUrl() != null) {
                    htmlReport.append("<a href='").append(rule.getHelpUrl()).append("'>Help</a>");
                } else {
                    htmlReport.append("-");
                }
                htmlReport.append("</td>")
                        .append("<td>").append(node.getFailureSummary()).append("</td>")
                        .append("<td>")
                        .append("<strong>").append(escapeHtml(br.recommendation())).append("</strong><br>");
                if (br.example() != null && !br.example().isBlank()) {
                    htmlReport.append("Example fix: <code>").append(escapeHtml(br.example())).append("</code><br>");
                }
                if (br.reference() != null && !br.reference().isBlank()) {
                    htmlReport.append("Reference: ").append(escapeHtml(br.reference()));
                }
                htmlReport.append("</td>")
                        .append("</tr>");
            }
        }

        htmlReport.append("</table>")
                .append("<!-- Modal for full-size screenshot viewing -->")
                .append("<div id='screenshotModal' class='modal'>")
                .append("<span class='close' onclick='closeModal()'>&times;</span>")
                .append("<img class='modal-content' id='modalImage'>")
                .append("</div>")
                .append("<script>")
                .append("function openModal(src) {")
                .append("    var modal = document.getElementById('screenshotModal');")
                .append("    var modalImg = document.getElementById('modalImage');")
                .append("    modal.style.display = 'block';")
                .append("    modalImg.src = src;")
                .append("}")
                .append("function closeModal() {")
                .append("    document.getElementById('screenshotModal').style.display = 'none';")
                .append("}")
                .append("// Close modal when clicking outside the image")
                .append("window.onclick = function(event) {")
                .append("    var modal = document.getElementById('screenshotModal');")
                .append("    if (event.target == modal) { closeModal(); }")
                .append("}")
                .append("</script>")
                .append("</body></html>");

        return htmlReport.toString()
                .replaceAll("<th\\s+scope=[\"']col[\"']\\s+class=[\"']numeric[\"']\\s*>\\s*</th>", "")
                .replaceAll("<th\\s+colspan=[\"']\\d+[\"']\\s*>\\s*</th>", "")
                .replaceAll("<th\\s+class=[\"']{0,1}\"?\"?\\s+colspan=[\"']2[\"']\\s*>\\s*</th>", "");
    }
}