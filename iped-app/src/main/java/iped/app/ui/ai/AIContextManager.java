package iped.app.ui.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;

import iped.data.IItem;

/**
 * Singleton manager responsible for maintaining the AI context file list.
 * <p>
 * This class provides thread-safe operations for adding, removing, and clearing
 * context files used by the AI. It also implements an event-listener mechanism
 * to notify interested components whenever the context changes.
 * </p>
 * 
 * <p>
 * Internally, it uses a {@link CopyOnWriteArrayList} to ensure safe concurrent access
 * without explicit synchronization for read-heavy operations.
 * </p>
 * 
 * <p>
 * All UI-related notifications are dispatched on the Swing Event Dispatch Thread (EDT).
 * </p>
 */
public class AIContextManager {

    /** Singleton instance */
    private static AIContextManager instance;
    
    /** Thread-safe list holding context files */
    private final List<IItem> contextFiles;
    
    /** Listener list for context change events */
    private final EventListenerList listeners;
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private AIContextManager() {
        this.contextFiles = new CopyOnWriteArrayList<>();
        this.listeners = new EventListenerList();
    }
    
    /**
     * Returns the singleton instance of the manager.
     *
     * @return the single {@code AIContextManager} instance
     */
    public static synchronized AIContextManager getInstance() {
        if (instance == null) {
            instance = new AIContextManager();
        }
        return instance;
    }
    
    /**
     * Adds a file to the context if it is not already present.
     *
     * @param item the item to add; ignored if {@code null}
     */
    public void addContextFile(IItem item) {
        if (item == null) return;
        
        boolean alreadyExists = contextFiles.stream()
            .anyMatch(existing -> existing.getId() == item.getId());
        
        if (!alreadyExists) {
            contextFiles.add(item);
            fireContextChanged(ContextChangeEvent.FILE_ADDED);
        }
    }
    
    /**
     * Removes a file from the context based on its ID.
     *
     * @param item the item to remove
     */
    public void removeContextFile(IItem item) {
        if (contextFiles.removeIf(existing -> existing.getId() == item.getId())) {
            fireContextChanged(ContextChangeEvent.FILE_REMOVED);
        }
    }
    
    /**
     * Clears all context files.
     */
    public void clearContext() {
        if (!contextFiles.isEmpty()) {
            contextFiles.clear();
            fireContextChanged(ContextChangeEvent.CLEARED);
        }
    }
    
    /**
     * Returns a copy of the current context file list.
     *
     * @return a new list containing all context files
     */
    public List<IItem> getContextFiles() {
        return new ArrayList<>(contextFiles);
    }
    
    /**
     * Registers a listener to receive context change events.
     *
     * @param listener the listener to add
     */
    public void addContextChangeListener(ContextChangeListener listener) {
        listeners.add(ContextChangeListener.class, listener);
    }
    
    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeContextChangeListener(ContextChangeListener listener) {
        listeners.remove(ContextChangeListener.class, listener);
    }
    
    /**
     * Notifies all registered listeners about a context change.
     * <p>
     * Ensures that notifications are dispatched on the Swing EDT.
     * </p>
     *
     * @param changeType the type of change that occurred
     */
    private void fireContextChanged(String changeType) {
        ContextChangeEvent event = new ContextChangeEvent(this, changeType);
        Runnable notification = () -> {
            for (ContextChangeListener listener : listeners.getListeners(ContextChangeListener.class)) {
                listener.contextChanged(event);
            }
        };
        
        if (SwingUtilities.isEventDispatchThread()) {
            notification.run();
        } else {
            SwingUtilities.invokeLater(notification);
        }
    }

    /**
     * Batch adds multiple files to the context.
     * <p>
     * This method avoids UI thread starvation by processing all additions first
     * and firing a single change event at the end.
     * </p>
     *
     * @param items list of items to add; ignored if {@code null} or empty
     */
    public void addContextFiles(List<IItem> items) {
        if (items == null || items.isEmpty()) return;
        
        boolean addedAny = false;
        
        for (IItem item : items) {
            if (item == null) continue;
            
            // Deduplication check
            boolean alreadyExists = contextFiles.stream()
                .anyMatch(existing -> existing.getId() == item.getId());
                
            if (!alreadyExists) {
                contextFiles.add(item);
                addedAny = true;
            }
        }
        
        // Fire a single event after the entire batch is processed
        if (addedAny) {
            fireContextChanged(ContextChangeEvent.FILE_ADDED);
        }
    }
}