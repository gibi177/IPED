package iped.app.ui.ai.context;

import iped.app.ui.ai.model.AIChatMessage;
import iped.app.ui.ai.model.Conversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton manager responsible for maintaining the state of AI conversations 
 * for the currently active IPED case.
 */
public class ConversationManager {

    private static ConversationManager instance;
    
    private final List<Conversation> conversations;
    private Conversation activeConversation;

    private ConversationManager() {
        this.conversations = new ArrayList<>();
        startNewConversation();
    }

    public static synchronized ConversationManager getInstance() {
        if (instance == null) {
            instance = new ConversationManager();
        }
        return instance;
    }

    public Conversation getActiveConversation() {
        return activeConversation;
    }

    /**
     * Sets a specific conversation as active (used when clicking a chat in the sidebar).
     */
    public void setActiveConversation(Conversation conversation) {
        this.activeConversation = conversation;
        // Safeguard: don't add null to the list if the active state is wiped
        if (conversation != null && !conversations.contains(conversation)) {
            conversations.add(0, conversation);
        }
    }

    /**
     * Initializes a fresh, empty conversation and sets it as active.
     */
    public Conversation startNewConversation() {
        Conversation newConv = new Conversation();
        setActiveConversation(newConv);
        return newConv;
    }

    public List<Conversation> getConversations() {
        return Collections.unmodifiableList(conversations);
    }

    public void removeConversation(Conversation conversation) {
        conversations.remove(conversation);
    }

    /**
     * Appends a message to the active conversation and auto-generates a title if needed.
     */
    public void addMessageToActive(AIChatMessage message) {
        if (activeConversation != null) {
            activeConversation.getMessages().add(message);
            activeConversation.updateLastModified();
            
            // If this is the first user message, generate the title!
            if ("New Conversation".equals(activeConversation.getTitle()) && "user".equals(message.getType())) {
                activeConversation.autoGenerateTitle();
            }
        }
    }
}
