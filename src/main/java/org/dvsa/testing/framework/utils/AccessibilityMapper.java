package org.dvsa.testing.framework.utils;

import java.util.List;

public class AccessibilityMapper {
    public static List<String> mapAxeRuleToGovPaths(String axeRuleId) {
        return switch (axeRuleId) {
            case "color-contrast" -> List.of("colour", "button");
            case "region", "main-role" -> List.of("layout");
            case "button-name" -> List.of("button");
            default -> List.of("headings");
        };
    }
}