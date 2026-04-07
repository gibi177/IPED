package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * AI Assistant floating panel UI Layer for IPED.
 * Strictly visual implementation for UI/UX testing (for now)
 */
public class AIAssistantPanel {
    
    private static final int HORIZONTAL_OFFSET = 30;
    private static final int VERTICAL_OFFSET = 120;
    private static final double HEIGHT_PERCENTAGE = 0.8;
    private static final int PANEL_WIDTH = 550;
    
    private JDialog dialog;
    private JTextArea chatArea;
    private JTextArea inputArea;
    private JButton sendButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    
    private static AIAssistantPanel instance;

    /**
     * Gets singleton instance
     */
    public static AIAssistantPanel getInstance() {
        if (instance == null) {
            instance = new AIAssistantPanel();
        }
        return instance;
    }

    /**
     * Private constructor - UI initialization only, no logic or LLM integration yet.
     */
    private AIAssistantPanel() {
        createUI();
    }

    /**
     * Creates the floating window UI
     */
    private void createUI() {
        // Initialize Dialog - linked to the main App frame
        dialog = new JDialog(App.get(), Messages.getString("AIAssistant.Title"), false);
        dialog.setResizable(true);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header with status
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);
        
        // Chat Display Area (Center)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatArea.setBackground(new Color(245, 245, 245));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 400));
        
        // Quick Tasks Panel (Right Sidebar)
        JPanel tasksPanel = createTasksPanel();
        
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(chatScroll, BorderLayout.CENTER);
        centerPanel.add(tasksPanel, BorderLayout.EAST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Input & Progress Section (Bottom)
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);
        
        dialog.getContentPane().add(mainPanel);
        dialog.pack();
        positionDialog();
        
        // Initial Mock Message
        addMessage("System", "UI Layer Active. Still missing LLM integration.");
    }

    /**
     * Creates header panel with title and status
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());

        // Title
        JLabel titleLabel = new JLabel(Messages.getString("AIAssistant.Title"));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        // Status indicator
        statusLabel = new JLabel("● UI only, for now");
        statusLabel.setForeground(new Color(100, 100, 100));
        
        // Assemble header
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(titleLabel, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);
        
        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        return headerPanel;
    }

    /**
     * Create quick tasks panel (right sidebar)
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
            // UI-only interaction:
            btn.addActionListener(e -> addMessage("Action", "Requested: " + task));
            panel.add(btn);
            panel.add(Box.createVerticalStrut(5));
        }
        return panel;
    }

    /**
     * Creates input panel, located on the bottom, with text area and send button
     */
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        
        inputArea = new JTextArea(6, 20);
        inputArea.setLineWrap(true);
        inputArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        // Key Listener for Enter Key
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    handleSendAction();
                }
            }
        });

        sendButton = new JButton(Messages.getString("AIAssistant.Send"));
        sendButton.addActionListener(e -> handleSendAction());

        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        
        return bottomPanel;
    }

    private void positionDialog() {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();
        
        // Calculate dialogue height
        int height = (int) (screenBounds.height * HEIGHT_PERCENTAGE);
        dialog.setSize(PANEL_WIDTH + 150, height);

        // Position dialogue
        int x = screenBounds.x + screenBounds.width - dialog.getWidth() - HORIZONTAL_OFFSET;
        int y = screenBounds.y + VERTICAL_OFFSET;

        // Ensure dialogue fits on screen
        if (y + dialog.getHeight() > screenBounds.y + screenBounds.height) {
            y = screenBounds.y + screenBounds.height - dialog.getHeight();
        }

        dialog.setLocation(x, y);
    }

    //-----------------------------------------------------------------
    /**
     * Everything downwards will need heavy editing for LLM integration
     * Also, many methods are currently missing
     * Don't forget to revise previous code when adding the integration
     */
    //-----------------------------------------------------------------

    private void handleSendAction() {
        String text = inputArea.getText().trim();
        if (!text.isEmpty()) {
            addMessage("User", text);
            inputArea.setText("");
            
            // Simulation of "Processing" state for visual feedback
            simulateProcessing();
        }
    }

    private void addMessage(String sender, String message) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        chatArea.append(String.format("[%s] %s: %s\n\n", time, sender, message));
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void simulateProcessing() {
        setProcessing(true);
        // Temporary timer to reset the UI after 1 second (purely for visual testing)
        new javax.swing.Timer(1000, e -> setProcessing(false)).start();
    }

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
}
