package pewter;

/**
 * Created by jakob on 02-10-15.
 */
public interface ProjectResource {
    ProjectResource getResource(String name);

    void addResource(String name, String text);
}
