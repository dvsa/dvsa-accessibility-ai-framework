package org.dvsa.testing.framework.ai;

import com.deque.html.axecore.results.Rule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class BedrockAgentAnalyser {
    private static final Logger LOGGER = LogManager.getLogger(BedrockAgentAnalyser.class);
    private final String bedrockRegion;
    private final String bedrockAgentId;
    private final String bedrockAgentAliasId;
    private HttpClient client;
    private final ObjectMapper MAPPER = new ObjectMapper();

    public BedrockAgentAnalyser(HttpClient client) {
        this.bedrockRegion = org.dvsa.testing.framework.config.AppConfig.getString("bedrock.region", "eu-west-2");
        this.bedrockAgentId = org.dvsa.testing.framework.config.AppConfig.getString("bedrock.agent.id");
        this.bedrockAgentAliasId = org.dvsa.testing.framework.config.AppConfig.getString("bedrock.agent.alias.id");
        this.client = Objects.requireNonNull(client);
    }

    public List<BedrockRecommendation> analyseViolationsWithBedrock(
            ConcurrentHashMap<Rule, String> violations,
            Map<String, BedrockRecommendation> kbMap
    ) throws Exception {

        List<BedrockRecommendation> allRecommendations = new ArrayList<>();

        String violationsJson = convertViolationsToJsonArray(violations);

        JSONObject promptJson = new JSONObject();
        promptJson.put(
                "inputText",
                "You are an accessibility expert. Produce ONLY valid JSON. " +
                        "Return a STRICT JSON array of objects, each with exactly these fields: " +
                        "{\"ruleId\":\"...\",\"issue\":\"...\",\"recommendation\":\"...\",\"reference\":\"...\",\"exampleFix\":\"...\"}. " +
                        "Do NOT include any text outside the array. Escape all HTML quotes in exampleFix. " +
                        "If uncertain, return an empty JSON array []."
        );
        promptJson.put("violations", new JSONArray(violationsJson));


        String agentResponse = invokeAgentViaRest(promptJson);
        LOGGER.info("Raw Bedrock response: {}", agentResponse);


        try {
            JsonNode root = MAPPER.readTree(agentResponse);

            if (root.isArray()) {
                for (JsonNode node : root) {
                    BedrockRecommendation rec = parseNodeRecommendation(node, kbMap);
                    if (rec != null) allRecommendations.add(rec);
                }
            } else if (root.isObject()) {
                JsonNode recsNode = root.has("recommendations") ? root.get("recommendations") : root;
                if (recsNode.isArray()) {
                    for (JsonNode node : recsNode) {
                        BedrockRecommendation rec = parseNodeRecommendation(node, kbMap);
                        if (rec != null) allRecommendations.add(rec);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Bedrock JSON invalid/truncated; falling back to text parser", e);

            String[] issues = agentResponse.split("(?=\\s*Issue:)");
            for (String issueText : issues) {
                BedrockRecommendation rec = parseTextRecommendation(issueText, kbMap);
                if (rec != null) {
                    allRecommendations.add(rec);
                }
            }
        }

        return allRecommendations.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    private BedrockRecommendation parseTextRecommendation(String text, Map<String, BedrockRecommendation> kbMap) {
        if (text == null || text.isBlank()) return null;

        BedrockRecommendation.Builder builder = BedrockRecommendation.builder()
                .ruleId("UNKNOWN_RULE")
                .issue(text.trim())
                .recommendation("Review and fix based on the issue text.")
                .reference("")
                .example("");

        // Try to detect ruleId from text or default to "UNKNOWN_RULE"
        kbMap.forEach((ruleId, kbRec) -> {
            if (text.toLowerCase().contains(ruleId.toLowerCase())) {
                builder.ruleId(ruleId)
                        .recommendation(kbRec.recommendation())
                        .reference(kbRec.reference())
                        .example(kbRec.example());
            }
        });

        return builder.build();
    }

    private BedrockRecommendation parseNodeRecommendation(JsonNode node, Map<String, BedrockRecommendation> kbMap) {
        if (node == null) return null;
        try {
            BedrockRecommendation rec;
            if (node.has("ruleId")) {
                rec = MAPPER.treeToValue(node, BedrockRecommendation.class);
            } else if (node.has("text")) {
                rec = parseTextRecommendation(node.get("text").asText(), kbMap);
            } else {
                rec = parseTextRecommendation(node.toString(), kbMap);
            }

            if (rec != null && rec.ruleId() != null && kbMap.containsKey(rec.ruleId())) {
                BedrockRecommendation kbRec = kbMap.get(rec.ruleId());
                BedrockRecommendation.Builder builder = BedrockRecommendation.builder()
                        .ruleId(rec.ruleId())
                        .recommendation((rec.recommendation() == null || rec.recommendation().isBlank()) ? kbRec.recommendation() : rec.recommendation())
                        .reference((rec.reference() == null || rec.reference().isBlank()) ? kbRec.reference() : rec.reference())
                        .example((rec.example() == null || rec.example().isBlank()) ? kbRec.example() : rec.example())
                        .issue(rec.issue())
                        .rawText(rec.rawText());
                return builder.build();
            }
            return rec;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse node into BedrockRecommendation; using text fallback", e);
            return parseTextRecommendation(node.toString(), kbMap);
        }
    }

    private String convertViolationsToJsonArray(ConcurrentHashMap<Rule, String> violations) {
        return new JSONArray(
                violations.entrySet().stream()
                        .map(e -> new JSONObject()
                                .put("ruleId", e.getKey().getId())
                                .put("violation", e.getValue()))
                        .toList()
        ).toString();
    }

    private String invokeAgentViaRest(JSONObject promptJson) throws Exception {
        String sessionId = "session-" + UUID.randomUUID();
        String endpoint = String.format(
            "https://bedrock-agent-runtime.%s.amazonaws.com/agents/%s/agentAliases/%s/sessions/%s/text",
            bedrockRegion, bedrockAgentId, bedrockAgentAliasId, sessionId
        );

        SdkHttpFullRequest sdkRequest = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(URI.create(endpoint))
                .appendHeader("Content-Type", "application/json")
                .contentStreamProvider(() ->
                        new ByteArrayInputStream(promptJson.toString().getBytes(StandardCharsets.UTF_8)))
                .build();

        Aws4Signer signer = Aws4Signer.create();

        SdkHttpFullRequest signed = signer.sign(
                sdkRequest,
                Aws4SignerParams.builder()
                    .signingRegion(Region.of(bedrockRegion))
                    .signingName("bedrock")
                    .awsCredentials(DefaultCredentialsProvider.create().resolveCredentials())
                    .build()
        );

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(promptJson.toString()));

        List<String> restricted = List.of("host", "content-length", "transfer-encoding", "expect", "connection");

        for (var e : signed.headers().entrySet()) {
            if (!restricted.contains(e.getKey().toLowerCase())) {
                e.getValue().forEach(v -> builder.header(e.getKey(), v));
            }
        }

        HttpRequest request = builder.build();
        client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response =
                client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Bedrock agent call failed: " +
                            new String(response.body(), StandardCharsets.UTF_8));
        }

        String decoded = decodeEventStreamResponse(response.body());
        LOGGER.debug("Decoded Bedrock Output: {}", decoded);

        String cleaned = decoded.trim();
        if (cleaned.startsWith("[[")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        cleaned = cleaned.replaceAll("(\\r|\\n){2,}", "\n").trim();

        LOGGER.debug("Cleaned Bedrock JSON: {}", cleaned);

        return cleaned;
    }

    private String decodeEventStreamResponse(byte[] responseBytes) {
        String raw = new String(responseBytes, StandardCharsets.UTF_8);
        Pattern base64Pattern = Pattern.compile("\"bytes\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
        Matcher matcher = base64Pattern.matcher(raw);

        List<String> decodedChunks = new ArrayList<>();
        while (matcher.find()) {
            try {
                String inner = new String(Base64.getDecoder().decode(matcher.group(1)), StandardCharsets.UTF_8).trim();
                if (inner.startsWith("{") || inner.startsWith("[")) {
                    decodedChunks.add(inner);
                } else {
                    inner = inner.replace("\"", "\\\"").replace("\n", " ");
                    decodedChunks.add("{\"recommendation\":\"" + inner + "\"}");
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to decode Base64 chunk: {}", e.getMessage());
            }
        }

        if (decodedChunks.isEmpty()) {
            LOGGER.warn("No Base64 payloads found; using fallback");
            decodedChunks.add("{\"recommendation\":\"" + raw.replace("\"", "\\\"").replace("\n", " ") + "\"}");
        }
        return "[" + String.join(",", decodedChunks) + "]";
    }
}