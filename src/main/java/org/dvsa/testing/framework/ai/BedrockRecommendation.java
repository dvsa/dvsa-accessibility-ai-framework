package org.dvsa.testing.framework.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record class for Bedrock recommendations
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BedrockRecommendation(
    String ruleId,
    String recommendation,
    String reference,
    String example,
    String issue,
    @JsonProperty("text") String rawText
) {
    
    // Factory methods for convenience
    public static BedrockRecommendation of(String ruleId, String recommendation) {
        return new BedrockRecommendation(ruleId, recommendation, null, null, null, null);
    }
    
    // Convenience method for getting recommendation with fallback
    public String getRecommendationText() {
        if (recommendation != null && !recommendation.isBlank()) {
            return recommendation;
        }
        if (rawText != null && !rawText.isBlank()) {
            return rawText.trim();
        }
        return null;
    }
    
    // Builder pattern for complex construction
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String ruleId;
        private String recommendation;
        private String reference;
        private String example;
        private String issue;
        private String rawText;
        
        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }
        
        public Builder recommendation(String recommendation) {
            this.recommendation = recommendation;
            return this;
        }
        
        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }
        
        public Builder example(String example) {
            this.example = example;
            return this;
        }
        
        public Builder issue(String issue) {
            this.issue = issue;
            return this;
        }
        
        public Builder rawText(String rawText) {
            this.rawText = rawText;
            return this;
        }
        
        public BedrockRecommendation build() {
            return new BedrockRecommendation(ruleId, recommendation, reference, example, issue, rawText);
        }
    }
}
