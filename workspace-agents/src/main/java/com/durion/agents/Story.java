package com.durion.agents;

/**
 * Represents a story in the story orchestration system.
 * Extracted from StoryOrchestrationAgent to follow the inner-classes-rule.md
 * guideline.
 */
public class Story {
    private final String id;
    private final String title;
    private final String type;
    private StoryClassification classification;

    public Story(String id, String title, String type) {
        this.id = id;
        this.title = title;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public StoryClassification getClassification() {
        return classification;
    }

    public void setClassification(StoryClassification classification) {
        this.classification = classification;
    }
}
