package iped.app.ui.ai.backend;

import java.util.function.Consumer;
import java.util.List;

/**
 * A mock implementation of the {@link AIBackendService} used for UI testing and local development.
 * <p>
 * This class simulates the latency and token-by-token streaming behavior of a real 
 * Large Language Model (LLM) over a network. It allows the frontend application to be 
 * built, tested, and debugged without requiring a live connection to the backend.
 * </p>
 */
public class AIBackendMockService implements AIBackendService{

    @Override
    public String initChat(String chatHtml) throws AIBackendException{
        // Simulate server processing delay
        try {Thread.sleep(1000); } catch (InterruptedException ignored) {}

        // Return fake hash that represents session ID
        return "mock_hash_12345abcde";
    }

    @Override
    public void streamChatResponse(String chatHash, String question, List<AIStreamChatRequest.AIMessage> history, Consumer<String> eventHandler) throws AIBackendException {
        // Simulate the chunked responses (Server-Sent Events)
        String[] mockTokens = {
            "Based ", "on ", "the ", "provided ", "file, ", "this ", "is ", "a ", "simulated ", "response."
        };

        for (String token : mockTokens) {
            try {
                Thread.sleep(150); // Delay between tokens to simulate typing/streaming
            } catch (InterruptedException ignored) {}
            
            // Push the generated token back to the UI
            eventHandler.accept(token);
        }
    }
}
