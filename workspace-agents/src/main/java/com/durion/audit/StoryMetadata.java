package com.durion.audit;

import java.util.Objects;

/**
 * Metadata about a story issue extracted from coordination files
 * (frontend-coordination.md and backend-coordination.md).
 * Contains story number, title, URL, and readiness flags for frontend and
 * backend implementation.
 */
public class StoryMetadata {
    private final int storyNumber;
    private final String title;
    private final String url;
    private final boolean readyForFrontend;
    private final boolean readyForBackend;

    /**
     * Creates a new StoryMetadata instance.
     * 
     * @param storyNumber      Story issue number
     * @param title            Story title (may include [STORY] prefix)
     * @param url              GitHub URL to the story issue
     * @param readyForFrontend Whether the story is ready for frontend
     *                         implementation
     * @param readyForBackend  Whether the story is ready for backend implementation
     */
    public StoryMetadata(int storyNumber, String title, String url, boolean readyForFrontend, boolean readyForBackend) {
        this.storyNumber = storyNumber;
        this.title = Objects.requireNonNull(title, "Story title cannot be null");
        this.url = Objects.requireNonNull(url, "Story URL cannot be null");
        this.readyForFrontend = readyForFrontend;
        this.readyForBackend = readyForBackend;
    }

    public int getStoryNumber() {
        return storyNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public boolean isReadyForFrontend() {
        return readyForFrontend;
    }

    public boolean isReadyForBackend() {
        return readyForBackend;
    }

    /**
     * Removes the [STORY] prefix from the title if present.
     */
    public String getCleanTitle() {
        if (title.startsWith("[STORY] ")) {
            return title.substring(8);
        }
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StoryMetadata that = (StoryMetadata) o;
        return storyNumber == that.storyNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(storyNumber);
    }

    @Override
    public String toString() {
        return String.format("StoryMetadata{#%d: %s, frontend=%b, backend=%b}",
                storyNumber, title, readyForFrontend, readyForBackend);
    }
}