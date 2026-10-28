package com.nicosarr.jazzLibraryAPI.SyncModel;

import java.util.List;

public class SyncPage<T> {

    private List<T> items;
    private String nextCursor;   // opaque; null when hasMore == false
    private boolean hasMore;
    private int count;

    public SyncPage() {}

    public SyncPage(List<T> items, String nextCursor, boolean hasMore) {
        this.items = items != null ? items : List.of();
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.count = this.items.size();
    }

    public List<T> getItems()                    { return items; }
    public void setItems(List<T> items)          { this.items = items; }

    public String getNextCursor()                { return nextCursor; }
    public void setNextCursor(String nextCursor) { this.nextCursor = nextCursor; }

    public boolean isHasMore()                   { return hasMore; }
    public void setHasMore(boolean hasMore)      { this.hasMore = hasMore; }

    public int getCount()                        { return count; }
    public void setCount(int count)              { this.count = count; }
}