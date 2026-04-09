package iped.app.ui.ai.backend;

import java.util.function.Consumer;

public interface AIBackendService {
    
    /**
     * Sends the extracted HTML content to the backend to initialize the chat.
     * @param chatHtml The raw WhatsApp HTML string.
     * @return The MD5 chat_hash returned by the server.
     * @throws AIBackendException if the server rejects the HTML or is unreachable.
     */
    String initChat(String chatHtml) throws AIBackendException;

    /**
     * Queries the LLM regarding a previously initialized chat.
     * @param chatHash The hash returned from initChat.
     * @param question The user's question.
     * @param eventHandler A callback that receives streamed tokens/status updates from the LLM.
     *                     Allows the implementing class to push tokens to the UI as soon as they
     *                     arrive over the network.
     * @throws AIBackendException if the connection fails.
     */
    void streamChatResponse(String chatHash, String question, Consumer<String> eventHandler) throws AIBackendException;
}