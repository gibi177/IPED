package iped.app.ui.ai;

import iped.data.IItem;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A utility service responsible for validating and extracting text content 
 * from IPED digital evidence items, specifically targeting WhatsApp HTML exports.
 * <p>
 * This class acts as a boundary layer between IPED's raw file storage and the 
 * AI Backend payload builders. It ensures that only potentially valid text data 
 * is loaded into memory and sent across the network.
 * </p>
 */
public class AIWhatsappChatExtractor {

    /**
     * <p>
     * Basic validation to check if the item is an HTML file.
     * Handles standard files and virtual files extracted from databases (which may lack extensions).
     * <p>
     * @param item The IPED evidence item to inspect.
     * @return true if the file indicates HTML or WhatsApp chat content; false otherwise.
     */
    public boolean isPotentiallyValidChat(IItem item) {
        if (item == null) return false;
        
        // Check by explicit file extension
        String ext = item.getExt();
        if (ext != null && (ext.equalsIgnoreCase("html") || ext.equalsIgnoreCase("htm"))) {
            return true;
        }
        
        // Check by IPED's Media Type (MIME type string, e.g., "text/html")
        if (item.getMediaType() != null) {
            String mimeType = item.getMediaType().getType().toLowerCase();
            if (mimeType.contains("html")) {
                return true;
            }
        }

        // Check by Virtual Filename (Fallback for .db extractions)
        String name = item.getName();
        if (name != null && name.toLowerCase().startsWith("whatsapp chat")) {
            return true;
        }
        
        return false;
    }

    /**
     * Extracts the raw file content from the IItem into a UTF-8 encoded String.
     * @param item The IPED evidence item containing the chat log.
     * @return The complete, raw HTML string.
     * @throws IllegalArgumentException if the item fails basic HTML validation.
     * @throws IllegalStateException if the underlying file stream cannot be opened.
     * @throws Exception if reading the stream fails due to an I/O error.
     */
    public String extractHtml(IItem item) throws Exception {
        if (!isPotentiallyValidChat(item)) {
            throw new IllegalArgumentException("Selected file does not appear to be an HTML chat export.");
        }

        // Try-with-resources ensures streams are closed automatically, preventing memory/file handle leaks.
        try (InputStream is = item.getBufferedInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
             
            if (is == null) {
                throw new IllegalStateException("Could not open input stream for item ID: " + item.getId());
            }

            int nRead;
            byte[] data = new byte[16384]; // 16KB chunks
            
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            // WhatsApp HTML exports heavily utilize emojis and international characters.
            // Forcing UTF-8 is strictly required here.
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}