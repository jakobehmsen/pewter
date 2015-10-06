package pewter;

import jdk.nashorn.api.scripting.ScriptObjectMirror;

public interface ProjectResource {
    void setDriver(ScriptObjectMirror driver);
    ProjectResource getResource(String name);
    void addResource(String name, String text);
}
