package pewter;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collections;

/**
 * Created by jakob on 02-10-15.
 */
public class DefaultProjectResource implements ProjectResource {
    private ResourceStore resourceStore;
    private DefaultMutableTreeNode resourceNode;
    private ResourceDriver driver;

    DefaultProjectResource(ResourceStore resourceStore, DefaultMutableTreeNode resourceNode) {
        this.resourceStore = resourceStore;
        this.resourceNode = resourceNode;
    }

    @Override
    public void setDriver(ResourceDriver driver) {
        //this.driver = driver;
        ((Resource)resourceNode.getUserObject()).setDriver(driver);
    }

    @Override
    public ProjectResource getResource(String name) {
        ResourceNode node = (ResourceNode) Collections.list(resourceNode.children()).stream()
            .filter(x -> ((Resource) ((ResourceNode) x).getUserObject()).getName().equals(name))
            .findFirst().orElse(null);

        if (node != null)
            return new DefaultProjectResource(resourceStore, node);

        return null;
    }

    @Override
    public void addResource(String name, String text) {
        DefaultMutableTreeNode newResourceNode = new ResourceNode();
        Resource newResource = new Main.TreeNodeResource(resourceStore, newResourceNode, driver);
        DefaultStyledDocument content = new DefaultStyledDocument();
        try {
            content.insertString(0, text, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        newResource.setContent(content);
        newResource.setName(name);
        newResourceNode.setUserObject(newResource);

        resourceNode.add(newResourceNode);
    }
}
