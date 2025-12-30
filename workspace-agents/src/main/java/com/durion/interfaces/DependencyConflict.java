package com.durion.interfaces;

public class DependencyConflict {
    private final String name;
    private final String versionA;
    private final String versionB;
    private final boolean conflict;

    public DependencyConflict(String name, String versionA, String versionB, boolean conflict) {
        this.name = name;
        this.versionA = versionA;
        this.versionB = versionB;
        this.conflict = conflict;
    }

    public String getName() { return name; }
    public String getVersionA() { return versionA; }
    public String getVersionB() { return versionB; }
    public boolean isConflict() { return conflict; }
}
