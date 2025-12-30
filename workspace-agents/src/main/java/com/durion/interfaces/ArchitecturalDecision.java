package com.durion.interfaces;

public class ArchitecturalDecision {
    private final String technology;
    private final String pattern;
    private final String project;

    public ArchitecturalDecision(String technology, String pattern, String project) {
        this.technology = technology;
        this.pattern = pattern;
        this.project = project;
    }

    public String getTechnology() { return technology; }
    public String getPattern() { return pattern; }
    public String getProject() { return project; }
}
