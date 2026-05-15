package iped.app.ui.ai.view;

import java.awt.Dimension;
import java.awt.Color;
import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

public class ChatAreaPanel extends JPanel {
    private final JTextPane chatArea; 
    private final JScrollPane chatScrollPane;
    private StyledDocument chatDocument;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public ChatAreaPanel(int panelWidth, String sendText) {
        setLayout(new BorderLayout(5, 5));
        
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        chatDocument = new DefaultStyledDocument();
        chatArea.setDocument(chatDocument);

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
    
        chatArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
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

    public void clearChatScreen() {
        try {
            if (chatDocument != null) {
                chatDocument.remove(0, chatDocument.getLength());
            }
        } catch (javax.swing.text.BadLocationException e) {
            System.err.println("Error clearing chat document: " + e.getMessage());
        }
    }

    public void setProcessing(boolean processing) {
        progressBar.setVisible(processing);
        sendButton.setEnabled(!processing);
        inputArea.setEnabled(!processing);
        setCursor(processing ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) 
                             : java.awt.Cursor.getDefaultCursor());
    }
    
}
