package iped.app.ui.ai.view;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.StyledDocument;

import iped.app.ui.ai.AIChatCoordinator;
import iped.app.ui.ai.model.AIChatMessage;
import iped.app.ui.ai.model.ContextFileEntry;
import iped.app.ui.ai.model.Conversation;
import iped.app.ui.ai.context.AIContextManager;
import iped.app.ui.ai.context.ContextChangeEvent;
import iped.app.ui.ai.context.ContextChangeListener;
import iped.app.ui.ai.context.ConversationManager;
import iped.app.ui.ai.backend.AIBackendClient;
import iped.app.ui.ai.backend.AIBackendConfig;
import iped.app.ui.ai.util.ConversationPersistence;

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
    private static final int CONTEXT_VISIBLE_ITEMS = 5;
    private static final int CONTEXT_REMOVE_HOTZONE_PX = 28;

    // Main UI components
    private JFrame frame;
    
    private ChatAreaPanel chatAreaPanel; // Contains the chat display and input area

    private HeaderPanel headerPanel;

    private boolean processing;

    // Sidebar components
    private JSplitPane splitPane;
    private JPanel sidebarPanel;
    private JButton newChatButton;
    private JList<Conversation> conversationList;
    private DefaultListModel<Conversation> conversationListModel;

    // Service layer that handles business logic and threading
    private AIChatCoordinator coordinator;

    // Context-related UI components
    private JPanel contextPanel;
    private JList<Object> contextList;
    private DefaultListModel<Object> contextListModel;
    private JLabel contextEmptyLabel;
    private JButton clearContextButton;
    private TitledBorder contextBorder;
    private boolean isSwitchingChats = false; // Prevents the ContextChangeListener from overwriting saved data during chat switching

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
                // Update the Visual UI
                refreshContextUI();

                // Abort if system is currently switching chats
                if (isSwitchingChats) return;
                
                // Real-Time State Sync for Unsaved Drafts
                Conversation activeConv = ConversationManager.getInstance().getActiveConversation();
                if (activeConv != null) {
                    
                    // Grab all valid file IDs currently sitting in the UI bucket
                    List<Integer> currentIds = AIContextManager.getInstance().getContextFiles().stream()
                            .map(IItem::getId)
                            .collect(Collectors.toList());
                    
                    // Push them directly into the active conversation model
                    activeConv.setContextIds(currentIds);
                    activeConv.updateLastModified();
                    
                    // Save the draft to disk asynchronously so it survives a sudden app crash
                    final Conversation convToSave = activeConv;
                    new Thread(() -> ConversationPersistence.saveConversation(convToSave)).start();
                }
            }
        });
    }

    public void startNewConversationWithCurrentContext(List<IItem> pendingItems) {
        Conversation newConversation = ConversationManager.getInstance().startNewConversation();

        List<Integer> contextIds = new ArrayList<>();
        for (IItem item : AIContextManager.getInstance().getContextFiles()) {
            if (item != null && !contextIds.contains(item.getId())) {
                contextIds.add(item.getId());
            }
        }

        if (pendingItems != null) {
            for (IItem item : pendingItems) {
                if (item != null && !contextIds.contains(item.getId())) {
                    contextIds.add(item.getId());
                }
            }
        }

        newConversation.setContextIds(contextIds);
        newConversation.setChatHashes(new ArrayList<>());
        newConversation.setMessages(new ArrayList<>());
        newConversation.updateLastModified();

        if (pendingItems != null) {
            AIContextManager.getInstance().addContextFiles(pendingItems);
        }

        if (coordinator != null) {
            coordinator.clearHistory();
        }

        refreshSidebarList();
        refreshChatArea();
        conversationList.setSelectedValue(newConversation, true);
        showFrame();
    }

    public boolean isProcessing() {
        return processing;
    }

    private void createUI() {
        String title = "AI Assistant";
        try { title = Messages.getString("AIAssistant.Title"); } catch (Exception e) {}

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setResizable(true);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Instancia o painel passando o título e a ação de toggle via expressão lambda
        headerPanel = new HeaderPanel(title, e -> toggleSidebar());
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Chat Workspace
        JPanel chatWorkspacePanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(createContextSection(), BorderLayout.NORTH);

        String sendText = "Send";
        try { sendText = Messages.getString("AIAssistant.Send"); } catch (Exception e) {}

        chatAreaPanel = new ChatAreaPanel(PANEL_WIDTH, sendText);
       
        // Vinculação dos Listeners de Interação aos Componentes Encapsulados
        chatAreaPanel.getSendButton().addActionListener(e -> handleSendAction());
        chatAreaPanel.getInputArea().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume(); // Previne a quebra de linha padrão do JTextArea
                    handleSendAction();
                }
            }
        });
        
        centerPanel.add(chatAreaPanel, BorderLayout.CENTER);
        refreshChatArea();

        chatAreaPanel.installTextPaneClickListener(
            (hash, chunkId) -> navigateToItem(hash, chunkId), // Regra 1: Navega no IPED
            this::refreshChatArea                             // Regra 2: Atualiza o estado visual
        );

        JPanel tasksPanel = createTasksPanel();
        centerPanel.add(tasksPanel, BorderLayout.EAST);

        chatWorkspacePanel.add(centerPanel, BorderLayout.CENTER);

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
        
        refreshSidebarList();
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

    private JPanel createSidebarPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setMinimumSize(new Dimension(150, 0)); 
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5)); 

        newChatButton = new JButton("+ New Chat");
        newChatButton.setFont(newChatButton.getFont().deriveFont(Font.BOLD));
        newChatButton.addActionListener(e -> startNewChat());
        
        panel.add(newChatButton, BorderLayout.NORTH);

        // Conversation List UI
        conversationListModel = new DefaultListModel<>();
        conversationList = new JList<>(conversationListModel);
        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Custom Renderer to make the items look like modern chat tabs
        conversationList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
            rowPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10)); // Padded
            rowPanel.setOpaque(true);
            rowPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

            if (value instanceof Conversation) {
                Conversation conv = (Conversation) value;
                
                JLabel textLabel = new JLabel(conv.getTitle());
                textLabel.setFont(list.getFont());
                textLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                
                JLabel removeLabel = new JLabel("X");
                removeLabel.setFont(list.getFont().deriveFont(Font.BOLD));
                // Only show red 'X' if selected, otherwise subtle gray, to keep UI clean
                removeLabel.setForeground(isSelected ? new Color(160, 0, 0) : Color.LIGHT_GRAY);
                
                rowPanel.add(textLabel, BorderLayout.CENTER);
                rowPanel.add(removeLabel, BorderLayout.EAST);
            }
            return rowPanel;
        });

        // Wire up the click listener for the sidebar tabs
        conversationList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                
                int index = conversationList.locationToIndex(e.getPoint());
                if (index < 0) return;
                
                Rectangle cellBounds = conversationList.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) return;

                Conversation selected = conversationListModel.getElementAt(index);
                
                // Check if clicked the 'X' hotzone
                if (e.getX() >= cellBounds.x + cellBounds.width - 28) {
                    promptDeleteConversation(selected);
                    return;
                }
                
                // Otherwise, load the conversation
                Conversation active = ConversationManager.getInstance().getActiveConversation();

                // Only load if they clicked a different conversation
                if (selected != null && (active == null || !active.getId().equals(selected.getId()))) {
                    loadConversation(selected);
                }
            }
        });

        // Hide the scrollpane borders to blend seamlessly into the sidebar
        JScrollPane scrollPane = new JScrollPane(conversationList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); 
        
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Confirmation to delete chat when clicking the 'X' on the Conversation list entry
     */
    private void promptDeleteConversation(Conversation conv) {
        int confirm = JOptionPane.showConfirmDialog(frame,
            "Are you sure you want to delete this chat?\n\"" + conv.getTitle() + "\"",
            "Delete Chat",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            // Delete the actual JSON file from the hard drive
            ConversationPersistence.deleteConversation(conv.getId());
            
            // Remove from memory
            ConversationManager.getInstance().removeConversation(conv);
            
            // Check if the chat currently being looked at was deleted
            Conversation active = ConversationManager.getInstance().getActiveConversation();
            if (active == null || active.getId().equals(conv.getId())) {
                List<Conversation> remaining = ConversationManager.getInstance().getConversations();
                
                if (!remaining.isEmpty()) {
                    // Option A: Load the conversation at the top of the list
                    loadConversation(remaining.get(0));
                    refreshSidebarList();
                } else {
                    // Option B: The list is completely empty
                    ConversationManager.getInstance().setActiveConversation(null);
                    clearChatScreenAndMemory();
                    AIContextManager.getInstance().clearContext();
                    refreshSidebarList();
                }
            } else {
                refreshSidebarList(); // Just removes it visually from the sidebar
            }
        }
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

    private void refreshSidebarList() {
        conversationListModel.clear();
        for (Conversation conv : ConversationManager.getInstance().getConversations()) {
            conversationListModel.addElement(conv);
        }
        conversationList.repaint();
    }

    private void startNewChat() {
        // Create a new active conversation in memory first (safeguards the old one)
        ConversationManager.getInstance().startNewConversation();
        
        // Clear UI and Coordinator memory
        clearChatScreenAndMemory();
        
        // Clear IPED context
        AIContextManager.getInstance().clearContext();
        
        refreshSidebarList();
        refreshChatArea();
        
        addMessage("System", "Started a new conversation session.");
        chatAreaPanel.getInputArea().requestFocusInWindow();
    }

    /**
     * Loads a selected conversation from the sidebar into the main chat window.
     */
    private void loadConversation(Conversation conv) {

        // Lock the listener
        isSwitchingChats = true;

        // Update State Manager
        ConversationManager.getInstance().setActiveConversation(conv);
        
        // Hydrate the Coordinator's Network Memory
        if (coordinator != null) {
            coordinator.loadHistoricalContext(conv.getChatHashes(), conv.getContextIds(), conv.getMessages());
        }
        
        // Restore the visual IPED Context UI 
        AIContextManager.getInstance().clearContext(); // Wipe previous chat's files
        
        new Thread(() -> {
            List<IItem> restoredItems = new ArrayList<>();

            try {
                // PATH A: The chat was previously sent to the backend
                // Use the MD5 Chat Hashes to find the file
                if (conv.getChatHashes() != null && !conv.getChatHashes().isEmpty()) {
                    for (String hash : conv.getChatHashes()) {
                        try {
                            IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, "hash:" + hash);
                            MultiSearchResult result = searcher.multiSearch();
                            
                            if (result != null && result.getLength() > 0) {
                                // Extract the fully qualified IItemId (which contains the source routing)
                                IItemId qualifiedItemId = result.getItem(0);

                                // Now the MultiSource can safely fetch the item
                                IItem item = App.get().appCase.getItemByItemId(qualifiedItemId);
                                if (item != null) {
                                restoredItems.add(item);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Could not restore context item hash: " + hash);
                        }
                    }
                }
                // PATH B: The chat is an unsent draft (Fallback to internal IPED Context IDs)
                else if (conv.getContextIds() != null && !conv.getContextIds().isEmpty()) {
                    for (Integer itemId : conv.getContextIds()) {
                        try {
                            IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, "id:" + itemId);
                            MultiSearchResult result = searcher.multiSearch();

                            if (result != null && result.getLength() > 0) {
                                // Extract the fully qualified IItemId (which contains the source routing)
                                IItemId qualifiedItemId = result.getItem(0);

                                // Now the MultiSource can safely fetch the item
                                IItem item = App.get().appCase.getItemByItemId(qualifiedItemId);
                                if (item != null) {
                                restoredItems.add(item);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Could not restore context item ID: " + itemId);
                        }
                    }
                }
            } finally {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (!restoredItems.isEmpty()) {
                            // Check race condition: Did the user click a different chat while the search is ongoing?
                            Conversation currentActive = ConversationManager.getInstance().getActiveConversation();
                            if (currentActive == null || !currentActive.getId().equals(conv.getId())) {
                                return; // Abort
                            }

                            // Update the Coordinator's memory with the freshly fetched IDs just in case
                            // the database was rebuilt and the integer IDs changed
                            List<Integer> freshIds = restoredItems.stream()
                                    .map(IItem::getId)
                                    .collect(Collectors.toList());
                            
                            if (coordinator != null) {
                                // Re-sync the coordinator to prevent a false "contextChanged" flag
                                coordinator.loadHistoricalContext(conv.getChatHashes(), freshIds, conv.getMessages());
                            }
                            
                            // Restore the visual UI
                            AIContextManager.getInstance().addContextFiles(restoredItems);
                        }
                    } finally {
                        // Unlock the listener on the EDT
                        isSwitchingChats = false;
                    }
                });
            }
        }).start();
        
        // Reset UI streaming states
        if (chatAreaPanel != null) {
            chatAreaPanel.forceDiscardStreaming(); 
        }
        
        // Redraw the screen
        refreshChatArea();
        conversationList.setSelectedValue(conv, true);
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

        String[] tasks = {"Summarize", "Find Patterns"};
        for (String task : tasks) {
            JButton btn = new JButton(task);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(200, 30));
            // Firing a pre-written prompt directly into the input area logic
            btn.addActionListener(e -> {
                chatAreaPanel.getInputArea().setText(taskPrompts.get(task));
                handleSendAction();
            });
            panel.add(btn);
            panel.add(Box.createVerticalStrut(5));
        }
        
        return panel;
    }
    
    /**
     * Clears the UI and the Coordinator's memory. 
     * Does NOT delete the saved messages in the ConversationManager.
     */
    private void clearChatScreenAndMemory() {
        // Wipe the Coordinator's memory so the LLM forgets the previous context
        if (coordinator != null) {
            coordinator.clearHistory();
        }

        if (chatAreaPanel != null) {
            chatAreaPanel.clearChatScreen();
        }
    }

    private void refreshChatArea() {
        if (chatAreaPanel != null) {
            chatAreaPanel.renderHistoricalMessages(buildRenderableMessages());
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
        
        // Let the Manager hold the data
        ConversationManager.getInstance().addMessageToActive(chatMessage);

        refreshChatArea();
    }

    private void addMessage(String sender, String message) {
        addMessage(sender, message, "system");
    }

    // Renderable messages are the messages in the current active conversation
    private List<AIChatMessage> buildRenderableMessages() {
        List<AIChatMessage> renderableMessages = new ArrayList<>();
    
        // Safely check if there is an active conversation
        Conversation activeConv = ConversationManager.getInstance().getActiveConversation();
        if (activeConv != null) {
            renderableMessages.addAll(activeConv.getMessages());
        }

        if (chatAreaPanel != null && chatAreaPanel.getCurrentDraftMessage() != null) {
            renderableMessages.add(chatAreaPanel.getCurrentDraftMessage());
        }
        return renderableMessages;
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

    public void showFrame() {
        Runnable action = () -> {
            ensureVisibleOnScreen();
            
            if (frame.getExtendedState() == JFrame.ICONIFIED) {
                frame.setExtendedState(JFrame.NORMAL);
            }
            
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
            
            frame.toFront();
            frame.requestFocus();
            
            if (chatAreaPanel != null) {
                chatAreaPanel.requestFocusToInput();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    /**
     * The main execution block linking user intent to the background Coordinator.
     */
    private void handleSendAction() {
        String text = chatAreaPanel.getInputArea().getText().trim();
        if (!text.isEmpty()) {
            if (!ensureChatServiceInitialized()) {
                return;
            }

            // If the user deleted all chats and types in the blank slate, start a new one
            if (ConversationManager.getInstance().getActiveConversation() == null) {
                ConversationManager.getInstance().startNewConversation();
                refreshSidebarList();
            }

            // Print user message immediately
            addMessage("You", text, "user");
            chatAreaPanel.getInputArea().setText("");
            
            // Lock the UI
            setProcessing(true);
            
            AIChatMessage assistantDraft = AIChatMessage.now("Assistant", "", "assistant");
            
            chatAreaPanel.startMessageStreaming(assistantDraft);
            
            // Call the service
            coordinator.askQuestion(
                text, 
                // Callback 1: Append tokens to the live assistant draft
                (token) -> SwingUtilities.invokeLater(() -> {
                    chatAreaPanel.enqueueStreamingToken(token);
                }),
                // Callback 2: Keep the completed draft visible and unlock the UI
                () -> SwingUtilities.invokeLater(() -> {
                   chatAreaPanel.pruneStreaming(() -> {
                        if (!assistantDraft.getContent().isEmpty()) {
                            // Registra a resposta final estável no modelo de dados histórico do IPED
                            ConversationManager.getInstance().addMessageToActive(assistantDraft);
                            refreshSidebarList();
                        }
                        setProcessing(false);
                    });
                }),
                // Callback 3: Handle Errors
                (errorMessage) -> SwingUtilities.invokeLater(() -> {
                    chatAreaPanel.forceDiscardStreaming();
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
        this.processing = processing;
        chatAreaPanel.setProcessing(processing);
        frame.setCursor(processing ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }


    public void toggleVisibility() {
        SwingUtilities.invokeLater(() -> {
            if (frame.isVisible()) {
                frame.setVisible(false);
            } else {
                showFrame();
            }
        });
    }
}
