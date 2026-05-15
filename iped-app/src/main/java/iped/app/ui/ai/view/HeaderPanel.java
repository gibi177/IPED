package iped.app.ui.ai.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Painel modularizado para o cabeçalho da aplicação.
 * Encapsula o título, botão de controle da sidebar e rótulo de status do backend.
 */
public class HeaderPanel extends JPanel {

    private final JLabel statusLabel;

    public HeaderPanel(String titleText, ActionListener toggleSidebarListener) {
        // Inicializa o JPanel base com BorderLayout
        super(new BorderLayout());

        // Botão de alternância da barra lateral (Sidebar)
        JButton toggleSidebarBtn = new JButton("☰");
        toggleSidebarBtn.setMargin(new Insets(2, 6, 2, 6));
        toggleSidebarBtn.setFocusPainted(false);
        toggleSidebarBtn.setToolTipText("Toggle Sidebar");
        // Executa a ação injetada pela classe principal
        toggleSidebarBtn.addActionListener(toggleSidebarListener);

        // Rótulo do título
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        // Área do título (Botão + Texto)
        JPanel titleArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleArea.add(toggleSidebarBtn);
        titleArea.add(titleLabel);

        // Inicialização do rótulo de status
        statusLabel = new JLabel("● Connected to local backend server");
        statusLabel.setForeground(new Color(0, 150, 0)); // Verde para ativo

        // Agrupamento do título e status no lado esquerdo
        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));
        leftPanel.add(titleArea, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        // Adiciona os componentes ao painel principal (this)
        add(leftPanel, BorderLayout.WEST);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
    }

    /**
     * Permite que controladores externos atualizem o estado visual do status do backend.
     */
    public void updateStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }
}