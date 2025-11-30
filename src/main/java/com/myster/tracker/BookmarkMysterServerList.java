package com.myster.tracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.general.events.NewGenericDispatcher;
import com.myster.tracker.Tracker.ListChangedListener;

/**
 * While the {@link MysterTypeServerList} tracks servers that seem to be good
 * candidates for files for a given type and the {@link LanMysterServerList}
 * tracks all servers on the LAN. The {@link BookmarkMysterServerList} tracks
 * all servers that a user has explicitly requested be tracked.
 * 
 * Each bookmark can have metadata like an alternative name (user-chosen display name)
 * and potentially a folder for organization.
 */
public class BookmarkMysterServerList {
    private static final Logger LOGGER = Logger.getLogger(BookmarkMysterServerList.class.getName());
    private static final String BOOKMARK_PREF_KEY = "Bookmarks"; // the place where bookmarked servers are stored
    private static final String ALT_NAME_KEY = "altName";
    private static final String FOLDER_KEY = "folder";
    
    private final MysterServerPool pool;
    private final NewGenericDispatcher<ListChangedListener> dispatcher;
    private final Preferences preferences;
    private final Map<MysterIdentity, Bookmark> bookmarks = new LinkedHashMap<>();

    /**
     * Immutable bookmark record containing server identity and optional metadata.
     */
    public record Bookmark(
        MysterIdentity identity,
        Optional<String> alternativeName,
        Optional<String> folder
    ) {
        public Bookmark(MysterIdentity identity) {
           this(identity, Optional.empty(), Optional.empty());
        }
        
        /**
         * Creates a new bookmark with updated alternative name.
         */
        public Bookmark withAlternativeName(String alternativeName) {
            return new Bookmark(identity, Optional.ofNullable(alternativeName), folder);
        }
        
        /**
         * Creates a new bookmark with updated folder.
         */
        public Bookmark withFolder(String folder) {
            return new Bookmark(identity, alternativeName, Optional.ofNullable(folder));
        }
    }

    public BookmarkMysterServerList(MysterServerPool pool,
                                    Preferences preferences,
                                    NewGenericDispatcher<ListChangedListener> dispatcher) {
        this.pool = pool;
        this.preferences = preferences.node(BOOKMARK_PREF_KEY);
        this.dispatcher = dispatcher;
        
        loadBookmarks();
    }

    /**
     * Loads bookmarks from preferences.
     */
    private void loadBookmarks() {
        String bookmarkList = preferences.get("list", "");
        if (bookmarkList.isEmpty()) {
            return;
        }
        
        StringTokenizer tokenizer = new StringTokenizer(bookmarkList);
        while (tokenizer.hasMoreTokens()) {
            try {
                ExternalName externalName = new ExternalName(tokenizer.nextToken());
                pool.lookupIdentityFromName(externalName)
                    .filter(identity -> pool.existsInPool(identity))
                    .flatMap(identity -> pool.getCachedMysterServer(identity))
                    .ifPresentOrElse(
                        server -> {
                            Bookmark bookmark = loadBookmarkMetadata(externalName, server.getIdentity());
                            bookmarks.put(server.getIdentity(), bookmark);
                        },
                        () -> LOGGER.warning("Bookmarked server does not exist in pool: " + externalName + ". Repairing.")
                    );
            } catch (Exception ex) {
                LOGGER.warning("Failed to load bookmark: " + ex);
            }
        }
    }

    /**
     * Loads metadata for a specific bookmark from preferences.
     */
    private Bookmark loadBookmarkMetadata(ExternalName externalName, MysterIdentity identity) {
        Preferences bookmarkNode = preferences.node(externalName.toString());
        
        String altName = bookmarkNode.get(ALT_NAME_KEY, null);
        String folder = bookmarkNode.get(FOLDER_KEY, null);
        
        return new Bookmark(identity).withAlternativeName(altName).withFolder(folder);
    }

    /**
     * Saves bookmarks to preferences.
     */
    private synchronized void save() {
        StringBuilder buffer = new StringBuilder();
        
        for (MysterIdentity identity : bookmarks.keySet()) {
            pool.getCachedMysterServer(identity).ifPresent(server -> {
                ExternalName externalName = server.getExternalName();
                buffer.append(externalName).append(" ");
                
                // Save metadata for this bookmark
                Bookmark bookmark = bookmarks.get(identity);
                Preferences bookmarkNode = preferences.node(externalName.toString());
                
                if (bookmark.alternativeName().isPresent()) {
                    bookmarkNode.put(ALT_NAME_KEY, bookmark.alternativeName().get());
                } else {
                    bookmarkNode.remove(ALT_NAME_KEY);
                }
                
                if (bookmark.folder().isPresent()) {
                    bookmarkNode.put(FOLDER_KEY, bookmark.folder().get());
                } else {
                    bookmarkNode.remove(FOLDER_KEY);
                }
            });
        }
        
        preferences.put("list", buffer.toString().trim());
        
        try {
            preferences.flush();
        } catch (BackingStoreException exception) {
            LOGGER.warning("Failed to save bookmarks: " + exception);
        }
    }

    /**
     * Returns all bookmarked servers.
     */
    public synchronized List<MysterServer> getAll() {
        List<MysterServer> servers = new ArrayList<>();
        
        for (MysterIdentity identity : bookmarks.keySet()) {
            pool.getCachedMysterServer(identity).ifPresent(servers::add);
        }
        
        return servers;
    }

    /**
     * Adds or updates a bookmark.
     * 
     * @param bookmark The bookmark to add or update
     */
    public synchronized void addBookmark(Bookmark bookmark) {
        if (!pool.existsInPool(bookmark.identity())) {
            LOGGER.warning("Cannot bookmark server that doesn't exist in pool: " + bookmark.identity());
            return;
        }
        
        bookmarks.put(bookmark.identity(), bookmark);
        
        save();
        notifyListChanged();
    }
    
    /**
     * Removes a server from bookmarks.
     * 
     * @param identity The identity of the server to remove from bookmarks
     */
    public synchronized void removeBookmark(MysterIdentity identity) {
        if (bookmarks.remove(identity) != null) {
            // Clean up the preferences node for this bookmark
            pool.getCachedMysterServer(identity).ifPresent(server -> {
                try {
                    Preferences bookmarkNode = preferences.node(server.getExternalName().toString());
                    bookmarkNode.removeNode();
                } catch (BackingStoreException e) {
                    LOGGER.warning("Failed to remove bookmark node: " + e);
                }
            });
            
            save();
            notifyListChanged();
        }
    }
    
    /**
     * Checks if a server is bookmarked.
     * 
     * @param identity The identity to check
     * @return true if the server is bookmarked
     */
    public synchronized boolean isBookmarked(MysterIdentity identity) {
        return bookmarks.containsKey(identity);
    }
    
    /**
     * Gets the bookmark for a bookmarked server.
     * 
     * @param identity The identity of the server
     * @return Optional containing the bookmark if the server is bookmarked
     */
    public synchronized Optional<Bookmark> getBookmark(MysterIdentity identity) {
        return Optional.ofNullable(bookmarks.get(identity));
    }
    
    /**
     * Notifies listeners that a server has been removed from the pool.
     */
    public synchronized void notifyDeadServer(MysterIdentity identity) {
        if (bookmarks.containsKey(identity)) {
            // Don't remove the bookmark, but it won't be returned by getAll()
            // until the server comes back online
            notifyListChanged();
        }
    }
    
    private void notifyListChanged() {
        dispatcher.fire().bookmarkServerAddedRemoved();
    }
}
