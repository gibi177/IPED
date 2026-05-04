package iped.app.ui.ai;

import iped.app.ui.ai.backend.AIBackendService;
import iped.app.ui.ai.backend.AIInitMultiChatRequest;
import iped.app.ui.ai.backend.AIStreamChatRequest;
import iped.app.ui.ai.util.AIWhatsappChatExtractor;
import iped.app.ui.ai.util.AIPayloadFactory;
import iped.app.ui.ai.model.ContextFileEntry;
import iped.app.ui.ai.context.AIContextManager;
import iped.data.IItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The central orchestrator that coordinates the flow of data between the UI, 
 * the file extraction utilities, and the AI backend service.
 * <p>
 * This class isolates the complexity of threading, session caching, and error handling 
 * from the presentation layer. 
 * </p>
 */
public class AIChatCoordinator {

    private final AIBackendService backendService;
    private final AIWhatsappChatExtractor extractor;
    
    // Track lists to support both single and multi-chat
    private List<String> currentChatHashes = new ArrayList<>();
    private List<Integer> currentContextItemIds = new ArrayList<>();

    private final List<AIStreamChatRequest.AIMessage> chatHistory = new ArrayList<>();

    /**
     * Constructs a new coordinator.
     * * @param backendService The backend client injected 
     * for handling actual AI communication.
     */
    public AIChatCoordinator(AIBackendService backendService) {
        this.backendService = backendService;
        this.extractor = new AIWhatsappChatExtractor();
    }

    /**
     * Executes the complete AI request pipeline: validates the selected file, 
     * uploads the context (if necessary), and streams the AI's response.
     * @param question   The text prompt from the user
     * @param uiCallback A callback used to push status updates and streamed text back to the UI
     * @param onComplete A callback triggered when the entire process finishes (success or fail),
     * typically used to re-enable UI buttons
     * @param onError    A callback used to push error messages back to the UI
     */
    public void askQuestion(String question, Consumer<String> uiCallback, Runnable onComplete, Consumer<String> onError) {
        
        // Fetch only valid entries from the Context Manager
        List<ContextFileEntry> validEntries = AIContextManager.getInstance().getContextEntriesForUI()
                .stream()
                .filter(ContextFileEntry::isValidForContext)
                .collect(Collectors.toList());

        if (validEntries.isEmpty()) {
            onError.accept("Please, add at least one valid file to context before asking.");
            return;
        }

        // Check if the context changed since the last question
        List<Integer> newContextIds = validEntries.stream()
                .map(e -> e.getItem().getId())
                .collect(Collectors.toList());
        boolean contextChanged = !newContextIds.equals(currentContextItemIds);

        // Offload heavy lifting to a background thread
        new Thread(() -> {
            try {
                // Step A: Initialize the Chat (Only if context changed)
                if (contextChanged) {
                    uiCallback.accept("**[System]:** Initializing context...\n\n");
                    chatHistory.clear();
                    currentChatHashes.clear();
                    
                    if (validEntries.size() == 1) {
                        // Single chat
                        IItem item = validEntries.get(0).getItem();
                        String html = extractor.extractHtml(item);
                        String hash = backendService.initChat(html);
                        currentChatHashes.add(hash);
                    } else {
                        // Multi chat
                        AIInitMultiChatRequest request = AIPayloadFactory.buildMultiChatRequest(validEntries);
                        List<String> hashes = backendService.initMultiChat(request);
                        currentChatHashes.addAll(hashes);
                    }
                    
                    // Update cache state
                    currentContextItemIds = newContextIds; 
                }

                // Step B: Stream the response
                StringBuilder fullResponse = new StringBuilder(); 

                // Route to the correct streaming endpoint
                if (validEntries.size() == 1) {
                    // Single chat stream
                    backendService.streamChatResponse(currentChatHashes.get(0), question, chatHistory, token -> {
                        uiCallback.accept(token);
                        fullResponse.append(token);
                    });
                } else {
                    // Multi chat stream
                    backendService.streamMultiChatResponse(currentChatHashes, question, chatHistory, token -> {
                        uiCallback.accept(token);
                        fullResponse.append(token);
                    });
                }

                uiCallback.accept("\n\n");

                // Step C: Save the turn to history
                chatHistory.add(new AIStreamChatRequest.AIMessage("user", question));
                chatHistory.add(new AIStreamChatRequest.AIMessage("assistant", fullResponse.toString()));

            } catch (Exception e) {
                onError.accept("Backend error: " + e.getMessage());
                // Invalidate the cache on error so the next attempt tries a fresh upload
                currentContextItemIds.clear();
                currentChatHashes.clear();
            } finally {
                onComplete.run(); 
            }
        }).start();
    }
<<<<<<< last-changes

    /**
     * Removes italicized thinking tags before saving to LLM history.
     */
    private String cleanThinkingTags(String rawResponse) {
        return rawResponse.replaceAll("(?m)^_.*_$\\n?", "").trim();
    }

    public void clearHistory() {
        this.chatHistory.clear();

        // Also clear chat currentChatHashes and currentContextItemIds
        currentChatHashes.clear();
        currentContextItemIds.clear();
    }
=======
>>>>>>> pr-2646
}