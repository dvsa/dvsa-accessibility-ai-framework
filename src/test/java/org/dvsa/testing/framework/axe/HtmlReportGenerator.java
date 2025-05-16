package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HtmlReportGenerator {
    static StringBuilder htmlReport = new StringBuilder();
    public static String generateHtmlReport(ConcurrentHashMap<Rule, String> rules) {
        Set<String> seenRulesIds = new HashSet<>();
        int totalIssues = rules.size();

        htmlReport.append("<!DOCTYPE html>\n")
                .append("<html><head><meta charset='UTF-8'><title>Accessibility Report</title>")
                .append("<style>")
                .append("body { font-family: Arial, sans-serif; margin: 20px; }")
                .append("table { width: 100%; border-collapse: collapse; margin-bottom: 40px; }")
                .append("th, td { border: 1px solid black; padding: 8px; text-align: left; }")
                .append("th { background-color: #f2f2f2; }")
                .append("</style>")
                .append("</head><body>")
                .append("<h1>Accessibility Report</h1>")
                .append("<h2> Issues Found").append("</h2>")
                .append("<table>")
                .append("<tr>")
                .append("<th>ID</th>")
                .append("<th>Description</th>")
                .append("<th>HTML</th>")
                .append("<th>URL</th>")
                .append("<th>Impact</th>")
                .append("<th>Help URL</th>")
                .append("<th>Failure Summary</th>")
                .append("</tr>");

        Map<String, String> impactColors = Map.of(
                "critical", "red",
                "serious", "pink",
                "minor", "blue",
                "moderate", "purple",
                "review", "turquoise"
        );

        for (Map.Entry<Rule, String> rule : rules.entrySet()){
            if(seenRulesIds.add(rule.getKey().getId())) {
                String color = impactColors.getOrDefault(rule.getKey().getImpact(), "grey");

                for (CheckedNode node : rule.getKey().getNodes()) {
                    htmlReport.append("<tr>")
                            .append("<td>").append(rule.getKey().getId()).append("</td>")
                            .append("<td>").append(rule.getKey().getDescription()).append("</td>")
                            .append("<td>").append(node.getHtml()).append("</td>")
                            .append("<td>").append(rule.getValue().toLowerCase()).append("</td>")
                            .append("<td style='background-color:").append(color).append(";'>")
                            .append(rule.getKey().getImpact()).append("</td>")
                            .append("<td><a href='").append(rule.getKey().getHelpUrl()).append("'>Help</a></td>")
                            .append("<td>").append(node.getFailureSummary()).append("</td>")
                            .append("</tr>");
                }
            }
        }
        htmlReport.append("</table></body></html>");
        return htmlReport.toString()
                .replaceAll("<th\\s+scope=[\"']col[\"']\\s+class=[\"']numeric[\"']\\s*>\\s*</th>", "")
                .replaceAll("<th \\s=\\s col[\"']=\\d>\\s*</th>", "")
                .replaceAll("<th\\s+class=\"\"\\s+colspan=\"2\">\\s*</th>", "");
    }
}