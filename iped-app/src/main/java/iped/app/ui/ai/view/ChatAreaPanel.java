package iped.app.ui.ai.view;

import java.awt.Dimension;
import java.util.List;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

import iped.app.ui.ai.model.AIChatMessage;

public class ChatAreaPanel extends JPanel {
    private final JTextPane chatArea; 
    private final JScrollPane chatScrollPane;
    private StyledDocument chatDocument;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    private final AIMarkdownRenderer markdownRenderer;
    private final ChatStreamAnimator streamAnimator;
    private AIChatMessage currentDraftMessage;

    public ChatAreaPanel(int panelWidth, String sendText) {
        setLayout(new BorderLayout(5, 5));
        
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        this.markdownRenderer = new AIMarkdownRenderer(chatArea);
        this.chatDocument = markdownRenderer.getDocument();
        
        this.streamAnimator = new ChatStreamAnimator(() -> renderActiveDraft());

        chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setPreferredSize(new Dimension(panelWidth, 400));

        inputArea = new JTextArea(6, 20);
        inputArea.setLineWrap(true);
        inputArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        sendButton = new JButton(sendText);

        statusLabel = new JLabel("● Connected to local backend server");
        statusLabel.setForeground(new Color(0, 150, 0)); //green
        
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
    
        JPanel bottomContainer = new JPanel(new BorderLayout(5, 5));
        bottomContainer.add(progressBar, BorderLayout.NORTH);
        bottomContainer.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        bottomContainer.add(sendButton, BorderLayout.EAST);
        
        add(chatScrollPane, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    public void startMessageStreaming(AIChatMessage assistantDraft) {
        this.currentDraftMessage = assistantDraft;
        streamAnimator.beginStreaming(assistantDraft);
        renderActiveDraft();
    }

    public void enqueueStreamingToken(String token) {
        streamAnimator.enqueueToken(token);
    }

    public void pruneStreaming(Runnable onDrained) {
        streamAnimator.completeStreaming(() -> {
            if (currentDraftMessage != null) {
                if (currentDraftMessage.getContent().isEmpty()) {
                    markdownRenderer.discardDraft();
                } else {
                    markdownRenderer.commitDraft();
                }
            }
            currentDraftMessage = null;
            onDrained.run();
        });
    }

    public void forceDiscardStreaming() {
        streamAnimator.resetState();
        markdownRenderer.discardDraft();
        currentDraftMessage = null;
    }

    private void renderActiveDraft() {
        if (currentDraftMessage != null) {
            markdownRenderer.renderDraft(currentDraftMessage);
            adjustScrollToBottom();
        }
    }

    public void renderHistoricalMessages(List<AIChatMessage> messages) {
        markdownRenderer.renderMessages(messages);
        adjustScrollToBottom();
    }

    private void adjustScrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
            chatArea.setCaretPosition(markdownRenderer.getDocument().getLength());
        });
    }

    public JTextPane getChatArea() {
        return chatArea;
    }

    public JScrollPane getChatScrollPane() {
        return chatScrollPane;
    }

    public StyledDocument getChatDocument() {
        return chatDocument;
    }

    public void setChatDocument(StyledDocument chatDocument) {
        this.chatDocument = chatDocument;
    }

    public JTextArea getInputArea() {
        return inputArea;
    }

    public JButton getSendButton() {
        return sendButton;
    }

    public JLabel getStatusLabel() {
        return statusLabel;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public AIMarkdownRenderer getMarkdownRenderer() {
        return markdownRenderer;
    }

    public ChatStreamAnimator getStreamAnimator() {
        return streamAnimator;
    }

    public AIChatMessage getCurrentDraftMessage() {
        return currentDraftMessage;
    }

    public void setCurrentDraftMessage(AIChatMessage currentDraftMessage) {
        this.currentDraftMessage = currentDraftMessage;
    }

    public void clearChatScreen() {
        
        if (streamAnimator != null) {
            streamAnimator.resetState();
        }

        if (markdownRenderer != null) {
            markdownRenderer.commitDraft();
        }

        try {
            if (chatDocument != null) {
                chatDocument.remove(0, chatDocument.getLength());
            }
        } catch (BadLocationException e) {
            System.err.println("Error clearing chat document: " + e.getMessage());
        }

        currentDraftMessage = null;
    }

    public void setProcessing(boolean processing) {
        progressBar.setVisible(processing);
        sendButton.setEnabled(!processing);
        inputArea.setEnabled(!processing);
        setCursor(processing ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) 
                             : Cursor.getDefaultCursor());
    }

    /**
     * Transfere o foco do teclado diretamente para a área de input de texto.
     */
    public void requestFocusToInput() {
        if (inputArea != null) {
            inputArea.requestFocusInWindow();
        }
    }
    
}
