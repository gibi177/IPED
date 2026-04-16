package iped.app.ui.ai;

import iped.app.ui.ai.backend.AIBackendException;
import iped.app.ui.ai.backend.AIBackendService;
import iped.app.ui.ai.backend.AIStreamChatRequest;
import iped.data.IItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The central orchestrator that coordinates the flow of data between the UI, 
 * the file extraction utilities, and the AI backend service.
 * <p>
 * This class isolates the complexity of threading, session caching, and error handling 
 * from the presentation layer. It ensures that heavy operations (like extracting files 
 * and making network calls) are safely routed off the main UI thread.
 * </p>
 */
public class AIChatCoordinator {

    private final AIBackendService backendService;
    private final AIWhatsappChatExtractor extractor;
    
    // Cache the hash so we don't re-upload the same chat every time
    private String currentChatHash = null;
    private IItem currentContextItem = null;

    private final List<AIStreamChatRequest.AIMessage> chatHistory = new ArrayList<>();

    /**
     * Constructs a new coordinator.
     * * @param backendService The backend client (can be a Mock or HTTP client) injected 
     * for handling actual AI communication.
     */
    public AIChatCoordinator(AIBackendService backendService) {
        this.backendService = backendService;
        this.extractor = new AIWhatsappChatExtractor();
    }

    /**
     * Removes tags from llm response to safely attach to chatHistory
     * 
     * @param rawResponse The llm response received 
     */
    public String cleanThinkingTags(String rawResponse) {
        return rawResponse.replaceAll("(?m)^_.*_$\\n?", "").trim();
    }

    /**
     * Executes the complete AI request pipeline: validates the selected file, 
     * uploads the context (if necessary), and streams the AI's response.
     * @param question   The text prompt from the user.
     * @param uiCallback A callback used to push status updates and streamed text back to the UI.
     * @param onComplete A callback triggered when the entire process finishes (success or fail),
     * typically used to re-enable UI buttons.
     * @param onError    A callback used to push error messages back to the UI.
     */
    public void askQuestion(String question, Consumer<String> uiCallback, Runnable onComplete, Consumer<String> onError) {
        // Validate Context 
        List<IItem> contextFiles = AIContextManager.getInstance().getContextFiles();
        
        if (contextFiles.isEmpty()) {
            onError.accept("Please, add a file to context before asking.");
            return;
        }
        if (contextFiles.size() > 1) {
            onError.accept("Only one file is currently supported, remove the extra ones.");
            return;
        }

        IItem item = contextFiles.get(0);

        // Offload heavy lifting to a background thread
        new Thread(() -> {
            try {
                // Step A: Extract and Initialize Chat 
                // If the user uploads a new chat to context, item will refer to
                // the new chat and currentContextItem will refer to the last chat
                // then, the condition is fulfilled and a new chat is initialized 
                if (currentChatHash == null || !item.equals(currentContextItem)) {
                    uiCallback.accept("[System]: Extracting and sending file...\n\n");
                    String html = extractor.extractHtml(item);
                    currentChatHash = backendService.initChat(html);
                    currentContextItem = item; // Update cache
                    chatHistory.clear();
                }

                // Step B: Stream the response
                StringBuilder fullResponse = new StringBuilder(); 
                uiCallback.accept("[Assistant]: ");
                
                // Use a lambda to intercept the tokens
                backendService.streamChatResponse(currentChatHash, question, chatHistory, token -> {
                    // Send to the screen
                    uiCallback.accept(token); 

                    // Accumulate in memory
                    fullResponse.append(token); 
                });

                uiCallback.accept("\n\n");
                String finalAnswer = cleanThinkingTags(fullResponse.toString());
                chatHistory.add(new AIStreamChatRequest.AIMessage("user", question));
                chatHistory.add(new AIStreamChatRequest.AIMessage("assistant", finalAnswer));

            } catch (Exception e) {
                onError.accept("backend error: " + e.getMessage());
                // Invalidate the cache on error so the next attempt tries a fresh upload
                currentChatHash = null; 
            } finally {
                // Step C: unlock the UI
                onComplete.run(); 
            }
        }).start();
    }
}