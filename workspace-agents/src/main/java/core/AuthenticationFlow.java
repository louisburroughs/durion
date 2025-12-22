package core;

public class AuthenticationFlow {
    private final boolean jwtValidation;
    private final boolean tokenConsistency;
    private final boolean secure;

    public AuthenticationFlow(boolean jwtValidation, boolean tokenConsistency, boolean secure) {
        this.jwtValidation = jwtValidation;
        this.tokenConsistency = tokenConsistency;
        this.secure = secure;
    }

    public boolean hasJwtValidation() { return jwtValidation; }
    public boolean hasTokenConsistency() { return tokenConsistency; }
    public boolean isSecure() { return secure; }
}
