package iped.app.ui.ai.backend;

import java.util.List;

/**
 * A Data Transfer Object (DTO) representing the payload sent to query a Multi-Chat session
 * <p>
 * This request is sent to the {@code /api/multichat/stream} endpoint. It combines 
 * multiple chat session IDs, the user's current prompt, and the ongoing conversational 
 * memory into a single JSON structure.
 * </p>
 */
public class AIMultiChatStreamRequest {

    /**
     * {@code chats_hashes} is the correct name in the Python schema, despite the unnecessary 's'.
     * <b>Do not</b> rename this to {@code chat_hashes}
     */
    private final List<String> chats_hashes;
    private final String user_question;
    private final List<AIStreamChatRequest.AIMessage> previousmessages; // Reuse the AIMessage class built for single-chat
    
    /**
     * Constructs a new streaming request for a multi-chat session.
     * @param chatsHashes      The list of session IDs to query against.
     * @param userQuestion     The prompt text from the user.
     * @param previousmessages The accumulated chat history for context.
     */
    public AIMultiChatStreamRequest(List<String> chatsHashes, String userQuestion, List<AIStreamChatRequest.AIMessage> previousmessages) {
        this.chats_hashes = chatsHashes;
        this.user_question = userQuestion;
        this.previousmessages = previousmessages;
    }

    // Public getter methods
    public List<String> getChatsHashes() { return chats_hashes; }
    public String getUserQuestion() { return user_question; }
    public List<AIStreamChatRequest.AIMessage> getPreviousmessages() { return previousmessages; }
}

