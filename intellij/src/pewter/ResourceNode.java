package pewter;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Created by jakob on 02-10-15.
 */
public class ResourceNode extends DefaultMutableTreeNode {
    public ResourceNode() {
    }

    public ResourceNode(Resource resource) {
        super(resource);
    }

    @Override
    public void setUserObject(Object userObject) {
        if (userObject instanceof String) {
            ((Resource) getUserObject()).setName((String) userObject);
        } else
            super.setUserObject(userObject);
    }
}
