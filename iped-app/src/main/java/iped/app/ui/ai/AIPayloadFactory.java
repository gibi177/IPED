package iped.app.ui.ai;

import iped.app.ui.ai.backend.AIInitMultiChatRequest;
import iped.app.ui.ai.backend.AIInitMultiChatRequest.SummarizedChat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Factory utility that translates IPED's internal UI state into 
 * standard Data Transfer Objects (DTOs) for the network layer.
 * <p>
 * This class acts as a boundary layer. It ensures that the network client 
 * never needs to know about IPED-specific classes (like {@link ContextFileEntry}), 
 * and enforces strict validation rules before data is allowed to leave the application
 * </p>
 */
public class AIPayloadFactory {

    /**
     * Builds the Multi-Chat initialization payload by extracting summaries from the UI context.
     * <p>
     * <b>Memory Guardrail:</b> This method aggressively filters out files that do not 
     * have pre-computed summaries. Sending raw HTML for multiple files would instantly 
     * exceed the LLM's token context window and crash the backend.
     * </p>
     * * @param entries The raw list of files currently held in the UI's {@link AIContextManager}.
     * @return A clean, populated DTO ready for Gson serialization.
     * @throws IllegalArgumentException if the provided list yields zero valid summaries, 
     * meaning a Multi-Chat session cannot be legally formed.
     */
    public static AIInitMultiChatRequest buildMultiChatRequest(List<ContextFileEntry> entries) {
        List<SummarizedChat> summarizedChats = new ArrayList<>();

        for (ContextFileEntry entry : entries) {
            // Skip files that failed validation
            if (!entry.isValidForContext()) {
                continue;
            }

            // In Multi-Chat, summaries are strictly required
            // If it doesn't have a summary, skip it to avoid blowing up the LLM context window
            if (!entry.hasSummary()) {
                continue;
            }

            // Map IPED data to backend variables
            String chatId = String.valueOf(entry.getItem().getId());
            String chatName = entry.getFileName();
            
            // Adapter Pattern: The Python backend expects lists for summaries and IDs
            // Since IPED joined them into one block, send a list of size 1
            List<String> summariesList = Collections.singletonList(entry.getSummary());
            List<String> summaryIdsList = Collections.singletonList("summary_1"); 

            // Build the inner DTO, representing a single summarized chat
            SummarizedChat chatDto = new SummarizedChat(chatId, chatName, summariesList, summaryIdsList);
            summarizedChats.add(chatDto);
        }

        if (summarizedChats.isEmpty()) {
            throw new IllegalArgumentException("No valid file with summary was found for multi-chat analysis");
        }

        // Return the final wrapper DTO
        return new AIInitMultiChatRequest(summarizedChats);
    }
}
