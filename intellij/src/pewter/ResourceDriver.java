package pewter;

public interface ResourceDriver {
    ResourceDriver addResource(String name, String text);
    void removeResource(String name);
    void setName(String name);
    void setText(String text);
}
