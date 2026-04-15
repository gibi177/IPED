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
import iped.app.ui.ai.backend.AIBackendClient;
import iped.app.ui.ai.backend.AIBackendConfig;
import iped.data.IItem;

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
    private static final int PANEL_WIDTH = 550;

    // Main UI components
    private JDialog dialog;
    private JTextArea chatArea;
    private JTextArea inputArea;
    private JButton sendButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    // Coordinator (The Controller that handles business logic and threading)
    private iped.app.ui.ai.AIChatCoordinator coordinator;

    // Context-related UI components
    private JPanel contextPanel;
    private JList<ContextFileEntry> contextList;
    private DefaultListModel<ContextFileEntry> contextListModel;
    private JLabel contextEmptyLabel;
    private JButton clearContextButton;
    private TitledBorder contextBorder;

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
        // Initialize the Controller. 
        this.coordinator = new iped.app.ui.ai.AIChatCoordinator(
            new AIBackendClient(AIBackendConfig.loadFromSystemProperties())
        );
        
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

        dialog = new JDialog(App.get(), title, false);
        dialog.setResizable(true);

        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(createContextSection(), BorderLayout.NORTH);

        // Chat display area setup
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatArea.setBackground(new Color(245, 245, 245));
        
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 400));
        centerPanel.add(chatScroll, BorderLayout.CENTER);

        JPanel tasksPanel = createTasksPanel();
        centerPanel.add(tasksPanel, BorderLayout.EAST);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        dialog.getContentPane().add(mainPanel);
        dialog.pack();
        positionDialog();

        addMessage("System", "AI Assistant ready. Connected to local Backend server.\nRight-click an HTML WhatsApp chat export to add it to the context, then type your question.");
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());

        String titleText = "AI Assistant";
        try { titleText = Messages.getString("AIAssistant.Title"); } catch (Exception e) {}
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        // Update status label to indicate live connection
        statusLabel = new JLabel("● Connected to local backend server"); 
        statusLabel.setForeground(new Color(0, 150, 0)); // Green for active

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(titleLabel, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        return headerPanel;
    }

    private JPanel createContextSection() {
        contextListModel = new DefaultListModel<>();
        contextList = new JList<>(contextListModel);
        contextList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contextList.setVisibleRowCount(5);
        contextList.setBackground(new Color(255, 255, 240));

        // Custom Cell Renderer mapping our ContextFileEntry ViewModel to the screen
        contextList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ContextFileEntry) {
                ContextFileEntry entry = (ContextFileEntry) value;
                label.setText(entry.getDisplayLabel());
                label.setToolTipText(entry.getFullPath());
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
        List<IItem> files = AIContextManager.getInstance().getContextFiles();
        contextListModel.clear();

        if (files.isEmpty()) {
            contextEmptyLabel.setVisible(true);
            contextList.setVisible(false);
            clearContextButton.setEnabled(false);
        } else {
            contextEmptyLabel.setVisible(false);
            contextList.setVisible(true);
            clearContextButton.setEnabled(true);
            for (IItem file : files) {
                contextListModel.addElement(new ContextFileEntry(file));
            }
        }

        contextBorder.setTitle("Added Context (" + files.size() + " files)");
        contextPanel.repaint();
    }

    private JPanel createTasksPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Quick Actions"));

        String[] tasks = {"Summarize", "Find Patterns", "Analyze Metadata"};
        for (String task : tasks) {
            JButton btn = new JButton(task);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(200, 30));
            // Firing a pre-written prompt directly into the input area logic
            btn.addActionListener(e -> {
                inputArea.setText(task + " the provided file.");
                handleSendAction();
            });
            panel.add(btn);
            panel.add(Box.createVerticalStrut(5));
        }
        return panel;
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
     * The main execution block linking user intent to the background Coordinator.
     */
    private void handleSendAction() {
        String text = inputArea.getText().trim();
        if (!text.isEmpty()) {
            // Print user message immediately
            addMessage("User", text);
            inputArea.setText("");
            
            // Lock the UI
            setProcessing(true);
            
            // Call the coordinator
            coordinator.askQuestion(
                text, 
                // Callback 1: Stream chunks to the chat area safely on the UI thread
                (token) -> javax.swing.SwingUtilities.invokeLater(() -> {
                    chatArea.append(token);
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }),
                // Callback 2: Unlock the UI when done
                () -> javax.swing.SwingUtilities.invokeLater(() -> setProcessing(false)),
                // Callback 3: Handle Errors
                (errorMessage) -> javax.swing.SwingUtilities.invokeLater(() -> {
                    addMessage("System Error", errorMessage);
                    setProcessing(false);
                })
            );
        }
    }

    private void addMessage(String sender, String message) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        chatArea.append(String.format("[%s] %s: %s\n\n", time, sender, message));
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    /**
     * Locks or unlocks the input fields and displays the loading bar.
     */
    private void setProcessing(boolean processing) {
        progressBar.setVisible(processing);
        sendButton.setEnabled(!processing);
        inputArea.setEnabled(!processing);
        dialog.setCursor(processing ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    public void toggleVisibility() {
        if (dialog.isVisible()) {
            dialog.setVisible(false);
        } else {
            dialog.setVisible(true);
            inputArea.requestFocusInWindow();
        }
    }

    public void showPanel() {
        if (!dialog.isVisible()) {
            dialog.setVisible(true);
        }
        inputArea.requestFocusInWindow();
    }
}