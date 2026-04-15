package iped.app.ui.ai;

import iped.data.IItem;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builds optimized payloads for LLM consumption from context files.
 * <p>
 * This class takes the list of context files (which may contain AI-generated summaries)
 * and prepares them for efficient transmission to the LLM backend. It prioritizes
 * AI summaries (already compact) over raw HTML, and structures the data as JSON
 * for clarity and reduced token usage.
 * </p>
 */
public class AIConversationPayloadBuilder {

    /**
     * Builds a JSON payload from context entries, optimized for LLM consumption.
     * <p>
     * Strategy:
     * - If item has AI summary: use it (already condensed by LLM)
     * - If no summary: fall back to full HTML content (future optimization)
     * - Group by type/source for better context
     * </p>
     *
     * @param entries List of context file entries
     * @return JSON string ready for backend API
     * @throws Exception if payload building fails
     */
    public static String buildPayload(List<ContextFileEntry> entries) throws Exception {
        JSONObject payload = new JSONObject();
        JSONArray conversations = new JSONArray();
        long validCount = entries.stream().filter(ContextFileEntry::isValidForContext).count();
        boolean singleItemMode = validCount <= 1;

        for (ContextFileEntry entry : entries) {
            if (!entry.isValidForContext()) {
                continue;
            }
            JSONObject conv = new JSONObject();
            
            IItem item = entry.getItem();
            conv.put("itemId", item.getId());
            conv.put("fileName", entry.getFileName());
            conv.put("path", entry.getFullPath());
            conv.put("type", item.getMediaType() != null ? item.getMediaType().toString() : "unknown");

            // Single item mode: keep only item reference/context, do not send HTML/summary text.
            if (singleItemMode) {
                conv.put("source", "ITEM_ONLY");
                conv.put("content", "");
                conv.put("contentLength", 0);
                conv.put("note", "Single context item: content text intentionally omitted.");
            } else {
                // Multiple item mode: use summary when available, otherwise keep only item reference.
                if (entry.hasSummary()) {
                    conv.put("source", "SUMMARY");
                    conv.put("content", entry.getSummary());
                    conv.put("contentLength", entry.getSummary().length());
                } else {
                    conv.put("source", "ITEM_ONLY");
                    conv.put("content", "");
                    conv.put("contentLength", 0);
                    conv.put("note", "Summary not available for this item.");
                }
            }
            
            conversations.put(conv);
        }

        payload.put("conversations", conversations);
        payload.put("totalItems", validCount);
        payload.put("singleItemMode", singleItemMode);
        payload.put("summariesIncluded", entries.stream()
            .filter(ContextFileEntry::isValidForContext)
            .filter(ContextFileEntry::hasSummary)
            .count());
        
        return payload.toString(2);
    }

    /**
     * Builds a compact text payload (alternative to JSON) for simpler APIs.
     * Each conversation is represented as: [FileName] Summary\n\n
     */
    public static String buildCompactPayload(List<ContextFileEntry> entries) {
        StringBuilder sb = new StringBuilder();
        long validCount = entries.stream().filter(ContextFileEntry::isValidForContext).count();
        boolean singleItemMode = validCount <= 1;
        
        for (ContextFileEntry entry : entries) {
            if (!entry.isValidForContext()) {
                continue;
            }
            sb.append("=== ").append(entry.getFileName()).append(" ===\n");

            if (singleItemMode) {
                sb.append("[Single context item: content text omitted]");
            } else if (entry.hasSummary()) {
                sb.append(entry.getSummary());
            } else {
                sb.append("[No summary available for this item]");
            }
            
            sb.append("\n\n");
        }
        
        return sb.toString();
    }

    /**
     * Calculates token estimate for the payload (rough approximation: ~4 chars per token).
     */
    public static int estimateTokens(String payload) {
        return Math.max(1, payload.length() / 4);
    }

    /**
     * Returns statistics about the payload for debugging/logging.
     */
    public static String getPayloadStats(List<ContextFileEntry> entries) {
        long totalChars = 0;
        int validEntries = 0;
        int rejectedEntries = 0;
        int withSummary = 0;
        int withoutSummary = 0;

        for (ContextFileEntry entry : entries) {
            if (!entry.isValidForContext()) {
                rejectedEntries++;
                continue;
            }

            validEntries++;
            if (entry.hasSummary()) {
                withSummary++;
                totalChars += entry.getSummary().length();
            } else {
                withoutSummary++;
            }
        }

        int estimatedTokens = estimateTokens("x".repeat((int)totalChars));
        
        return String.format(
            "Payload Stats: %d valid items, %d rejected, %d with summaries, %d without. Total: %d chars, ~%d tokens estimated.",
            validEntries, rejectedEntries, withSummary, withoutSummary, totalChars, estimatedTokens
        );
    }
}
