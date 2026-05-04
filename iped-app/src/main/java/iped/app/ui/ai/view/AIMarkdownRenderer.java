package iped.app.ui.ai.view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import iped.app.ui.ai.context.AIContextManager;
import iped.app.ui.ai.model.ContextFileEntry;
import iped.app.ui.ai.model.AIChatMessage;

/**
 * Renders assistant chat messages with lightweight Markdown support.
 */
public class AIMarkdownRenderer {

    public static final String TOKEN_ATTRIBUTE = "ai-token";
    public static final String TOKEN_HASH_ATTRIBUTE = "ai-token-hash";
    public static final String TOKEN_CHUNK_ID_ATTRIBUTE = "ai-token-chunk-id";

    private static final Logger LOGGER = LoggerFactory.getLogger(AIMarkdownRenderer.class);

    private final JTextPane chatArea;
    private final StyledDocument chatDocument;
    private final Map<String, Style> chatStyles = new HashMap<>();
    private int draftStartOffset = -1;

    public AIMarkdownRenderer(JTextPane chatArea) {
        this.chatArea = chatArea;
        this.chatDocument = new DefaultStyledDocument();
        this.chatArea.setDocument(chatDocument);
        configureChatStyles();
    }

    /**
     * Returns the styled document currently bound to the chat area.
     */
    public StyledDocument getDocument() {
        return chatDocument;
    }

    /**
     * Clears the chat and renders all messages in sequence.
     */
    public void renderMessages(List<AIChatMessage> messages) {
        try {
            draftStartOffset = -1;
            chatDocument.remove(0, chatDocument.getLength());
            for (AIChatMessage message : messages) {
                appendMessageToDocument(message);
            }
        } catch (BadLocationException e) {
            System.err.println("Error rendering chat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Appends a finalized message to the end of the current document.
     */
    public void appendMessage(AIChatMessage message) {
        try {
            appendMessageToDocument(message);
        } catch (BadLocationException e) {
            System.err.println("Error appending chat message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Replaces only the streaming draft at the end of the document.
     */
    public void renderDraft(AIChatMessage message) {
        try {
            if (draftStartOffset < 0) {
                draftStartOffset = chatDocument.getLength();
            } else {
                chatDocument.remove(draftStartOffset, chatDocument.getLength() - draftStartOffset);
            }

            appendMessageToDocument(message);
        } catch (BadLocationException e) {
            System.err.println("Error rendering draft chat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Marks the current draft as committed, keeping the rendered content in place.
     */
    public void commitDraft() {
        draftStartOffset = -1;
    }

    /**
     * Removes the current draft from the document.
     */
    public void discardDraft() {
        if (draftStartOffset < 0) {
            return;
        }

        try {
            chatDocument.remove(draftStartOffset, chatDocument.getLength() - draftStartOffset);
        } catch (BadLocationException e) {
            System.err.println("Error discarding draft chat: " + e.getMessage());
            e.printStackTrace();
        } finally {
            draftStartOffset = -1;
        }
    }

    /**
     * Maps a message type to the corresponding style key.
     */
    private String getMessageClass(String type) {
        if ("user".equalsIgnoreCase(type)) {
            return "user-message";
        } else if ("assistant".equalsIgnoreCase(type)) {
            return "assistant-message";
        } else if ("error".equalsIgnoreCase(type)) {
            return "error-message";
        } else {
            return "system-message";
        }
    }

    /**
     * Defines all reusable styles used by the chat renderer.
     */
    private void configureChatStyles() {
        Style base = chatArea.addStyle("base", null);
        StyleConstants.setFontFamily(base, "SansSerif");
        StyleConstants.setFontSize(base, 12);
        StyleConstants.setForeground(base, new java.awt.Color(0x2f, 0x34, 0x37));
        chatStyles.put("base", base);

        Style sender = chatArea.addStyle("sender", base);
        StyleConstants.setBold(sender, true);
        StyleConstants.setForeground(sender, new java.awt.Color(0x21, 0x26, 0x2b));
        chatStyles.put("sender", sender);

        Style time = chatArea.addStyle("time", base);
        StyleConstants.setFontSize(time, 10);
        StyleConstants.setForeground(time, new java.awt.Color(0x7a, 0x81, 0x86));
        chatStyles.put("time", time);

        Style user = chatArea.addStyle("user", base);
        StyleConstants.setBackground(user, new java.awt.Color(0xec, 0xef, 0xf1));
        chatStyles.put("user-message", user);

        Style assistant = chatArea.addStyle("assistant", base);
        StyleConstants.setBackground(assistant, new java.awt.Color(0xf6, 0xf7, 0xf8));
        chatStyles.put("assistant-message", assistant);

        Style system = chatArea.addStyle("system", base);
        StyleConstants.setBackground(system, new java.awt.Color(0xfd, 0xef, 0xef));
        chatStyles.put("system-message", system);

        Style error = chatArea.addStyle("error", base);
        StyleConstants.setBackground(error, new java.awt.Color(0xfa, 0xde, 0xde));
        chatStyles.put("error-message", error);

        Style heading = chatArea.addStyle("heading", base);
        StyleConstants.setBold(heading, true);
        StyleConstants.setFontSize(heading, 14);
        chatStyles.put("heading", heading);

        Style bold = chatArea.addStyle("bold", base);
        StyleConstants.setBold(bold, true);
        chatStyles.put("bold", bold);

        Style italic = chatArea.addStyle("italic", base);
        StyleConstants.setItalic(italic, true);
        chatStyles.put("italic", italic);

        Style blockquote = chatArea.addStyle("blockquote", base);
        StyleConstants.setForeground(blockquote, new java.awt.Color(0x55, 0x55, 0x55));
        chatStyles.put("blockquote", blockquote);

        Style token = chatArea.addStyle("token", base);
        StyleConstants.setForeground(token, new java.awt.Color(0x0b, 0x57, 0xd0));
        StyleConstants.setUnderline(token, true);
        StyleConstants.setBold(token, true);
        StyleConstants.setBackground(token, new java.awt.Color(0xe8, 0xf0, 0xfe));
        chatStyles.put("token", token);
    }

    /**
     * Renders a single message by composing its container, header and content.
     */
    private void appendMessageToDocument(AIChatMessage message) throws BadLocationException {
        Style messageStyle = resolveMessageStyle(message.getType());
        SimpleAttributeSet container = createMessageContainer(messageStyle);

        appendMessageHeader(message, container);
        appendMessageContent(message.getContent(), container);
        appendMessageSeparator();
    }

    /**
     * Resolves the style used for a message type, with fallback to base style.
     */
    private Style resolveMessageStyle(String type) {
        Style messageStyle = chatStyles.get(getMessageClass(type));
        return messageStyle != null ? messageStyle : chatArea.getStyle("base");
    }

    /**
     * Creates a paragraph/container attribute set for one rendered message.
     */
    private SimpleAttributeSet createMessageContainer(Style messageStyle) {
        SimpleAttributeSet container = new SimpleAttributeSet();
        StyleConstants.setBackground(container, StyleConstants.getBackground(messageStyle));
        StyleConstants.setLeftIndent(container, 6f);
        StyleConstants.setRightIndent(container, 6f);
        return container;
    }

    /**
     * Writes the message header line: time and sender.
     */
    private void appendMessageHeader(AIChatMessage message, SimpleAttributeSet container) throws BadLocationException {
        int start = chatDocument.getLength();
        String timeValue = message.getTime() == null ? "--:--" : message.getTime();
        String senderValue = message.getSender() == null ? "Unknown" : message.getSender();

        chatDocument.insertString(start, "[" + timeValue + "] ", chatArea.getStyle("time"));
        chatDocument.insertString(chatDocument.getLength(), senderValue + "\n", chatArea.getStyle("sender"));
        applyContainerAttributes(start, container);
    }

    /**
     * Writes message content using the markdown-aware renderer.
     */
    private void appendMessageContent(String content, SimpleAttributeSet container) throws BadLocationException {
        appendMarkdown(content == null ? "" : content, container);
    }

    /**
     * Inserts a blank line to separate message blocks.
     */
    private void appendMessageSeparator() throws BadLocationException {
        chatDocument.insertString(chatDocument.getLength(), "\n", chatArea.getStyle("base"));
    }

    /**
     * Renders markdown line by line and applies paragraph/container attributes.
     */
    private void appendMarkdown(String markdown, SimpleAttributeSet container) throws BadLocationException {
        String normalizedMarkdown = markdown.replace("\r\n", "\n").replace('\r', '\n');
        AttributeSet normalBaseStyle = createBaseStyle(false);
        AttributeSet italicBaseStyle = createBaseStyle(true);
        String[] lines = normalizedMarkdown.split("\n", -1);
        boolean inUnderscoreItalicBlock = false;

        for (String line : lines) {
            int lineStart = chatDocument.getLength();
            String lineToRender = line;
            String trimmed = lineToRender.trim();

            int firstNonWhitespace = firstNonWhitespaceIndex(lineToRender);
            if (!inUnderscoreItalicBlock && firstNonWhitespace >= 0 && lineToRender.charAt(firstNonWhitespace) == '_') {
                debug("open italic block");
                lineToRender = removeCharAt(lineToRender, firstNonWhitespace);
                inUnderscoreItalicBlock = true;
            }

            int lastNonWhitespace = lastNonWhitespaceIndex(lineToRender);
            boolean closesItalicBlock = inUnderscoreItalicBlock && lastNonWhitespace >= 0
                    && lineToRender.charAt(lastNonWhitespace) == '_';
            if (closesItalicBlock) {
                lineToRender = removeCharAt(lineToRender, lastNonWhitespace);
            }

            AttributeSet baseStyle = inUnderscoreItalicBlock ? italicBaseStyle : normalBaseStyle;
            trimmed = lineToRender.trim();

            if (trimmed.isEmpty()) {
                debugLineType("blank", lineToRender, inUnderscoreItalicBlock);
                chatDocument.insertString(chatDocument.getLength(), "\n", baseStyle);
                applyContainerAttributes(lineStart, container);
                if (closesItalicBlock) {
                    debug("close italic block");
                    inUnderscoreItalicBlock = false;
                }
                continue;
            }

            

            if (renderStandaloneItalicLine(lineToRender, baseStyle)) {
                debugLineType("standalone-italic", lineToRender, inUnderscoreItalicBlock);
                applyContainerAttributes(lineStart, container);
                if (closesItalicBlock) {
                    debug("close italic block");
                    inUnderscoreItalicBlock = false;
                }
                continue;
            }

            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                if (level > 0 && level < trimmed.length() && trimmed.charAt(level) == ' ') {
                    debugLineType("heading", lineToRender, inUnderscoreItalicBlock);
                    appendStyledLine(trimmed.substring(level + 1), combineStyles(baseStyle, chatArea.getStyle("heading")));
                    chatDocument.insertString(chatDocument.getLength(), "\n", chatArea.getStyle("base"));
                    applyContainerAttributes(lineStart, container);
                    if (closesItalicBlock) {
                        debug("close italic block");
                        inUnderscoreItalicBlock = false;
                    }
                    continue;
                }
            }

            if (trimmed.startsWith(">")) {
                debugLineType("blockquote", lineToRender, inUnderscoreItalicBlock);
                appendStyledText(trimmed.substring(1).trim() + "\n", combineStyles(baseStyle, chatArea.getStyle("blockquote")));
                applyContainerAttributes(lineStart, container);
                if (closesItalicBlock) {
                    debug("close italic block");
                    inUnderscoreItalicBlock = false;
                }
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches("\\d+\\. .*")) {
                debugLineType("list", lineToRender, inUnderscoreItalicBlock);
                appendStyledText("• ", baseStyle);
                appendInlineMarkdown(trimmed.replaceFirst("^(?:- |\\* |\\d+\\. )", ""), baseStyle);
                chatDocument.insertString(chatDocument.getLength(), "\n", baseStyle);
                applyContainerAttributes(lineStart, container);
                if (closesItalicBlock) {
                    debug("close italic block");
                    inUnderscoreItalicBlock = false;
                }
                continue;
            }

            debugLineType("normal", lineToRender, inUnderscoreItalicBlock);
            appendInlineMarkdown(lineToRender, baseStyle);
            chatDocument.insertString(chatDocument.getLength(), "\n", baseStyle);
            applyContainerAttributes(lineStart, container);
            if (closesItalicBlock) {
                debug("close italic block");
                inUnderscoreItalicBlock = false;
            }
        }
    }

    /**
     * Builds a base text style optionally enabling italic.
     */
    private AttributeSet createBaseStyle(boolean italic) {
        SimpleAttributeSet baseStyle = new SimpleAttributeSet(chatArea.getStyle("base"));
        if (italic) {
            StyleConstants.setItalic(baseStyle, true);
        }
        return baseStyle;
    }

    /**
     * Returns the index of the first non-whitespace character, or -1 if none.
     */
    private int firstNonWhitespaceIndex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the last non-whitespace character, or -1 if none.
     */
    private int lastNonWhitespaceIndex(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes one character at the given index.
     */
    private String removeCharAt(String text, int index) {
        return text.substring(0, index) + text.substring(index + 1);
    }

    /**
     * Applies container attributes to both paragraph and character ranges.
     */
    private void applyContainerAttributes(int start, SimpleAttributeSet container) throws BadLocationException {
        chatDocument.setParagraphAttributes(start, chatDocument.getLength() - start, container, false);
        chatDocument.setCharacterAttributes(start, chatDocument.getLength() - start, container, false);
    }

    /**
     * Renders a full line wrapped with '_' as italic and returns true when applied.
     */
    private boolean renderStandaloneItalicLine(String line, AttributeSet baseStyle) throws BadLocationException {
        String trimmed = line.trim();
        if (!trimmed.startsWith("_") || !trimmed.endsWith("_") || trimmed.length() < 2) {
            return false;
        }

        String innerText = trimmed.substring(1, trimmed.length() - 1).trim();
        if (innerText.isEmpty()) {
            return false;
        }

        chatDocument.insertString(chatDocument.getLength(), innerText, combineStyles(baseStyle, chatArea.getStyle("italic")));
        chatDocument.insertString(chatDocument.getLength(), "\n", baseStyle);
        return true;
    }

    /**
     * Inserts a line of styled text without appending newline automatically.
     */
    private void appendStyledLine(String text, AttributeSet style) throws BadLocationException {
        chatDocument.insertString(chatDocument.getLength(), text, style);
    }

    /**
     * Inserts styled text as provided.
     */
    private void appendStyledText(String text, AttributeSet style) throws BadLocationException {
        chatDocument.insertString(chatDocument.getLength(), text, style);
    }

    /**
     * Merges multiple attribute sets into a single set.
     */
    private AttributeSet combineStyles(AttributeSet... styles) {
        SimpleAttributeSet combined = new SimpleAttributeSet();
        for (AttributeSet style : styles) {
            if (style != null) {
                combined.addAttributes(style);
            }
        }
        return combined;
    }

    /**
     * Parses inline markdown for bold/italic markers and writes styled segments.
     */
    private void appendInlineMarkdown(String text, AttributeSet baseStyle) throws BadLocationException {
        int index = 0;
        while (index < text.length()) {
            int tokenStart = text.indexOf("<<", index);
            int boldStart = text.indexOf("**", index);
            int italicStart = findItalicStart(text, index);

            int next = smallestPositive(tokenStart, boldStart, italicStart);
            if (next == -1) {
                chatDocument.insertString(chatDocument.getLength(), text.substring(index), baseStyle);
                return;
            }

            if (next > index) {
                chatDocument.insertString(chatDocument.getLength(), text.substring(index, next), baseStyle);
            }

            if (next == tokenStart) {
                int end = text.indexOf(">>", tokenStart + 2);
                if (end > tokenStart + 2) {
                    String tokenText = text.substring(tokenStart + 2, end);
                    String[] tokenParts = tokenText.split("-", 2);
                    if (tokenParts.length == 2 && !tokenParts[0].isBlank() && !tokenParts[1].isBlank()) {
                        String fallbackVisibleText = text.substring(tokenStart, end + 2);
                        String visibleText = resolveTokenVisibleText(tokenParts[0], fallbackVisibleText);
                        appendToken(visibleText, tokenParts[0], tokenParts[1], baseStyle);
                        index = end + 2;
                        continue;
                    }
                }

                chatDocument.insertString(chatDocument.getLength(), "<<", baseStyle);
                index = tokenStart + 2;
                continue;
            }

            if (next == boldStart) {
                int end = text.indexOf("**", boldStart + 2);
                if (end > boldStart + 2) {
                    chatDocument.insertString(chatDocument.getLength(), text.substring(boldStart + 2, end), combineStyles(baseStyle, chatArea.getStyle("bold")));
                    index = end + 2;
                } else {
                    chatDocument.insertString(chatDocument.getLength(), "**", baseStyle);
                    index = boldStart + 2;
                }
                continue;
            }

            if (next == italicStart) {
                char marker = text.charAt(italicStart);
                int end = findItalicEnd(text, italicStart + 1, marker);
                if (end > italicStart + 1) {
                    chatDocument.insertString(chatDocument.getLength(), text.substring(italicStart + 1, end), combineStyles(baseStyle, chatArea.getStyle("italic")));
                    index = end + 1;
                } else {
                    chatDocument.insertString(chatDocument.getLength(), String.valueOf(marker), baseStyle);
                    index = italicStart + 1;
                }
            }
        }
    }

    /**
     * Logs line classification during markdown debug sessions.
     */
    private void debugLineType(String type, String line, boolean italicBlock) {
        debug("line type=" + type + " italicBlock=" + italicBlock + " text=[" + preview(line) + "]");
    }

    /**
     * Writes a debug message when markdown debug mode is enabled.
     */
    private void debug(String message) {
        if (isDebugMarkdownEnabled()) {
            LOGGER.info("[AIMarkdownRenderer] {}", message);
        }
    }

    /**
     * Returns true when markdown debug logs are enabled via JVM property.
     */
    private boolean isDebugMarkdownEnabled() {
        return Boolean.parseBoolean(System.getProperty("iped.debugMarkdown", "false"));
    }

    /**
     * Produces a compact preview string for logs.
     */
    private String preview(String text) {
        if (text == null) {
            return "null";
        }

        String compact = text.replace("\n", "\\n").replace("\r", "\\r");
        return compact.length() > 120 ? compact.substring(0, 120) + "..." : compact;
    }

    /**
     * Finds the next single '*' marker candidate for italic.
     */
    private int findItalicStart(String text, int fromIndex) {
        for (int i = fromIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '*' && (i + 1 >= text.length() || text.charAt(i + 1) != '*')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the matching end marker for italic.
     */
    private int findItalicEnd(String text, int fromIndex, char marker) {
        for (int i = fromIndex; i < text.length(); i++) {
            if (text.charAt(i) == marker) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns the smallest non-negative index among the provided values.
     */
    private int smallestPositive(int... values) {
        int result = -1;
        for (int value : values) {
            if (value < 0) {
                continue;
            }
            if (result < 0 || value < result) {
                result = value;
            }
        }
        return result;
    }

    /**
     * Resolves the visual text for a token without changing its click metadata.
     */
    private String resolveTokenVisibleText(String hash, String fallbackVisibleText) {
        if (hash == null || hash.isBlank()) {
            return fallbackVisibleText;
        }

        for (ContextFileEntry entry : AIContextManager.getInstance().getContextEntriesForUI()) {
            if (entry == null || !entry.isValidForContext() || entry.getItem() == null) {
                continue;
            }

            String itemHash = entry.getItem().getHash();
            if (itemHash != null && itemHash.equalsIgnoreCase(hash)) {
                String fileName = entry.getFileName();
                if (fileName != null && !fileName.isBlank()) {
                    return fileName;
                }
                break;
            }
        }

        return fallbackVisibleText;
    }

    /**
     * Writes a chunk token using a dedicated clickable style and embedded metadata.
     */
    private void appendToken(String visibleText, String hash, String chunkId, AttributeSet baseStyle) throws BadLocationException {
        SimpleAttributeSet tokenStyle = new SimpleAttributeSet(chatArea.getStyle("token"));
        tokenStyle.addAttributes(baseStyle);
        tokenStyle.addAttribute(TOKEN_ATTRIBUTE, Boolean.TRUE);
        tokenStyle.addAttribute(TOKEN_HASH_ATTRIBUTE, hash);
        tokenStyle.addAttribute(TOKEN_CHUNK_ID_ATTRIBUTE, chunkId);
        chatDocument.insertString(chatDocument.getLength(), visibleText, tokenStyle);
    }
}
