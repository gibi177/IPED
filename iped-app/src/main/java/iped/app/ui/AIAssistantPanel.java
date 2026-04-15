package iped.app.ui;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import iped.app.ui.ai.AIContextManager;
import iped.app.ui.ai.ContextChangeEvent;
import iped.app.ui.ai.ContextChangeListener;
import iped.app.ui.ai.ContextFileEntry;
import iped.data.IItem;

/**
 * AI Assistant floating panel UI layer for IPED.
 * <p>
 * This class implements the visual UI of the AI Assistant. It is a
 * singleton and provides a floating panel containing:
 * <ul>
 *     <li>Chat display and input areas</li>
 *     <li>AI context file management list</li>
 *     <li>Quick action buttons</li>
 *     <li>Status and progress indicators</li>
 * </ul>
 * </p>
 * <p>
 * Currently, this is strictly a UI layer for testing and does not include
 * logic or LLM integration.
 * </p>
 */
public class AIAssistantPanel {

    // UI positioning constants
    private static final int HORIZONTAL_OFFSET = 30;
    private static final int VERTICAL_OFFSET = 120;
    private static final double HEIGHT_PERCENTAGE = 0.8;
    private static final int PANEL_WIDTH = 550;

    // Main UI components
    private JDialog dialog;
    private JTextArea chatArea;
    private JTextArea inputArea;
    private JButton sendButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    // Context-related UI components
    private JPanel contextPanel;
    private JList<ContextFileEntry> contextList;
    private DefaultListModel<ContextFileEntry> contextListModel;
    private JLabel contextEmptyLabel;
    private JButton clearContextButton;
    private TitledBorder contextBorder;

    // Singleton instance
    private static AIAssistantPanel instance;

    /**
     * Returns the singleton instance of the AI Assistant Panel.
     *
     * @return singleton instance
     */
    public static AIAssistantPanel getInstance() {
        if (instance == null) {
            instance = new AIAssistantPanel();
        }
        return instance;
    }

    /**
     * Private constructor: initializes UI components and registers context change listener.
     */
    private AIAssistantPanel() {
        createUI();

        // Listen for AI Context Changes to refresh context list
        AIContextManager.getInstance().addContextChangeListener(new ContextChangeListener() {
            @Override
            public void contextChanged(ContextChangeEvent event) {
                refreshContextUI();
            }
        });
    }

    /**
     * Creates the floating panel UI including chat, context, and quick tasks.
     */
    private void createUI() {
        String title = "AI Assistant";
        try { title = Messages.getString("AIAssistant.Title"); } catch (Exception e) {}

        dialog = new JDialog(App.get(), title, false);
        dialog.setResizable(true);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Add header (title + status)
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // Center panel contains context list and chat
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(createContextSection(), BorderLayout.NORTH);

        // Chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatArea.setBackground(new Color(245, 245, 245));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 400));

        centerPanel.add(chatScroll, BorderLayout.CENTER);

        // Quick action buttons
        JPanel tasksPanel = createTasksPanel();
        centerPanel.add(tasksPanel, BorderLayout.EAST);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Input area and send button at the bottom
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        dialog.getContentPane().add(mainPanel);
        dialog.pack();
        positionDialog();

        // Initial system message
        addMessage("System", "UI Layer Active. Still missing LLM integration. Right click files to add them to Context.");
    }

    /**
     * Creates the header panel with title and status label.
     *
     * @return header panel
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());

        String titleText = "AI Assistant";
        try { titleText = Messages.getString("AIAssistant.Title"); } catch (Exception e) {}
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        statusLabel = new JLabel("● UI only, for now");
        statusLabel.setForeground(new Color(100, 100, 100));

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(titleLabel, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        return headerPanel;
    }

    /**
     * Creates the AI context section showing added files and clear button.
     *
     * @return context panel
     */
    private JPanel createContextSection() {
        contextListModel = new DefaultListModel<>();
        contextList = new JList<>(contextListModel);
        contextList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contextList.setVisibleRowCount(5);
        contextList.setBackground(new Color(255, 255, 240));

        // Custom cell renderer to show file label and tooltip
        contextList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ContextFileEntry) {
                ContextFileEntry entry = (ContextFileEntry) value;
                if (entry.isValidForContext()) {
                    label.setText(entry.getDisplayLabel());
                    label.setToolTipText(entry.getFullPath());
                } else {
                    String reason = entry.getValidationReason() != null ? entry.getValidationReason() : "Rejected item.";
                    label.setText(entry.getFileName() + " - " + reason);
                    label.setToolTipText(reason + " Path: " + entry.getFullPath());
                    label.setForeground(new Color(180, 0, 0));
                }
            }
            return label;
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

        // Clear button to remove all context files
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
     * Updates the context list UI based on current AI context files.
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
            for (ContextFileEntry entry : entries) {
                contextListModel.addElement(entry);
            }
        }

        if (invalidCount > 0) {
            contextBorder.setTitle("Added Context (" + validFiles.size() + " valid, " + invalidCount + " rejected)");
        } else {
            contextBorder.setTitle("Added Context (" + validFiles.size() + " valid)");
        }
        contextPanel.repaint();
    }

    /**
     * Creates the panel containing quick action buttons.
     *
     * @return tasks panel
     */
    private JPanel createTasksPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Quick Actions"));

        String[] tasks = {"Summarize", "Find Patterns", "Analyze Metadata"};
        for (String task : tasks) {
            JButton btn = new JButton(task);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(200, 30));
            btn.addActionListener(e -> addMessage("Action", "Requested: " + task));
            panel.add(btn);
            panel.add(Box.createVerticalStrut(5));
        }
        return panel;
    }

    /**
     * Creates the bottom panel containing input area, send button, and progress bar.
     *
     * @return bottom panel
     */
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        inputArea = new JTextArea(6, 20);
        inputArea.setLineWrap(true);
        inputArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
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

    /**
     * Positions the floating dialog on the screen with offsets.
     */
    private void positionDialog() {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();

        int height = (int) (screenBounds.height * HEIGHT_PERCENTAGE);
        dialog.setSize(PANEL_WIDTH + 150, height);

        int x = screenBounds.x + screenBounds.width - dialog.getWidth() - HORIZONTAL_OFFSET;
        int y = screenBounds.y + VERTICAL_OFFSET;

        if (y + dialog.getHeight() > screenBounds.y + screenBounds.height) {
            y = screenBounds.y + screenBounds.height - dialog.getHeight();
        }

        dialog.setLocation(x, y);
    }

    /**
     * Handles sending the user's input message.
     */
    private void handleSendAction() {
        String text = inputArea.getText().trim();
        if (!text.isEmpty()) {
            addMessage("User", text);
            inputArea.setText("");
            simulateProcessing();
        }
    }

    /**
     * Appends a message to the chat area with timestamp.
     *
     * @param sender  name of the sender (e.g., User, System)
     * @param message message text
     */
    private void addMessage(String sender, String message) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        chatArea.append(String.format("[%s] %s: %s\n\n", time, sender, message));
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    /**
     * Simulates AI processing by showing the progress bar briefly.
     */
    private void simulateProcessing() {
        setProcessing(true);
        new javax.swing.Timer(1000, e -> setProcessing(false)).start();
    }

    /**
     * Sets the UI into a processing state.
     *
     * @param processing true if processing, false otherwise
     */
    private void setProcessing(boolean processing) {
        progressBar.setVisible(processing);
        sendButton.setEnabled(!processing);
        inputArea.setEnabled(!processing);
        dialog.setCursor(processing ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    /**
     * Toggles the visibility of the AI Assistant panel.
     */
    public void toggleVisibility() {
        if (dialog.isVisible()) {
            dialog.setVisible(false);
        } else {
            dialog.setVisible(true);
            inputArea.requestFocusInWindow();
        }
    }

    /**
     * Shows the panel if hidden, and requests input focus.
     */
    public void showPanel() {
        if (!dialog.isVisible()) {
            dialog.setVisible(true);
        }
        inputArea.requestFocusInWindow();
    }
}