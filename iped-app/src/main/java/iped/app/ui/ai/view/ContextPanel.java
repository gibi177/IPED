package iped.app.ui.ai.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

import iped.app.ui.ai.context.AIContextManager;
import iped.app.ui.ai.model.ContextFileEntry;
import iped.data.IItem;

/**
 * Encapsulated component responsible for displaying and managing the interface
 * for files added to the AI context. Applies SRP and reacts directly to the AIContextManager.
 */
public class ContextPanel extends JPanel {

    private static final int CONTEXT_VISIBLE_ITEMS = 5;
    private static final int CONTEXT_REMOVE_HOTZONE_PX = 28;
    private static final int PANEL_WIDTH = 750;

    private JList<Object> contextList;
    private DefaultListModel<Object> contextListModel;
    private JLabel contextEmptyLabel;
    private JButton clearContextButton;
    private TitledBorder contextBorder;

    private final AIContextManager contextManager;

    /**
     * Internal utility class responsible for rendering the capacity overflow line.
     */
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

    public ContextPanel(AIContextManager contextManager) {
        this.contextManager = contextManager;
        
        configureLayout();
        initComponents();
        setupContextListener();
        refreshContextUI();
    }

    private void configureLayout() {
        setLayout(new BorderLayout(5, 5));
        contextBorder = BorderFactory.createTitledBorder("Added Context (0 files)");
        setBorder(contextBorder);
    }

    private void initComponents() {
        contextListModel = new DefaultListModel<>();
        contextList = new JList<>(contextListModel);
        contextList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contextList.setVisibleRowCount(CONTEXT_VISIBLE_ITEMS);
        contextList.setBackground(new Color(255, 255, 240));

        setupCellRenderer();
        setupMouseListener();

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
        clearContextButton.addActionListener(e -> contextManager.clearContext());

        JPanel actionPanel = new JPanel(new BorderLayout());
        actionPanel.add(clearContextButton, BorderLayout.NORTH);

        add(listContainer, BorderLayout.CENTER);
        add(actionPanel, BorderLayout.EAST);
    }

    private void setupCellRenderer() {
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

    private void setupMouseListener() {
        contextList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;

                int index = contextList.locationToIndex(e.getPoint());
                if (index < 0 || index >= contextListModel.size()) return;

                Rectangle cellBounds = contextList.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) return;

                Object value = contextListModel.getElementAt(index);
                if (!(value instanceof ContextFileEntry)) return;

                if (e.getX() < cellBounds.x + cellBounds.width - CONTEXT_REMOVE_HOTZONE_PX) return;

                contextManager.removeContextFile(((ContextFileEntry) value).getItem());
            }
        });
    }

    private void setupContextListener() {
        // The panel itself listens to its own state to redraw the list when the state changes
        contextManager.addContextChangeListener(event -> refreshContextUI());
    }

    /**
     * Synchronizes the file UI with the actual domain state.
     */
    public void refreshContextUI() {
        List<ContextFileEntry> entries = contextManager.getContextEntriesForUI();
        List<IItem> validFiles = contextManager.getContextFiles();
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

        repaint();
    }
}