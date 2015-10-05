package pewter;

public interface ProjectResource {
    void setDriver(ResourceDriver driver);
    ProjectResource getResource(String name);
    void addResource(String name, String text);
}
