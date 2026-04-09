package iped.app.ui.ai.backend;

import java.util.function.Consumer;

/**
 * The concrete implementation of the {@link AIBackendService} that handles HTTP 
 * communication with the AI backend.
 * <p>
 * This class is responsible for taking the application's requests, formatting them 
 * into the appropriate network payloads, executing the HTTP calls using 
 * the provided {@link AIBackendConfig}, and parsing the responses back to the application.
 * </p>
 */
public class AIBackendClient implements AIBackendService {
    
    private final AIBackendConfig config;

    /**
     * Constructs a new AIBackendClient with the specified configuration.
     * @param config The AI backend configuration (must not be null).
     */
    public AIBackendClient(AIBackendConfig config) {
        this.config = config;
    }

    @Override
    public String initChat(String chatHtml) throws AIBackendException {
        // TODO: Implement later (step 4)
        throw new UnsupportedOperationException("Backend HTTP calls are not yet implemented.");
    }

    @Override
    public void streamChatResponse(String chatHash, String question, Consumer<String> eventHandler) throws AIBackendException {
        // TODO: Implement later (step 4)
        throw new UnsupportedOperationException("Backend HTTP calls are not yet implemented.");
    }
}
