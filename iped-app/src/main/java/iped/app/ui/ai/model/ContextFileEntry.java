package iped.app.ui.ai.model;

import iped.data.IItem;

/**
 * Wrapper class for {@link IItem} to represent a file entry in the AI context.
 * <p>
 * Provides convenient methods for displaying file names, paths, and labels
 * in the UI, as well as proper {@code equals} and {@code hashCode} implementations
 * based on the unique item ID.
 * </p>
 */
public class ContextFileEntry {

    /** The underlying file item */
    private final IItem item;
    
    /** The AI-generated summary of the item (if available) */
    private final String summary;

    /** Whether this entry is valid to be sent to AI context payload */
    private final boolean validForContext;

    /** Validation reason shown in UI when entry is invalid */
    private final String validationReason;
    
    /**
     * Constructs a new {@code ContextFileEntry} wrapping the given {@link IItem}.
     *
     * @param item the item to wrap
     */
    public ContextFileEntry(IItem item) {
        this(item, extractSummary(item), true, null);
    }
    
    /**
     * Constructs a new {@code ContextFileEntry} wrapping the given {@link IItem} with explicit summary.
     *
     * @param item the item to wrap
     * @param summary the AI-generated summary (can be null)
     */
    public ContextFileEntry(IItem item, String summary) {
        this(item, summary, true, null);
    }

    private ContextFileEntry(IItem item, String summary, boolean validForContext, String validationReason) {
        this.item = item;
        this.summary = summary;
        this.validForContext = validForContext;
        this.validationReason = validationReason;
    }

    /**
     * Creates an invalid entry used only for UI feedback.
     *
     * @param item   the item that failed validation
     * @param reason the reason shown to the user
     * @return invalid context entry
     */
    public static ContextFileEntry invalid(IItem item, String reason) {
        return new ContextFileEntry(item, extractSummary(item), false, reason);
    }
    
    /**
     * Extracts the AI summary from the item's metadata.
     */
    private static String extractSummary(IItem item) {
        if (item == null) {
            return null;
        }

        Object extraValue = item.getExtraAttribute(iped.properties.ExtraProperties.SUMMARY);
        if (extraValue instanceof String) {
            String summary = ((String) extraValue).trim();
            if (!summary.isEmpty()) {
                return summary;
            }
        } else if (extraValue instanceof java.util.Collection<?>) {
            StringBuilder sb = new StringBuilder();
            for (Object value : (java.util.Collection<?>) extraValue) {
                if (value != null) {
                    String text = value.toString().trim();
                    if (!text.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(text);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        } else if (extraValue instanceof Object[]) {
            StringBuilder sb = new StringBuilder();
            for (Object value : (Object[]) extraValue) {
                if (value != null) {
                    String text = value.toString().trim();
                    if (!text.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(text);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        } else if (extraValue instanceof String[]) {
            String[] summaries = (String[]) extraValue;
            if (summaries.length > 0) {
                String joined = String.join("\n", summaries).trim();
                if (!joined.isEmpty()) {
                    return joined;
                }
            }
        }

        if (item.getMetadata() == null) {
            return null;
        }
        
        String[] summaries = item.getMetadata().getValues(iped.properties.ExtraProperties.SUMMARY);
        if (summaries != null && summaries.length > 0) {
            // Join multiple summary parts if any
            String joined = String.join("\n", summaries).trim();
            return joined.isEmpty() ? null : joined;
        }
        return null;
    }
    
    /**
     * Returns the underlying {@link IItem}.
     *
     * @return the wrapped item
     */
    public IItem getItem() {
        return item;
    }
    
    /**
     * Returns the AI-generated summary if available.
     *
     * @return the summary, or null if not available
     */
    public String getSummary() {
        return summary;
    }
    
    /**
     * Returns true if this entry has an AI-generated summary.
     *
     * @return true if summary exists
     */
    public boolean hasSummary() {
        return summary != null && !summary.trim().isEmpty();
    }

    /**
     * Returns true when the entry is valid for AI context payload.
     */
    public boolean isValidForContext() {
        return validForContext;
    }

    /**
     * Returns UI validation reason for invalid entries.
     */
    public String getValidationReason() {
        return validationReason;
    }
    
    /**
     * Returns the displayable file name, or "Unknown File" if {@code null}.
     *
     * @return file name
     */
    public String getFileName() {
        return item.getName() != null ? item.getName() : "Unknown File";
    }
    
    /**
     * Returns the full file path, or an empty string if {@code null}.
     *
     * @return full path
     */
    public String getFullPath() {
        return item.getPath() != null ? item.getPath() : "";
    }
    
    @Override
    public String toString() {
        if (!validForContext && validationReason != null && !validationReason.trim().isEmpty()) {
            return getFileName() + " - " + validationReason;
        }
        if (hasSummary()) {
            return getFileName() + " [Summary]";
        }
        return getFileName();
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ContextFileEntry)) return false;
        return this.item.getId() == ((ContextFileEntry) o).item.getId();
    }
    
    @Override
    public int hashCode() {
        // Use Integer wrapper to get the hashcode of a primitive int
        return Integer.hashCode(item.getId());
    }
}