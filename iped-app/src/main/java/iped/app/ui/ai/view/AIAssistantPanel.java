package iped.app.ui.ai.view;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

import iped.app.ui.ai.AIChatCoordinator;
import iped.app.ui.ai.model.AIChatMessage;
import iped.app.ui.ai.model.ContextFileEntry;
import iped.app.ui.ai.context.AIContextManager;
import iped.app.ui.ai.context.ContextChangeEvent;
import iped.app.ui.ai.context.ContextChangeListener;
import iped.app.ui.ai.backend.AIBackendClient;
import iped.app.ui.ai.backend.AIBackendConfig;

import iped.app.ui.App;
import iped.app.ui.Messages;
import iped.app.ui.FileProcessor;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;

/**
 * AI Assistant floating panel UI layer for IPED.
 * <p>
 * This class acts as the "View" in our architecture. It is a Singleton that 
 * provides a floating panel containing the chat display, file context management, 
 * and progress indicators. It is entirely decoupled from the extraction and 
 * network logic, communicating only through the AIChatCoordinator.
 * </p>
 */
public class AIAssistantPanel {

    // UI positioning constants
    private static final int HORIZONTAL_OFFSET = 30;
    private static final int VERTICAL_OFFSET = 120;
    private static final double HEIGHT_PERCENTAGE = 0.8;
    private static final int PANEL_WIDTH = 750;
    private static final int STREAM_APPEND_DELAY_MS = 30;
    private static final int AUTO_SCROLL_BOTTOM_THRESHOLD_PX = 24;
    private static final int CONTEXT_VISIBLE_ITEMS = 5;
    private static final int CONTEXT_REMOVE_HOTZONE_PX = 28;
    private static final Pattern STREAM_PART_PATTERN = Pattern.compile("\\S+|\\s+");

    // Main UI components
    private JFrame frame; // main window
    private JTextPane chatArea; 
    private JScrollPane chatScrollPane;
    private StyledDocument chatDocument;
    private JTextArea inputArea;
    private JButton sendButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    private AIMarkdownRenderer markdownRenderer;
    private final List<AIChatMessage> finalizedMessages = new ArrayList<>();
    private AIChatMessage draftMessage;
    private final List<String> streamQueue = new ArrayList<>();
    private Timer streamTimer;
    private AIChatMessage streamingMessage;
    private Runnable streamDrainAction;

    // Sidebar components
    private JSplitPane splitPane;
    private JPanel sidebarPanel;
    private JButton newChatButton;

    // Service layer that handles business logic and threading
    private AIChatCoordinator coordinator;

    // Context-related UI components
    private JPanel contextPanel;
    private JList<Object> contextList;
    private DefaultListModel<Object> contextListModel;
    private JLabel contextEmptyLabel;
    private JButton clearContextButton;
    private TitledBorder contextBorder;

    private static final class ContextSummaryRow {
        private final String text;

        private ContextSummaryRow(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }


    // Singleton instance ensures only one floating panel exists at a time
    private static AIAssistantPanel instance;

    public static AIAssistantPanel getInstance() {
        if (instance == null) {
            instance = new AIAssistantPanel();
        }
        return instance;
    }

    /**
     * Private constructor: This is the application's wiring hub.
     */
    private AIAssistantPanel() {
        createUI();

        // Subscribe to the State Manager.
        // We use the Observer pattern to passively listen for context changes.
        AIContextManager.getInstance().addContextChangeListener(new ContextChangeListener() {
            @Override
            public void contextChanged(ContextChangeEvent event) {
                refreshContextUI();
            }
        });
    }

    private void createUI() {
        String title = "AI Assistant";
        try { title = Messages.getString("AIAssistant.Title"); } catch (Exception e) {}

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setResizable(true);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // Chat Workspace
        JPanel chatWorkspacePanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(createContextSection(), BorderLayout.NORTH);

        // Chat display area setup
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(0xf5, 0xf5, 0xf5));
        chatArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        chatDocument = new DefaultStyledDocument();
        chatArea.setDocument(chatDocument);
        try {
            markdownRenderer = new AIMarkdownRenderer(chatArea);
            chatDocument = markdownRenderer.getDocument();
            installTokenClickHandler();
        } catch (Throwable t) {
            System.err.println("Failed to initialize markdown renderer: " + t.getMessage());
            t.printStackTrace();
            markdownRenderer = null;
        }
        refreshChatArea();
        
        chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setPreferredSize(new Dimension(PANEL_WIDTH, 400));
        centerPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel tasksPanel = createTasksPanel();
        centerPanel.add(tasksPanel, BorderLayout.EAST);

        chatWorkspacePanel.add(centerPanel, BorderLayout.CENTER);
        chatWorkspacePanel.add(createBottomPanel(), BorderLayout.SOUTH);

        // Conversations Sidebar
        sidebarPanel = createSidebarPanel();

        // The SplitPane connecting them
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, chatWorkspacePanel);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(5);
        splitPane.setBorder(null); // Keep it clean
        splitPane.setDividerLocation(220); // Default sidebar width

        mainPanel.add(splitPane, BorderLayout.CENTER);

        frame.getContentPane().add(mainPanel);
        frame.pack();
        positionDialog();

        addMessage("System", "AI Assistant ready. Connected to local Backend server.\nRight-click an HTML WhatsApp chat export to add it to the context, then type your question.");
    }

    private void installTokenClickHandler() {
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                int offset = chatArea.viewToModel2D(e.getPoint());
                if (offset < 0 || chatDocument == null) {
                    return;
                }

                javax.swing.text.Element element = chatDocument.getCharacterElement(offset);
                javax.swing.text.AttributeSet attributes = element.getAttributes();
                Object tokenFlag = attributes.getAttribute(AIMarkdownRenderer.TOKEN_ATTRIBUTE);
                if (!Boolean.TRUE.equals(tokenFlag)) {
                    return;
                }

                int start = element.getStartOffset();
                int end = element.getEndOffset();
                chatArea.setSelectionStart(start);
                chatArea.setSelectionEnd(Math.max(start, end));

                Object hash = attributes.getAttribute(AIMarkdownRenderer.TOKEN_HASH_ATTRIBUTE);
                Object chunkId = attributes.getAttribute(AIMarkdownRenderer.TOKEN_CHUNK_ID_ATTRIBUTE);
                navigateToItem(String.valueOf(hash), String.valueOf(chunkId));
            }
        });
    }

    private void navigateToItem(String hash, String chunkId) {
        if (hash == null || hash.isEmpty() || App.get() == null || App.get().appCase == null) {
            return;
        }

        new Thread(() -> {
            try {
                IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, "hash:" + hash);
                MultiSearchResult result = searcher.multiSearch();
                if (result == null || result.getLength() == 0) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                            "Item not found for hash: " + hash, "Not found", JOptionPane.INFORMATION_MESSAGE));
                    return;
                }

                IItemId itemId = result.getItem(0);
                int luceneId = App.get().appCase.getLuceneId(itemId);
                
                // Prepare viewer to scroll to the specific message/chunk position
                // chunkId corresponds to parentViewPosition in chat metadata
                if (chunkId != null && !chunkId.isEmpty()) {
                    try {
                        App.get().getViewerController().getHtmlLinkViewer().setElementIDToScroll(chunkId);
                    } catch (Exception e) {
                        // Viewer might not be HTML or element not available; continue anyway
                    }
                }
                
                FileProcessor fp = new FileProcessor(luceneId, true);
                fp.execute();
                
                // After opening the file, select it in the results table
                SwingUtilities.invokeLater(() -> selectItemInResultsTable(luceneId));

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                        "Error opening item: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }
    
    private void selectItemInResultsTable(int luceneId) {
        if (App.get() == null || App.get().getResults() == null || App.get().getResultsTable() == null) {
            return;
        }
        
        // Search for the item with the given luceneId in the results
        for (int i = 0; i < App.get().getResults().getLength(); i++) {
            try {
                IItemId item = App.get().getResults().getItem(i);
                if (App.get().appCase.getLuceneId(item) == luceneId) {
                    // Found it! Select this row in the results table
                    int viewIndex = App.get().getResultsTable().convertRowIndexToView(i);
                    App.get().getResultsTable().setRowSelectionInterval(viewIndex, viewIndex);
                    
                    // Scroll to make it visible
                    java.awt.Rectangle cellRect = App.get().getResultsTable().getCellRect(viewIndex, 0, false);
                    App.get().getResultsTable().scrollRectToVisible(cellRect);
                    break;
                }
            } catch (Exception e) {
                // Continue searching if there's an error with this item
            }
        }
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());

        // Toggle Sidebar Button
        JButton toggleSidebarBtn = new JButton("☰");
        toggleSidebarBtn.setMargin(new Insets(2, 6, 2, 6));
        toggleSidebarBtn.setFocusPainted(false);
        toggleSidebarBtn.setToolTipText("Toggle Sidebar");
        toggleSidebarBtn.addActionListener(e -> toggleSidebar());

        String titleText = "AI Assistant";
        try { titleText = Messages.getString("AIAssistant.Title"); } catch (Exception e) {}
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        statusLabel = new JLabel("● Connected to local backend server");
        statusLabel.setForeground(new Color(0, 150, 0)); // Green for active

        // Group toggle button and title
        JPanel titleArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleArea.add(toggleSidebarBtn);
        titleArea.add(titleLabel);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));
        leftPanel.add(titleArea, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        return headerPanel;
    }

    private JPanel createSidebarPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setMinimumSize(new Dimension(150, 0)); // Prevent crushing it too small
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5)); 

        newChatButton = new JButton("+ New Chat");
        newChatButton.setFont(newChatButton.getFont().deriveFont(Font.BOLD));
        newChatButton.addActionListener(e -> startNewChat());
        
        panel.add(newChatButton, BorderLayout.NORTH);

        // Placeholder for future step: JList of past conversations
        JLabel placeholder = new JLabel("Chat history loading...", SwingConstants.CENTER);
        placeholder.setForeground(Color.GRAY);
        panel.add(placeholder, BorderLayout.CENTER);

        return panel;
    }

    private void toggleSidebar() {
        if (sidebarPanel.isVisible()) {
            sidebarPanel.setVisible(false);
            splitPane.setDividerLocation(0);
            splitPane.setDividerSize(0);
        } else {
            sidebarPanel.setVisible(true);
            splitPane.setDividerSize(5);
            splitPane.setDividerLocation(220);
        }
    }

    private void startNewChat() {
        // Clear UI and Coordinator memory
        clearChatHistory();
        
        // Clear IPED context
        AIContextManager.getInstance().clearContext();
        
        // In future step, ask ConversationManager to create a new active Conversation here
        
        addMessage("System", "Started a new conversation session.");
        inputArea.requestFocusInWindow();
    }

    private JPanel createContextSection() {
        contextListModel = new DefaultListModel<>();
        contextList = new JList<>(contextListModel);
        contextList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contextList.setVisibleRowCount(CONTEXT_VISIBLE_ITEMS);
        contextList.setBackground(new Color(255, 255, 240));

        contextList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            if (value instanceof ContextSummaryRow) {
                JLabel label = (JLabel) new DefaultListCellRenderer()
                        .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                ContextSummaryRow summary = (ContextSummaryRow) value;
                label.setText(summary.toString());
                label.setForeground(Color.DARK_GRAY);
                label.setFont(label.getFont().deriveFont(Font.ITALIC));
                label.setToolTipText(summary.toString());
                return label;
            }

            if (value instanceof ContextFileEntry) {
                return createContextEntryCell(list, (ContextFileEntry) value, isSelected);
            }

            JLabel label = (JLabel) new DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setText(String.valueOf(value));
            return label;
        });

        contextList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                int index = contextList.locationToIndex(e.getPoint());
                if (index < 0 || index >= contextListModel.size()) {
                    return;
                }

                Rectangle cellBounds = contextList.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) {
                    return;
                }

                Object value = contextListModel.getElementAt(index);
                if (!(value instanceof ContextFileEntry)) {
                    return;
                }

                if (!isRemoveHotzoneClick(e, cellBounds)) {
                    return;
                }

                AIContextManager.getInstance().removeContextFile(((ContextFileEntry) value).getItem());
            }
        });

        JScrollPane contextScroll = new JScrollPane(contextList);
        contextScroll.setPreferredSize(new Dimension(PANEL_WIDTH - 10, 80));

        contextEmptyLabel = new JLabel("No files added to context.");
        contextEmptyLabel.setForeground(Color.GRAY);
        contextEmptyLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        contextEmptyLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel listContainer = new JPanel(new BorderLayout());
        listContainer.add(contextScroll, BorderLayout.CENTER);
        listContainer.add(contextEmptyLabel, BorderLayout.NORTH);

        clearContextButton = new JButton("Clear");
        clearContextButton.setMargin(new Insets(0, 5, 0, 5));
        clearContextButton.setEnabled(false);
        clearContextButton.addActionListener(e -> AIContextManager.getInstance().clearContext());

        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.add(clearContextButton, BorderLayout.NORTH);

        contextPanel = new JPanel(new BorderLayout(5, 5));
        contextBorder = BorderFactory.createTitledBorder("Added Context (0 files)");
        contextPanel.setBorder(contextBorder);

        contextPanel.add(listContainer, BorderLayout.CENTER);
        contextPanel.add(actionPanel, BorderLayout.EAST);

        refreshContextUI();

        return contextPanel;
    }

    /**
     * Destructive refresh: Wipes the UI list and rebuilds it from the Source of Truth.
     */
    private void refreshContextUI() {
        List<ContextFileEntry> entries = AIContextManager.getInstance().getContextEntriesForUI();
        List<IItem> validFiles = AIContextManager.getInstance().getContextFiles();
        int invalidCount = entries.size() - validFiles.size();
        contextListModel.clear();

        if (entries.isEmpty()) {
            contextEmptyLabel.setVisible(true);
            contextList.setVisible(false);
            clearContextButton.setEnabled(false);
        } else {
            contextEmptyLabel.setVisible(false);
            contextList.setVisible(true);
            clearContextButton.setEnabled(true);

            int visibleCount = Math.min(CONTEXT_VISIBLE_ITEMS, entries.size());
            for (int i = 0; i < visibleCount; i++) {
                contextListModel.addElement(entries.get(i));
            }

            if (entries.size() > CONTEXT_VISIBLE_ITEMS) {
                int hiddenCount = entries.size() - CONTEXT_VISIBLE_ITEMS;
                String summaryText = "+ " + hiddenCount + " more items (" + validFiles.size() + " valid, " + invalidCount + " rejected)";
                contextListModel.addElement(new ContextSummaryRow(summaryText));
            }
        }

        if (invalidCount > 0) {
            contextBorder.setTitle("Added Context (" + validFiles.size() + " valid, " + invalidCount + " rejected)");
        } else {
            contextBorder.setTitle("Added Context (" + validFiles.size() + " valid)");
        }

        contextPanel.repaint();
    }

    private JComponent createContextEntryCell(JList<?> list, ContextFileEntry entry, boolean isSelected) {
        JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
        rowPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        rowPanel.setOpaque(true);
        rowPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

        JLabel textLabel = new JLabel();
        textLabel.setOpaque(false);
        textLabel.setFont(list.getFont());
        textLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

        JLabel removeLabel = new JLabel("X");
        removeLabel.setOpaque(false);
        removeLabel.setFont(list.getFont().deriveFont(Font.BOLD));
        removeLabel.setForeground(new Color(160, 0, 0));

        if (entry.isValidForContext()) {
            textLabel.setText(entry.getFileName());
            rowPanel.setToolTipText(entry.getFullPath());
        } else {
            String reason = entry.getValidationReason() != null ? entry.getValidationReason() : "Rejected item.";
            textLabel.setText(entry.getFileName() + " - " + reason);
            rowPanel.setToolTipText(reason + " Path: " + entry.getFullPath());
            textLabel.setForeground(isSelected ? list.getSelectionForeground() : new Color(180, 0, 0));
        }

        rowPanel.add(textLabel, BorderLayout.CENTER);
        rowPanel.add(removeLabel, BorderLayout.EAST);
        return rowPanel;
    }

    private boolean isRemoveHotzoneClick(MouseEvent e, Rectangle cellBounds) {
        return e.getX() >= cellBounds.x + cellBounds.width - CONTEXT_REMOVE_HOTZONE_PX;
    }

    // Quick actions panel
    private JPanel createTasksPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Quick Actions"));

        // Map English button text to Portuguese prompts
        Map<String, String> taskPrompts = new java.util.HashMap<>();
        taskPrompts.put("Summarize", "Resuma o arquivo fornecido.");
        taskPrompts.put("Find Patterns", "Encontre padrões no arquivo fornecido.");
        taskPrompts.put("Analyze Metadata", "Analise os metadados do arquivo fornecido.");

        String[] tasks = {"Summarize", "Find Patterns", "Analyze Metadata"};
        for (String task : tasks) {
            JButton btn = new JButton(task);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(200, 30));
            // Firing a pre-written prompt directly into the input area logic
            btn.addActionListener(e -> {
                inputArea.setText(taskPrompts.get(task));
                handleSendAction();
            });
            panel.add(btn);
            panel.add(Box.createVerticalStrut(5));
        }
        
        // Add separator
        panel.add(Box.createVerticalStrut(10));
        JSeparator separator = new JSeparator();
        separator.setMaximumSize(new Dimension(200, 1));
        panel.add(separator);
        panel.add(Box.createVerticalStrut(5));
        
        // Clear Chat History button
        JButton clearChatButton = new JButton("Clear Chat History");
        clearChatButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        clearChatButton.setMaximumSize(new Dimension(200, 30));
        clearChatButton.addActionListener(e -> clearChatHistory());
        panel.add(clearChatButton);
        
        return panel;
    }
    
    // Action Button to clear the chat history, resetting the conversation and UI to a clean state
    private void clearChatHistory() {
        finalizedMessages.clear();
        draftMessage = null;
        
        // Wipe the Coordinator's memory
        if (coordinator != null) {
            coordinator.clearHistory();
        }
        
        try {
            chatDocument.remove(0, chatDocument.getLength());
        } catch (BadLocationException e) {
            System.err.println("Error clearing chat document: " + e.getMessage());
        }
        
        refreshChatArea();
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        inputArea = new JTextArea(6, 20);
        inputArea.setLineWrap(true);
        inputArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        // Listen for "Enter" key to trigger send, allowing "Shift+Enter" for new lines
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume(); // Prevent adding a newline character
                    handleSendAction();
                }
            }
        });

        String sendText = "Send";
        try { sendText = Messages.getString("AIAssistant.Send"); } catch (Exception e) {}

        sendButton = new JButton(sendText);
        sendButton.addActionListener(e -> handleSendAction());

        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        return bottomPanel;
    } 

    private void refreshChatArea() {
        JScrollBar verticalBar = chatScrollPane != null ? chatScrollPane.getVerticalScrollBar() : null;
        boolean shouldAutoFollow = shouldAutoFollow(verticalBar);
        int previousScrollValue = verticalBar != null ? verticalBar.getValue() : -1;

        if (markdownRenderer != null) {
            markdownRenderer.renderMessages(buildRenderableMessages());
        } else {
            renderMessagesFallback();
        }

        SwingUtilities.invokeLater(() -> {
            if (chatScrollPane == null) {
                return;
            }

            JScrollBar bar = chatScrollPane.getVerticalScrollBar();
            if (shouldAutoFollow) {
                bar.setValue(bar.getMaximum());
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                return;
            }

            if (previousScrollValue >= 0) {
                int maxScroll = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
                bar.setValue(Math.min(previousScrollValue, maxScroll));
            }
        });
    }

    private boolean shouldAutoFollow(JScrollBar verticalBar) {
        if (verticalBar == null) {
            return true;
        }

        int distanceToBottom = verticalBar.getMaximum() - (verticalBar.getValue() + verticalBar.getVisibleAmount());
        return distanceToBottom <= AUTO_SCROLL_BOTTOM_THRESHOLD_PX;
    }

    private void renderMessagesFallback() {
        try {
            chatDocument.remove(0, chatDocument.getLength());
            for (AIChatMessage message : buildRenderableMessages()) {
                chatDocument.insertString(
                    chatDocument.getLength(),
                    "[" + message.getTime() + "] " + message.getSender() + "\n" + message.getContent() + "\n\n",
                    null
                );
            }
        } catch (BadLocationException e) {
            System.err.println("Error rendering fallback chat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean ensureChatServiceInitialized() {
        if (coordinator != null) {
            return true;
        }

        try {
            coordinator = new AIChatCoordinator(new AIBackendClient(AIBackendConfig.loadFromSystemProperties()));
            return true;
        } catch (Throwable t) {
            addMessage("System Error", "Failed to initialize AI backend: " + t.getMessage(), "error");
            System.err.println("Failed to initialize AI backend: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    private void addMessage(String sender, String message, String type) {
        AIChatMessage chatMessage = AIChatMessage.now(sender, message, type);
        finalizedMessages.add(chatMessage);

        if (markdownRenderer != null) {
            appendFinalizedMessage(chatMessage);
        } else {
            refreshChatArea();
        }
    }

    private List<AIChatMessage> buildRenderableMessages() {
        List<AIChatMessage> renderableMessages = new ArrayList<>(finalizedMessages);
        if (draftMessage != null) {
            renderableMessages.add(draftMessage);
        }
        return renderableMessages;
    }

    private void addMessage(String sender, String message) {
        addMessage(sender, message, "system");
    }

    private void positionDialog() {
        Rectangle screenBounds = resolvePreferredScreenBounds();

        int height = (int) (screenBounds.height * HEIGHT_PERCENTAGE);
        frame.setSize(PANEL_WIDTH + 150, height);

        int x = screenBounds.x + screenBounds.width - frame.getWidth() - HORIZONTAL_OFFSET;
        int y = screenBounds.y + VERTICAL_OFFSET;

        if (y + frame.getHeight() > screenBounds.y + screenBounds.height) {
            y = screenBounds.y + screenBounds.height - frame.getHeight();
        }

        frame.setLocation(x, y);
    }

    private Rectangle resolvePreferredScreenBounds() {
        Window owner = App.get();
        if (owner != null) {
            GraphicsConfiguration ownerGc = owner.getGraphicsConfiguration();
            if (ownerGc != null) {
                return ownerGc.getBounds();
            }
        }

        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    }

    private Rectangle getVirtualScreenBounds() {
        Rectangle virtualBounds = new Rectangle();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            virtualBounds = virtualBounds.union(device.getDefaultConfiguration().getBounds());
        }
        return virtualBounds;
    }

    private void ensureVisibleOnScreen() {
        Rectangle virtualBounds = getVirtualScreenBounds();
        Rectangle currentBounds = frame.getBounds();
        if (!virtualBounds.intersects(currentBounds)) {
            positionDialog();
        }
    }

    private void showDialogSafely() {
        ensureVisibleOnScreen();
        if (!frame.isVisible()) {
            frame.setVisible(true);
        }
        frame.toFront();
        frame.requestFocus();
        inputArea.requestFocusInWindow();
    }

    private void beginStreaming(AIChatMessage message) {
        streamingMessage = message;
        streamQueue.clear();
        streamDrainAction = null;
        ensureStreamTimer();
    }

    private void ensureStreamTimer() {
        if (streamTimer != null) {
            return;
        }

        streamTimer = new Timer(STREAM_APPEND_DELAY_MS, e -> onStreamTimerTick());
    }

    private void enqueueStreamToken(String token) {
        if (streamingMessage == null || token == null || token.isEmpty()) {
            return;
        }

        Matcher matcher = STREAM_PART_PATTERN.matcher(token);
        while (matcher.find()) {
            streamQueue.add(matcher.group());
        }

        if (!streamQueue.isEmpty() && !streamTimer.isRunning()) {
            streamTimer.start();
        }
    }

    private void onStreamTimerTick() {
        if (streamingMessage == null) {
            streamTimer.stop();
            return;
        }

        if (streamQueue.isEmpty()) {
            streamTimer.stop();
            runPendingDrainAction();
            return;
        }

        String part = streamQueue.remove(0);
        streamingMessage.appendContent(part);
        renderDraftMessage();
    }

    private void completeStreaming(Runnable onDrained) {
        if (streamQueue.isEmpty() && (streamTimer == null || !streamTimer.isRunning())) {
            onDrained.run();
            resetStreamingState();
        } else {
            streamDrainAction = () -> {
                onDrained.run();
                resetStreamingState();
            };
        }
    }

    private void runPendingDrainAction() {
        if (streamDrainAction == null) {
            return;
        }

        Runnable action = streamDrainAction;
        streamDrainAction = null;
        action.run();
    }

    private void resetStreamingState() {
        if (streamTimer != null) {
            streamTimer.stop();
        }
        streamQueue.clear();
        streamDrainAction = null;
        streamingMessage = null;
    }

    /**
     * The main execution block linking user intent to the background Coordinator.
     */
    private void handleSendAction() {
        String text = inputArea.getText().trim();
        if (!text.isEmpty()) {
            if (!ensureChatServiceInitialized()) {
                return;
            }

            // Print user message immediately
            addMessage("You", text, "user");
            inputArea.setText("");
            
            // Lock the UI
            setProcessing(true);
            
            AIChatMessage assistantDraft = AIChatMessage.now("Assistant", "", "assistant");
            draftMessage = assistantDraft;
            beginStreaming(assistantDraft);
            renderDraftMessage();
            
            // Call the service
            coordinator.askQuestion(
                text, 
                // Callback 1: Append tokens to the live assistant draft
                (token) -> javax.swing.SwingUtilities.invokeLater(() -> {
                    enqueueStreamToken(token);
                }),
                // Callback 2: Keep the completed draft visible and unlock the UI
                () -> javax.swing.SwingUtilities.invokeLater(() -> {
                    completeStreaming(() -> {
                        if (assistantDraft.getContent().isEmpty()) {
                            if (markdownRenderer != null) {
                                markdownRenderer.discardDraft();
                            }
                            draftMessage = null;
                        } else {
                            finalizedMessages.add(assistantDraft);
                            if (markdownRenderer != null) {
                                markdownRenderer.commitDraft();
                            }
                            draftMessage = null;
                        }
                        setProcessing(false);
                    });
                }),
                // Callback 3: Handle Errors
                (errorMessage) -> javax.swing.SwingUtilities.invokeLater(() -> {
                    if (markdownRenderer != null) {
                        markdownRenderer.discardDraft();
                    }
                    resetStreamingState();
                    draftMessage = null;
                    addMessage("System Error", errorMessage, "error");
                    setProcessing(false);
                })
            );
        }
    }

    /**
     * Locks or unlocks the input fields and displays the loading bar.
     */
    private void setProcessing(boolean processing) {
        progressBar.setVisible(processing);
        sendButton.setEnabled(!processing);
        inputArea.setEnabled(!processing);
        frame.setCursor(processing ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void appendFinalizedMessage(AIChatMessage message) {
        if (chatScrollPane == null) {
            refreshChatArea();
            return;
        }

        JScrollBar verticalBar = chatScrollPane.getVerticalScrollBar();
        boolean shouldAutoFollow = shouldAutoFollow(verticalBar);
        int previousScrollValue = verticalBar != null ? verticalBar.getValue() : -1;

        markdownRenderer.appendMessage(message);
        restoreScrollAfterIncrementalUpdate(shouldAutoFollow, previousScrollValue);
    }

    private void renderDraftMessage() {
        if (draftMessage == null) {
            return;
        }

        if (chatScrollPane == null || markdownRenderer == null) {
            refreshChatArea();
            return;
        }

        JScrollBar verticalBar = chatScrollPane.getVerticalScrollBar();
        boolean shouldAutoFollow = shouldAutoFollow(verticalBar);
        int previousScrollValue = verticalBar != null ? verticalBar.getValue() : -1;

        markdownRenderer.renderDraft(draftMessage);
        restoreScrollAfterIncrementalUpdate(shouldAutoFollow, previousScrollValue);
    }

    private void restoreScrollAfterIncrementalUpdate(boolean shouldAutoFollow, int previousScrollValue) {
        if (chatScrollPane == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (chatScrollPane == null) {
                return;
            }

            JScrollBar bar = chatScrollPane.getVerticalScrollBar();
            if (shouldAutoFollow) {
                bar.setValue(bar.getMaximum());
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                return;
            }

            if (previousScrollValue >= 0) {
                int maxScroll = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
                bar.setValue(Math.min(previousScrollValue, maxScroll));
            }
        });
    }

    public void toggleVisibility() {
        Runnable action = () -> {
            if (frame.isVisible()) {
                frame.setVisible(false);
            } else {
                showDialogSafely();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    public void showPanel() {
        Runnable action = this::showDialogSafely;
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
