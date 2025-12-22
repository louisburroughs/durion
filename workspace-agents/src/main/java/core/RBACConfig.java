package core;

import java.util.*;

public class RBACConfig {
    private final String role;
    private final java.util.List<String> permissions;

    public RBACConfig(String role, java.util.List<String> permissions) {
        this.role = role;
        this.permissions = new ArrayList<>(permissions);
    }

    public String getRole() { return role; }
    public java.util.List<String> getPermissions() { return Collections.unmodifiableList(permissions); }
}
