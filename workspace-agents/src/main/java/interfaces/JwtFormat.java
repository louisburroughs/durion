package interfaces;

import java.util.Set;

public class JwtFormat {
    private final String project;
    private final String algorithm;
    private final Set<String> claims;

    public JwtFormat(String project, String algorithm, Set<String> claims) {
        this.project = project;
        this.algorithm = algorithm;
        this.claims = claims;
    }

    public String getProject() { return project; }
    public String getAlgorithm() { return algorithm; }
    public Set<String> getClaims() { return claims; }
}
