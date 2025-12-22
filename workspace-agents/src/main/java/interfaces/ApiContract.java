package interfaces;

public class ApiContract {
    private final String project;
    private final String endpoint;
    private final String method;
    private final String format;

    public ApiContract(String project, String endpoint, String method, String format) {
        this.project = project;
        this.endpoint = endpoint;
        this.method = method;
        this.format = format;
    }

    public String getProject() { return project; }
    public String getEndpoint() { return endpoint; }
    public String getMethod() { return method; }
    public String getFormat() { return format; }
}
