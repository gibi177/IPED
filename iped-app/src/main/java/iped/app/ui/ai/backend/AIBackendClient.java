package iped.app.ui.ai.backend;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concrete implementation of {@link AIBackendService} responsible for handling
 * communication with the AI backend over HTTP.
 *
 * <p>This client supports two primary operations:</p>
 * <ul>
 *     <li><b>Chat initialization</b> via a synchronous HTTP POST request</li>
 *     <li><b>Streaming responses</b> via Server-Sent Events (SSE)</li>
 * </ul>
 *
 * <p>It uses Java's {@link HttpClient} for network communication and {@link Gson}
 * for JSON serialization/deserialization.</p>
 *
 * <p><b>Thread safety:</b> This class is thread-safe provided that the supplied
 * {@link AIBackendConfig} is immutable.</p>
 */
public class AIBackendClient implements AIBackendService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AIBackendClient.class);
    
    private final AIBackendConfig config;
    private final HttpClient httpClient;
    private final Gson gson;
    private final boolean debugEnabled;

    /**
     * Constructs a new {@code AIBackendClient}.
     *
     * @param config Backend configuration (must not be null).
     * @throws IllegalArgumentException if config is null
     */
    public AIBackendClient(AIBackendConfig config) {
        this.config = config;
        this.gson = new Gson();
        this.debugEnabled = true;
        // Create an HTTP client explicitly locked to HTTP/1.1 (required for the backend)
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Initializes a new chat session with the backend.
     *
     * <p>This method sends the initial chat content (HTML formatted) to the backend,
     * which responds with a chat identifier</p>
     *
     * @param chatHtml Initial chat content in HTML format
     * @return The MD5 chat_hash returned by the server.
     * @throws AIBackendException if:
     * <ul>
     *     <li>The HTTP request fails</li>
     *     <li>The backend returns a non-200 status</li>
     *     <li>The backend returns an application-level error</li>
     *     <li>The response cannot be parsed</li>
     * </ul>
     */
    @Override
    public String initChat(String chatHtml) throws AIBackendException {
        try {
            // Construct the payload using the specified DTO for initChat
            AIInitChatRequest payload = new AIInitChatRequest(chatHtml);
            String jsonBody = gson.toJson(payload);

            if (debugEnabled) {
                LOGGER.info("AI backend init_chat -> {} (payload={} chars)", config.getBaseUrl() + "/api/init_chat", jsonBody.length());
            }

            // Build the POST request targeting the initialization endpoint
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/init_chat"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            // Send synchronously
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Validate HTTP response
            if (response.statusCode() != 200) {
                if (debugEnabled) {
                    LOGGER.warn("AI backend init_chat failed with HTTP {}. Body: {}", response.statusCode(), truncate(response.body()));
                }
                throw new AIBackendException("Backend returned HTTP " + response.statusCode() + ": " + response.body());
            }

            // Parse the JSON response
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            
            // Check backend level error
            if (responseJson.has("error")) {
                if (debugEnabled) {
                    LOGGER.warn("AI backend init_chat returned application error: {}", responseJson.get("error").getAsString());
                }
                throw new AIBackendException("Backend Error: " + responseJson.get("error").getAsString());
            }

            return responseJson.get("response").getAsString();

        } catch (Exception e) {
            throw new AIBackendException("Failed to initialize chat: " + e.getMessage(), e);
        }
    }

    /**
     * Streams a chat response from the backend using Server-Sent Events (SSE).
     *
     * <p>This method sends a user question and listens for incremental responses
     * from the backend. Each chunk of data is processed and forwarded to the UI
     * via the provided {@link Consumer}.</p>
     *
     * @param chatHash Unique identifier of the chat session
     * @param question User's input question
     * @param history The previous messages of this same chat
     * @param eventHandler Callback invoked for each streamed content chunk
     * @throws AIBackendException if:
     * <ul>
     *     <li>The HTTP request fails</li>
     *     <li>The backend returns a non-200 status</li>
     *     <li>A streaming error occurs</li>
     *     <li>The stream cannot be read or parsed</li>
     * </ul>
     */
    @Override
    public void streamChatResponse(String chatHash, String question, List<AIStreamChatRequest.AIMessage> history, Consumer<String> eventHandler) throws AIBackendException {
        try {
            // Construct the payload for the query using the specified DTO
            AIStreamChatRequest payload = new AIStreamChatRequest(chatHash, question, history);
            String jsonBody = gson.toJson(payload);

            if (debugEnabled) {
                LOGGER.info("AI backend chat stream -> {} (chatHash={}, payload={} chars, history={})",
                        config.getBaseUrl() + "/api/chat/stream",
                        chatHash,
                        jsonBody.length(),
                        history != null ? history.size() : 0);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/chat/stream"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            // response as an InputStream so we can process data as it arrives.
            HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() != 200) {
                if (debugEnabled) {
                    LOGGER.warn("AI backend chat stream failed with HTTP {}", response.statusCode());
                }
                throw new AIBackendException("Backend returned HTTP " + response.statusCode());
            }

            // Process the Server-Sent Events (SSE) stream
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                
                // Blocks here until a new line arrives over the network, then processes it
                while ((line = reader.readLine()) != null) {
                    if (debugEnabled) {
                        LOGGER.info("AI backend SSE raw line: {}", line);
                    }
                    
                    // SSE protocol dictates data lines begin with "data: "
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6).trim(); // Strips the "data: "
                        
                        // Ignore keep-alive pings and stop as soon as the backend signals completion.
                        if (jsonData.isEmpty()) {
                            continue;
                        }
                        if ("[DONE]".equals(jsonData)) {
                            if (debugEnabled) {
                                LOGGER.info("AI backend SSE completed with [DONE]");
                            }
                            break;
                        }

                        // Parse the JSON, figure what type of message it is
                        try {
                            JsonObject eventObj = JsonParser.parseString(jsonData).getAsJsonObject();
                            String type = eventObj.has("type") ? eventObj.get("type").getAsString() : "";
                            
                            // Isolate the actual content
                            if (eventObj.has("content")) {
                                String content = eventObj.get("content").getAsString();
                                
                                // Route the token to the UI using the Consumer callback
                                if (type.equals("status") || type.equals("thinking")) {
                                    // Format metadata in italics for the UI
                                    eventHandler.accept("\n_" + content + "_\n");
                                } else if (type.equals("final")) {
                                    // Push the raw LLM token directly to the screen
                                    eventHandler.accept(content); 
                                } else if (type.equals("error")) {
                                    throw new AIBackendException("Backend Streaming Error: " + content);
                                } else if (debugEnabled) {
                                    LOGGER.info("AI backend SSE unhandled event type '{}' with content: {}", type, content);
                                }
                            } else if (debugEnabled) {
                                LOGGER.info("AI backend SSE event without content: {}", jsonData);
                            }
                        } catch (JsonParseException parseException) {
                            if (debugEnabled) {
                                LOGGER.warn("AI backend SSE payload was not valid JSON: {}", jsonData, parseException);
                                throw new AIBackendException("Backend returned non-JSON SSE payload: " + truncate(jsonData), parseException);
                            }
                            throw parseException;
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new AIBackendException("Failed to stream chat response: " + e.getMessage(), e);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 2000) {
            return normalized;
        }
        return normalized.substring(0, 2000) + "...";
    }
}
