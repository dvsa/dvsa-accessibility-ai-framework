package org.dvsa.testing.framework.mcp.integration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.ai.BedrockAgentAnalyser;
import org.dvsa.testing.framework.ai.BedrockRecommendation;
import org.dvsa.testing.framework.mcp.client.AccessibilityMcpClient;
import org.dvsa.testing.framework.config.AppConfig;
import com.deque.html.axecore.results.Rule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced Bedrock Agent Analyser that uses real-time MCP server
 * instead of static S3 knowledge base
 */
public class McpEnhancedBedrockAnalyser extends BedrockAgentAnalyser {
    private static final Logger LOGGER = LogManager.getLogger(McpEnhancedBedrockAnalyser.class);
    
    private final AccessibilityMcpClient mcpClient;
    private final boolean useMcpForGuidelines;
    private final boolean fallbackToS3;
    
    public McpEnhancedBedrockAnalyser(java.net.http.HttpClient client) {
        super(client);
        this.mcpClient = new AccessibilityMcpClient();
        
        String knowledgeSource = AppConfig.getString("bedrock.agent.knowledge.source", "mcp");
        this.useMcpForGuidelines = "mcp".equals(knowledgeSource);
        
        String fallbackConfig = AppConfig.getString("bedrock.agent.fallback.to.s3", "true");
        this.fallbackToS3 = Boolean.parseBoolean(fallbackConfig);
        
        LOGGER.info("MCP Enhanced Bedrock Analyser initialized. MCP enabled: {}, S3 fallback: {}", 
                   useMcpForGuidelines, fallbackToS3);
    }
    
    /**
     * Enhanced analysis using real-time guidelines from MCP server
     */
    @Override
    public List<BedrockRecommendation> analyseViolationsWithBedrock(
            ConcurrentHashMap<Rule, String> violations,
            Map<String, BedrockRecommendation> kbMap // This becomes fallback data
    ) throws Exception {
        
        if (!useMcpForGuidelines || !mcpClient.isServerHealthy()) {
            LOGGER.info("Using standard Bedrock analysis (MCP disabled or unavailable)");
            return super.analyseViolationsWithBedrock(violations, kbMap);
        }
        
        LOGGER.info("Using MCP-enhanced Bedrock analysis with real-time guidelines");
        
        // Extract rule IDs from violations
        Set<String> ruleIds = violations.keySet().stream()
                .map(Rule::getId)
                .collect(Collectors.toSet());
        
        // Get real-time guidelines from MCP server
        CompletableFuture<Map<String, List<BedrockRecommendation>>> mcpGuidelines = 
                mcpClient.getBatchGuidelines(ruleIds);
        
        // Enhance the knowledge base with real-time data
        Map<String, BedrockRecommendation> enhancedKbMap = new HashMap<>(kbMap);
        
        try {
            Map<String, List<BedrockRecommendation>> realTimeGuidelines = mcpGuidelines.get();
            
            for (Map.Entry<String, List<BedrockRecommendation>> entry : realTimeGuidelines.entrySet()) {
                String ruleId = entry.getKey();
                List<BedrockRecommendation> guidelines = entry.getValue();
                
                if (!guidelines.isEmpty()) {
                    // Use the first guideline as the primary recommendation
                    BedrockRecommendation primary = guidelines.get(0);
                    enhancedKbMap.put(ruleId, primary);
                    
                    // If there are multiple guidelines, merge them
                    if (guidelines.size() > 1) {
                        BedrockRecommendation merged = mergeGuidelines(guidelines);
                        enhancedKbMap.put(ruleId, merged);
                    }
                }
            }
            
            LOGGER.info("Enhanced knowledge base with {} real-time guidelines from MCP server", 
                       realTimeGuidelines.size());
            
        } catch (Exception e) {
            LOGGER.warn("Failed to get real-time guidelines from MCP server, falling back to standard analysis", e);
            return super.analyseViolationsWithBedrock(violations, kbMap);
        }
        
        // Now call the standard Bedrock analysis with enhanced knowledge base
        return super.analyseViolationsWithBedrock(violations, enhancedKbMap);
    }
    
    /**
     * Get comprehensive accessibility guidance for a specific rule
     * This provides more detailed guidance than the standard approach
     */
    public CompletableFuture<DetailedGuidance> getDetailedGuidance(String ruleId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<BedrockRecommendation> guidelines = mcpClient.getAccessibilityGuidelines(ruleId).get();
                
                return new DetailedGuidance(
                        ruleId,
                        guidelines,
                        System.currentTimeMillis(), // timestamp
                        "mcp-server" // source
                );
                
            } catch (Exception e) {
                LOGGER.warn("Failed to get detailed guidance for: " + ruleId, e);
                return new DetailedGuidance(ruleId, Collections.emptyList(), System.currentTimeMillis(), "fallback");
            }
        });
    }
    
    /**
     * Merge multiple guidelines into a comprehensive recommendation
     */
    private BedrockRecommendation mergeGuidelines(List<BedrockRecommendation> guidelines) {
        if (guidelines.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge empty guidelines list");
        }
        
        if (guidelines.size() == 1) {
            return guidelines.get(0);
        }
        
        BedrockRecommendation first = guidelines.get(0);
        StringBuilder mergedRecommendation = new StringBuilder();
        StringBuilder mergedIssue = new StringBuilder();
        List<String> references = new ArrayList<>();
        List<String> examples = new ArrayList<>();
        
        for (BedrockRecommendation guideline : guidelines) {
            if (guideline.recommendation() != null && !guideline.recommendation().isEmpty()) {
                mergedRecommendation.append(guideline.recommendation()).append(" ");
            }
            
            if (guideline.issue() != null && !guideline.issue().isEmpty()) {
                mergedIssue.append(guideline.issue()).append(" ");
            }
            
            if (guideline.reference() != null && !guideline.reference().isEmpty()) {
                references.add(guideline.reference());
            }
            
            if (guideline.example() != null && !guideline.example().isEmpty()) {
                examples.add(guideline.example());
            }
        }
        
        return BedrockRecommendation.builder()
                .ruleId(first.ruleId())
                .issue(mergedIssue.toString().trim())
                .recommendation(mergedRecommendation.toString().trim())
                .reference(String.join(", ", references))
                .example(String.join("; ", examples))
                .build();
    }
    
    /**
     * Refresh the MCP server cache to get latest guidelines
     */
    public void refreshRealTimeGuidelines() {
        if (useMcpForGuidelines) {
            mcpClient.refreshCache();
            LOGGER.info("Real-time guidelines cache refreshed");
        }
    }
    
    /**
     * Check if MCP server is available for real-time guidelines
     */
    public boolean isRealTimeGuidelinesAvailable() {
        return useMcpForGuidelines && mcpClient.isServerHealthy();
    }
    
    /**
     * Detailed guidance result containing multiple sources
     */
    public static class DetailedGuidance {
        private final String ruleId;
        private final List<BedrockRecommendation> guidelines;
        private final long timestamp;
        private final String source;
        
        public DetailedGuidance(String ruleId, List<BedrockRecommendation> guidelines, long timestamp, String source) {
            this.ruleId = ruleId;
            this.guidelines = guidelines;
            this.timestamp = timestamp;
            this.source = source;
        }
        
        // Getters
        public String getRuleId() { return ruleId; }
        public List<BedrockRecommendation> getGuidelines() { return guidelines; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
        
        public boolean hasGuidelines() { return !guidelines.isEmpty(); }
        
        public BedrockRecommendation getPrimaryGuideline() {
            return guidelines.isEmpty() ? null : guidelines.get(0);
        }
    }
}