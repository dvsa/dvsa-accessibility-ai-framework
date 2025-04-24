package org.dvsa.testing.framework.axe;

import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;

import java.util.List;
import java.util.Map;

public class HtmlReportGenerator {
    public static String generateHtmlReport(List<Rule> rules) {
        StringBuilder htmlReport = new StringBuilder();
        int totalIssues = rules.size();

        htmlReport.append("<!DOCTYPE html>\n")
                .append("<html><head><title>Accessibility Report</title></head>")
                .append("<style>")
                .append("body { font-family: Arial, sans-serif; margin: 20px; }")
                .append("table { width: 100%; border-collapse: collapse; }")
                .append("th, td { border: 1px solid black; padding: 8px; text-align: left; }")
                .append("th { background-color: #f2f2f2; }")
                .append("</style>")
                .append("</head><body>")
                .append("<h1>Accessibility Report</h1>")
                .append("<h2>Overall Issues Found: ").append(totalIssues).append("</h2>")
                .append("<table>")
                .append("<tr><th>ID</th><th>Description</th><th>URL</th><th>Impact</th><th>Help URL</th><th>Failure Summary</th></tr>");

        Map<String, String> impactColors = Map.of(
                "critical", "red",
                "serious", "pink",
                "minor", "blue",
                "moderate", "purple",
                "review", "turquoise"
        );

        for (Rule rule : rules) {
            String color = impactColors.getOrDefault(rule.getImpact(), "gray");
            for (CheckedNode node : rule.getNodes()) {
                htmlReport.append("<tr>")
                        .append("<td>").append(rule.getId()).append("</td>")
                        .append("<td>").append(rule.getDescription()).append("</td>")
                        .append("<td>").append(node.getHtml()).append("</td>")
                        .append("<td style='background-color:").append(color).append(";'>")
                        .append(rule.getImpact()).append("</td>")
                        .append("<td><a href='").append(rule.getHelpUrl()).append("'>Help</a></td>")
                        .append("<td>").append(node.getFailureSummary()).append("</td>")
                        .append("</tr>");
            }
        }
        htmlReport.append("</table></body></html>");
        return htmlReport.toString();
    }
}