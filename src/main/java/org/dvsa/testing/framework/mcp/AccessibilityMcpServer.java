package org.dvsa.testing.framework.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.mcp.scrapers.DesignSystemScraper;
import org.dvsa.testing.framework.mcp.scrapers.WcagScraper;
import org.dvsa.testing.framework.mcp.models.AccessibilityGuideline;
import org.dvsa.testing.framework.mcp.cache.GuidelineCache;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MCP Server for real-time accessibility guidelines scraping
 * Provides up-to-date accessibility guidance from GOV.UK Design System and W3C
 */
public class AccessibilityMcpServer {
    private static final Logger LOGGER = LogManager.getLogger(AccessibilityMcpServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final DesignSystemScraper designSystemScraper;
    private final WcagScraper wcagScraper;
    private final GuidelineCache cache;
    
    public AccessibilityMcpServer() {
        this.designSystemScraper = new DesignSystemScraper();
        this.wcagScraper = new WcagScraper();
        this.cache = new GuidelineCache();
    }
    
    /**
     * Main MCP server loop - handles incoming requests
     */
    public void start() {
        LOGGER.info("Starting Accessibility MCP Server...");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter writer = new PrintWriter(System.out, true)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode request = MAPPER.readTree(line);
                    JsonNode response = handleRequest(request);
                    writer.println(MAPPER.writeValueAsString(response));
                } catch (Exception e) {
                    LOGGER.error("Error processing MCP request", e);
                    writer.println(createErrorResponse(e.getMessage()));
                }
            }
        } catch (IOException e) {
            LOGGER.error("MCP Server I/O error", e);
        }
    }
    
    private JsonNode handleRequest(JsonNode request) {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        
        return switch (method) {
            case "initialize" -> handleInitialize(params);
            case "tools/list" -> listTools();
            case "tools/call" -> handleToolCall(params);
            default -> createErrorResponse("Unknown method: " + method);
        };
    }
    
    private JsonNode handleInitialize(JsonNode params) {
        ObjectNode response = MAPPER.createObjectNode();
        ObjectNode capabilities = MAPPER.createObjectNode();
        ObjectNode tools = MAPPER.createObjectNode();
        
        tools.put("listChanged", true);
        capabilities.set("tools", tools);
        response.set("capabilities", capabilities);
        
        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", "accessibility-guidelines-mcp");
        serverInfo.put("version", "1.0.0");
        response.set("serverInfo", serverInfo);
        
        return response;
    }
    
    private JsonNode listTools() {
        ObjectNode response = MAPPER.createObjectNode();
        ArrayNode tools = MAPPER.createArrayNode();
        
        // Tool 1: Scrape Design System Guidelines
        ObjectNode designSystemTool = MAPPER.createObjectNode();
        designSystemTool.put("name", "scrape_design_system_guidelines");
        designSystemTool.put("description", "Scrape accessibility guidelines from GOV.UK Design System");
        ObjectNode designParams = MAPPER.createObjectNode();
        designParams.put("type", "object");
        ObjectNode designProps = MAPPER.createObjectNode();
        ObjectNode componentType = MAPPER.createObjectNode();
        componentType.put("type", "string");
        componentType.put("description", "Component type (e.g., 'forms', 'tables', 'navigation')");
        designProps.set("component_type", componentType);
        designParams.set("properties", designProps);
        designSystemTool.set("inputSchema", designParams);
        tools.add(designSystemTool);
        
        // Tool 2: Get ARIA Guidance
        ObjectNode ariaTool = MAPPER.createObjectNode();
        ariaTool.put("name", "get_aria_guidance");
        ariaTool.put("description", "Get ARIA attributes guidance for specific roles and elements");
        ObjectNode ariaParams = MAPPER.createObjectNode();
        ariaParams.put("type", "object");
        ObjectNode ariaProps = MAPPER.createObjectNode();
        ObjectNode ariaAttribute = MAPPER.createObjectNode();
        ariaAttribute.put("type", "string");
        ariaAttribute.put("description", "ARIA attribute name");
        ariaProps.set("aria_attribute", ariaAttribute);
        ObjectNode elementRole = MAPPER.createObjectNode();
        elementRole.put("type", "string");
        elementRole.put("description", "Element role or tag name");
        ariaProps.set("element_role", elementRole);
        ariaParams.set("properties", ariaProps);
        ariaTool.set("inputSchema", ariaParams);
        tools.add(ariaTool);
        
        // Tool 3: Check WCAG Compliance
        ObjectNode wcagTool = MAPPER.createObjectNode();
        wcagTool.put("name", "check_wcag_compliance");
        wcagTool.put("description", "Get WCAG compliance guidance for specific accessibility rules");
        ObjectNode wcagParams = MAPPER.createObjectNode();
        wcagParams.put("type", "object");
        ObjectNode wcagProps = MAPPER.createObjectNode();
        ObjectNode ruleId = MAPPER.createObjectNode();
        ruleId.put("type", "string");
        ruleId.put("description", "Accessibility rule ID (e.g., 'color-contrast', 'aria-hidden-focus')");
        wcagProps.set("rule_id", ruleId);
        ObjectNode level = MAPPER.createObjectNode();
        level.put("type", "string");
        level.put("description", "WCAG compliance level (A, AA, AAA)");
        wcagProps.set("level", level);
        wcagParams.set("properties", wcagProps);
        wcagTool.set("inputSchema", wcagParams);
        tools.add(wcagTool);
        
        // Tool 4: Refresh Cache
        ObjectNode cacheTool = MAPPER.createObjectNode();
        cacheTool.put("name", "refresh_guidelines_cache");
        cacheTool.put("description", "Invalidate cache and refresh accessibility guidelines from sources");
        ObjectNode cacheParams = MAPPER.createObjectNode();
        cacheParams.put("type", "object");
        ObjectNode cacheProps = MAPPER.createObjectNode();
        ObjectNode forceRefresh = MAPPER.createObjectNode();
        forceRefresh.put("type", "boolean");
        forceRefresh.put("description", "Force refresh even if cache is recent");
        cacheProps.set("force_refresh", forceRefresh);
        cacheParams.set("properties", cacheProps);
        cacheTool.set("inputSchema", cacheParams);
        tools.add(cacheTool);
        
        response.set("tools", tools);
        return response;
    }
    
    private JsonNode handleToolCall(JsonNode params) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");
        
        try {
            return switch (toolName) {
                case "scrape_design_system_guidelines" -> scrapeDesignSystemGuidelines(arguments);
                case "get_aria_guidance" -> getAriaGuidance(arguments);
                case "check_wcag_compliance" -> checkWcagCompliance(arguments);
                case "refresh_guidelines_cache" -> refreshGuidelinesCache(arguments);
                default -> createErrorResponse("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            LOGGER.error("Error executing tool: " + toolName, e);
            return createErrorResponse("Tool execution failed: " + e.getMessage());
        }
    }
    
    private JsonNode scrapeDesignSystemGuidelines(JsonNode arguments) {
        String componentType = arguments.path("component_type").asText("general");
        
        try {
            List<AccessibilityGuideline> guidelines = cache.getOrFetch(
                "design_system_" + componentType,
                () -> designSystemScraper.scrapeGuidelines(componentType)
            );
            
            ObjectNode response = MAPPER.createObjectNode();
            ArrayNode content = MAPPER.createArrayNode();
            
            ObjectNode textContent = MAPPER.createObjectNode();
            textContent.put("type", "text");
            StringBuilder text = new StringBuilder();
            text.append("GOV.UK Design System Guidelines for ").append(componentType).append(":\n\n");
            
            for (AccessibilityGuideline guideline : guidelines) {
                text.append("Rule: ").append(guideline.getRuleId()).append("\n");
                text.append("Title: ").append(guideline.getTitle()).append("\n");
                text.append("Description: ").append(guideline.getDescription()).append("\n");
                text.append("Fix: ").append(guideline.getRecommendedFix()).append("\n");
                text.append("Example: ").append(guideline.getExampleFix()).append("\n");
                text.append("Reference: ").append(guideline.getReference()).append("\n\n");
            }
            
            textContent.put("text", text.toString());
            content.add(textContent);
            response.set("content", content);
            
            return response;
        } catch (Exception e) {
            return createErrorResponse("Failed to scrape design system guidelines: " + e.getMessage());
        }
    }
    
    private JsonNode getAriaGuidance(JsonNode arguments) {
        String ariaAttribute = arguments.path("aria_attribute").asText();
        String elementRole = arguments.path("element_role").asText();
        
        try {
            List<AccessibilityGuideline> guidelines = cache.getOrFetch(
                "aria_" + ariaAttribute + "_" + elementRole,
                () -> wcagScraper.scrapeAriaGuidance(ariaAttribute, elementRole)
            );
            
            ObjectNode response = MAPPER.createObjectNode();
            ArrayNode content = MAPPER.createArrayNode();
            
            ObjectNode textContent = MAPPER.createObjectNode();
            textContent.put("type", "text");
            StringBuilder text = new StringBuilder();
            text.append("ARIA Guidance for ").append(ariaAttribute)
                .append(" on ").append(elementRole).append(":\n\n");
            
            for (AccessibilityGuideline guideline : guidelines) {
                text.append("Rule: ").append(guideline.getRuleId()).append("\n");
                text.append("Description: ").append(guideline.getDescription()).append("\n");
                text.append("Usage: ").append(guideline.getRecommendedFix()).append("\n");
                text.append("Example: ").append(guideline.getExampleFix()).append("\n");
                text.append("Reference: ").append(guideline.getReference()).append("\n\n");
            }
            
            textContent.put("text", text.toString());
            content.add(textContent);
            response.set("content", content);
            
            return response;
        } catch (Exception e) {
            return createErrorResponse("Failed to get ARIA guidance: " + e.getMessage());
        }
    }
    
    private JsonNode checkWcagCompliance(JsonNode arguments) {
        String ruleId = arguments.path("rule_id").asText();
        String level = arguments.path("level").asText("AA");
        
        try {
            List<AccessibilityGuideline> guidelines = cache.getOrFetch(
                "wcag_" + ruleId + "_" + level,
                () -> wcagScraper.scrapeWcagGuidance(ruleId, level)
            );
            
            ObjectNode response = MAPPER.createObjectNode();
            ArrayNode content = MAPPER.createArrayNode();
            
            ObjectNode textContent = MAPPER.createObjectNode();
            textContent.put("type", "text");
            StringBuilder text = new StringBuilder();
            text.append("WCAG ").append(level).append(" Compliance for ").append(ruleId).append(":\n\n");
            
            for (AccessibilityGuideline guideline : guidelines) {
                text.append("Rule: ").append(guideline.getRuleId()).append("\n");
                text.append("Title: ").append(guideline.getTitle()).append("\n");
                text.append("Description: ").append(guideline.getDescription()).append("\n");
                text.append("Requirements: ").append(guideline.getRecommendedFix()).append("\n");
                text.append("Example: ").append(guideline.getExampleFix()).append("\n");
                text.append("Reference: ").append(guideline.getReference()).append("\n\n");
            }
            
            textContent.put("text", text.toString());
            content.add(textContent);
            response.set("content", content);
            
            return response;
        } catch (Exception e) {
            return createErrorResponse("Failed to check WCAG compliance: " + e.getMessage());
        }
    }
    
    private JsonNode refreshGuidelinesCache(JsonNode arguments) {
        boolean forceRefresh = arguments.path("force_refresh").asBoolean(false);
        
        try {
            if (forceRefresh) {
                cache.clearAll();
                LOGGER.info("Cache cleared forcefully");
            } else {
                cache.clearExpired();
                LOGGER.info("Expired cache entries cleared");
            }
            
            ObjectNode response = MAPPER.createObjectNode();
            ArrayNode content = MAPPER.createArrayNode();
            
            ObjectNode textContent = MAPPER.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", "Guidelines cache refreshed successfully. Next requests will fetch fresh data.");
            content.add(textContent);
            response.set("content", content);
            
            return response;
        } catch (Exception e) {
            return createErrorResponse("Failed to refresh cache: " + e.getMessage());
        }
    }
    
    private JsonNode createErrorResponse(String message) {
        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", -1);
        error.put("message", message);
        
        ObjectNode response = MAPPER.createObjectNode();
        response.set("error", error);
        return response;
    }
    
    public static void main(String[] args) {
        new AccessibilityMcpServer().start();
    }
}