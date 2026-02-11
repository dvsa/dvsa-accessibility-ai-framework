package org.dvsa.testing.framework.mcp.models;

import java.time.LocalDateTime;

/**
 * Model representing an accessibility guideline scraped from various sources
 */
public class AccessibilityGuideline {
    private String ruleId;
    private String title;
    private String description;
    private String recommendedFix;
    private String exampleFix;
    private String reference;
    private String source; // "gov-uk", "w3c-wcag", "aria-spec"
    private LocalDateTime lastUpdated;
    
    public AccessibilityGuideline() {}
    
    public AccessibilityGuideline(String ruleId, String title, String description, 
                                 String recommendedFix, String exampleFix, String reference, String source) {
        this.ruleId = ruleId;
        this.title = title;
        this.description = description;
        this.recommendedFix = recommendedFix;
        this.exampleFix = exampleFix;
        this.reference = reference;
        this.source = source;
        this.lastUpdated = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getRecommendedFix() { return recommendedFix; }
    public void setRecommendedFix(String recommendedFix) { this.recommendedFix = recommendedFix; }
    
    public String getExampleFix() { return exampleFix; }
    public void setExampleFix(String exampleFix) { this.exampleFix = exampleFix; }
    
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    
    @Override
    public String toString() {
        return "AccessibilityGuideline{" +
                "ruleId='" + ruleId + '\'' +
                ", title='" + title + '\'' +
                ", source='" + source + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}