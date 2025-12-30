package com.durion.core;

import java.util.*;

public class JWTStructure {
    private final String project;
    private final String algorithm;
    private final List<String> claims;

    public JWTStructure(String project, String algorithm, List<String> claims) {
        this.project = project;
        this.algorithm = algorithm;
        this.claims = new ArrayList<>(claims);
    }

    public String getProject() { return project; }
    public String getAlgorithm() { return algorithm; }
    public List<String> getClaims() { return Collections.unmodifiableList(claims); }
}
