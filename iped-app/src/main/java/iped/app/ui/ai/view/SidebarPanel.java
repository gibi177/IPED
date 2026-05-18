package iped.app.ui.ai.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import iped.app.ui.ai.model.Conversation;
import iped.app.ui.ai.context.ConversationManager;
import iped.app.ui.ai.util.ConversationPersistence;

/**
 * Componente de interface gráfica responsável pela barra lateral de conversas.
 * Aplica os princípios SRP (Responsabilidade Única) e DIP (Inversão de Dependência).
 */
public class SidebarPanel extends JPanel {



    private JButton newChatButton;
    private JList<Conversation> conversationList;
    private DefaultListModel<Conversation> conversationListModel;
    
    private final Component parentFrame;
    private final SidebarListener listener;

    private final ConversationManager conversationManager;

    /**
     * Interface de contrato para comunicação com o painel principal (AIAssistantPanel).
     */
    public interface SidebarListener {
        void onConversationSelected(Conversation conversation);
        void onNewChatRequested();
        void onConversationDeleted(Conversation conversation, boolean isActiveDeleted);
    }

    /**
     * Construtor da Sidebar com injeção de dependências.
     * 
     * @param parentFrame Componente pai necessário para posicionamento do JOptionPane.
     * @param listener    Implementação do contrato de eventos da barra lateral.
     */
    public SidebarPanel(Component parentFrame, SidebarListener listener, ConversationManager conversationManager) {
        this.parentFrame = parentFrame;
        this.conversationManager = conversationManager; // dependency injection via constructor
        this.listener = listener;
        
        configurePanelLayout();
        initComponents();
    }

    private void configurePanelLayout() {
        setMinimumSize(new Dimension(150, 0)); 
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5)); 
        setLayout(new BorderLayout(0, 5));
    }

    private void initComponents() {
        // Inicialização do botão de criação de chat
        newChatButton = new JButton("+ New Chat");
        newChatButton.setFont(newChatButton.getFont().deriveFont(Font.BOLD));
        newChatButton.addActionListener(e -> {
            if (listener != null) {
                listener.onNewChatRequested();
            }
        });
        add(newChatButton, BorderLayout.NORTH);

        // Inicialização da lista de componentes
        conversationListModel = new DefaultListModel<>();
        conversationList = new JList<>(conversationListModel);
        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        setupCellRenderer();
        setupMouseListeners();

        JScrollPane scrollPane = new JScrollPane(conversationList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); 
        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupCellRenderer() {
        conversationList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
            rowPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            rowPanel.setOpaque(true);
            rowPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

            if (value instanceof Conversation) {
                Conversation conv = (Conversation) value;
                
                JLabel textLabel = new JLabel(conv.getTitle());
                textLabel.setFont(list.getFont());
                textLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                
                JLabel removeLabel = new JLabel("X");
                removeLabel.setFont(list.getFont().deriveFont(Font.BOLD));
                removeLabel.setForeground(isSelected ? new Color(160, 0, 0) : Color.LIGHT_GRAY);
                
                rowPanel.add(textLabel, BorderLayout.CENTER);
                rowPanel.add(removeLabel, BorderLayout.EAST);
            }
            return rowPanel;
        });
    }

    private void setupMouseListeners() {
        conversationList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                
                int index = conversationList.locationToIndex(e.getPoint());
                if (index < 0) return;
                
                Rectangle cellBounds = conversationList.getCellBounds(index, index);
                if (cellBounds == null || !cellBounds.contains(e.getPoint())) return;

                Conversation selected = conversationListModel.getElementAt(index);
                
                // Verificação de clique na Hotzone do botão 'X' (28 pixels à direita)
                if (e.getX() >= cellBounds.x + cellBounds.width - 28) {
                    promptDeleteConversation(selected);
                    return;
                }
                
                // Seleção de nova conversa
                Conversation active = conversationManager.getActiveConversation();
                if (selected != null && (active == null || !active.getId().equals(selected.getId()))) {
                    if (listener != null) {
                        listener.onConversationSelected(selected);
                    }
                }
            }
        });
    }
    
    public JList<Conversation> getConversationList() {
        return conversationList;
    }

    public void setConversationList(JList<Conversation> conversationList) {
        this.conversationList = conversationList;
    }

    /**
     * Sincroniza os dados locais do modelo visual com o ConversationManager.
     */
    public void refreshList() {
        conversationListModel.clear();
        for (Conversation conv : conversationManager.getConversations()) {
            conversationListModel.addElement(conv);
        }
        conversationList.repaint();
    }

    /**
     * Permite que a classe externa controle a seleção visual do componente.
     */
    public void setSelectedValue(Conversation conv, boolean shouldScroll) {
        conversationList.setSelectedValue(conv, shouldScroll);
    }

    private void promptDeleteConversation(Conversation conv) {
        int confirm = JOptionPane.showConfirmDialog(parentFrame,
            "Are you sure you want to delete this chat?\n\"" + conv.getTitle() + "\"",
            "Delete Chat",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            // Remove logicamente e fisicamente
            ConversationPersistence.deleteConversation(conv.getId());
            conversationManager.removeConversation(conv);
            
            Conversation active = conversationManager.getActiveConversation();
            boolean isActiveDeleted = (active == null || active.getId().equals(conv.getId()));
            
            // Reposiciona o ponteiro de conversas caso a conversa ativa tenha sido deletada
            if (isActiveDeleted) {
                List<Conversation> remaining = ConversationManager.getInstance().getConversations();
                if (!remaining.isEmpty()) {
                    conversationManager.setActiveConversation(remaining.get(0));
                } else {
                    conversationManager.setActiveConversation(null);
                }
            }
            
            // Notifica o listener externo para atualizações colaterais (Ex: limpar tela)
            if (listener != null) {
                listener.onConversationDeleted(conv, isActiveDeleted);
            }
            
            refreshList();
        }
    }
}