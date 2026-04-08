package iped.app.ui.ai;

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
    
    /**
     * Constructs a new {@code ContextFileEntry} wrapping the given {@link IItem}.
     *
     * @param item the item to wrap
     */
    public ContextFileEntry(IItem item) {
        this.item = item;
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
    
    /**
     * Returns the label used for display in UI lists.
     *
     * @return display label
     */
    public String getDisplayLabel() {
        return getFileName();
    }
    
    @Override
    public String toString() {
        return getDisplayLabel();
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