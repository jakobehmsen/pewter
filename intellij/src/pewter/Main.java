package pewter;

import com.sun.glass.events.KeyEvent;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.activation.DataHandler;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private interface Language {
        void attachTo(Object content, JPanel panel);
        void dettachFrom(Object content, JPanel panel);
        Object newContent();
        Object evaluate(Object content);
    }

    private interface ResourceListener {
        void nameChanged(Resource resource);
    }

    private interface Resource extends Language {
        void addListener(ResourceListener listener);
        void removeListener(ResourceListener listener);
        default Runnable bindListener(ResourceListener listener) {
            addListener(listener);
            return () -> removeListener(listener);
        }
        Language getLanguage();
        String getName();
        void setName(String name);
        Object getContent();
        void setContent(Object content);
        default Object evaluate() {
            return getLanguage().evaluate(getContent());
        }
        default void attachTo(JPanel panel) {
            getLanguage().attachTo(getContent(), panel);
        }
        default void detachFrom(JPanel panel) {
            getLanguage().dettachFrom(getContent(), panel);
        }
    }

    private interface ResourceStore {
        java.util.List<Resource> getAllResources();
    }

    private static class ResourceNode extends DefaultMutableTreeNode {
        public ResourceNode() { }

        public ResourceNode(Resource resource) {
            super(resource);
        }

        @Override
        public void setUserObject(Object userObject) {
            if(userObject instanceof String) {
                ((Resource)getUserObject()).setName((String)userObject);
            } else
                super.setUserObject(userObject);
        }
    }

    private static class ResourceView extends JLabel implements ResourceListener {
        private Resource resource;

        public ResourceView(Resource resource) {
            this.resource = resource;
            nameChanged(resource);
            resource.addListener(this);
        }

        public void unbind() {
            resource.removeListener(this);
        }

        @Override
        public void nameChanged(Resource resource) {
            setText(resource.getName());
        }
    }

    private static abstract class AbstractResource implements Resource {
        private ResourceStore resourceStore;
        private String name;
        private Object content;
        private List<ResourceListener> listeners = new ArrayList<ResourceListener>();

        public AbstractResource(ResourceStore resourceStore) {
            this.resourceStore = resourceStore;
        }

        @Override
        public void addListener(ResourceListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(ResourceListener listener) {
            listeners.remove(listener);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
            listeners.forEach(x -> x.nameChanged(this));
        }

        @Override
        public Object getContent() {
            return content;
        }

        @Override
        public void setContent(Object content) {
            this.content = content;
        }

        @Override
        public void attachTo(Object content, JPanel panel) {
            JTextPane textPane = new JTextPane();
            textPane.setDocument((Document) content);
            ((DefaultStyledDocument)textPane.getStyledDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                    Element ce = textPane.getStyledDocument().getCharacterElement(offset);

                    if (ce.getAttributes().containsAttribute(AbstractDocument.ElementNameAttribute, StyleConstants.ComponentElementName)) {
                        ResourceView lblComp = (ResourceView) StyleConstants.getComponent(ce.getAttributes());
                        lblComp.unbind();
                    }

                    super.remove(fb, offset, length);
                }
            });
            textPane.getKeymap().addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JPopupMenu candidateReferences = new JPopupMenu();

                    resourceStore.getAllResources().forEach(x -> {
                        candidateReferences.add(new AbstractAction(x.getName()) {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                //int pos = textPane.getCaretPosition();
                                //((DefaultStyledDocument)textPane.getDocument()).getParagraphElement(pos).
                                ResourceView lbl = new ResourceView(x);
                                lbl.setAlignmentY(0.85f);
                                lbl.setForeground(Color.BLUE);
                                lbl.setFont(textPane.getFont());
                                textPane.insertComponent(lbl);
                            }
                        });
                    });

                    try {
                        Rectangle caretPosition = textPane.modelToView(textPane.getCaretPosition());
                        candidateReferences.show(textPane, caretPosition.x, caretPosition.y);
                    } catch (BadLocationException e1) {
                        e1.printStackTrace();
                    }
                }
            });
            panel.add(new JScrollPane(textPane), BorderLayout.CENTER);
        }

        @Override
        public void dettachFrom(Object content, JPanel panel) {

        }

        @Override
        public Object newContent() {
            return new DefaultStyledDocument();
        }

        @Override
        public Object evaluate() {
            return getLanguage().evaluate(getContent());
        }

        @Override
        public Object evaluate(Object content) {
            ScriptObjectMirror evaluator = (ScriptObjectMirror)evaluate();
            String sourceCode = getText((StyledDocument)content);

            Input input = new CharSequenceInput(sourceCode);
            ListOutput acceptOutput = new ListOutput(new ArrayList<>());
            ListOutput rejectOutput = new ListOutput(new ArrayList<>());

            boolean accepted = (boolean)evaluator.callMember("eval", input, acceptOutput, rejectOutput);

            if(accepted) {
                //String result = acceptOutput.getList().stream().map(x -> x.toString()).collect(Collectors.joining(", "));
                //return acceptOutput.getList().get(0);
                return acceptOutput.getList();
            } else {
                return "REJECT";
            }
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private static void createExample(JTree overviewPanelActionsResources, ResourceStore resourceStore) {
        DefaultMutableTreeNode examplePattern = newResource(overviewPanelActionsResources, resourceStore, (DefaultMutableTreeNode) overviewPanelActionsResources.getModel().getRoot());
        try {
            String text = new String(java.nio.file.Files.readAllBytes(Paths.get("src/parsers/Example")));
            ((Document)((Resource)examplePattern.getUserObject()).getContent()).insertString(0, text, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        DefaultMutableTreeNode examplePatternExample = newResource(overviewPanelActionsResources, resourceStore, examplePattern);
        try {
            String text = "HI THERE";
            ((Document)((Resource)examplePatternExample.getUserObject()).getContent()).insertString(0, text, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private static DefaultMutableTreeNode newResource(JTree overviewPanelActionsResources, ResourceStore resourceStore, DefaultMutableTreeNode parent) {
        DefaultMutableTreeNode parentResourceNode = (DefaultMutableTreeNode)parent;//(DefaultMutableTreeNode)overviewPanelActionsResources.getSelectionPath().getLastPathComponent();

        DefaultMutableTreeNode resourceNode = new ResourceNode();
        Resource resource = new AbstractResource(resourceStore) {
            @Override
            public Language getLanguage() {
                return (Resource)((DefaultMutableTreeNode)resourceNode.getParent()).getUserObject();
            }
        };
        resource.setContent(new DefaultStyledDocument());
        resource.setName("Example" + (parentResourceNode.getChildCount() + 1));
        resourceNode.setUserObject(resource);

        overviewPanelActionsResources.getModel().addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {

            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                /*if(e.getTreePath().getLastPathComponent().equals(resourceNode)) {
                    overviewPanelActionsResources.expandPath(e.getTreePath());
                    overviewPanelActionsResources.getModel().removeTreeModelListener(this);
                }*/
                overviewPanelActionsResources.expandPath(e.getTreePath());
                overviewPanelActionsResources.getModel().removeTreeModelListener(this);
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {

            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {

            }
        });

        ((DefaultTreeModel) overviewPanelActionsResources.getModel()).insertNodeInto(resourceNode, parentResourceNode, parentResourceNode.getChildCount());

        return resourceNode;
    }

    private static JTree overviewPanelActionsResources;

    private static String getText(StyledDocument document) {
        // Should be for loop where i is update according to ce.getEndOffset() for non-component elements
        StringBuilder textBuilder = new StringBuilder();
        int i = 0;

        while(i < document.getLength()) {
            Element ce = document.getCharacterElement(i);

            if (ce.getAttributes().containsAttribute(AbstractDocument.ElementNameAttribute, StyleConstants.ComponentElementName)) {
                JLabel lblComp = (JLabel) StyleConstants.getComponent(ce.getAttributes());
                textBuilder.append(lblComp.getText());
                i++;
            } else {
                try {
                    String textPart = document.getText(i, ce.getEndOffset() - i);
                    textBuilder.append(textPart);
                    i = ce.getEndOffset();
                } catch (BadLocationException e1) {
                    e1.printStackTrace();
                }
            }
        }

        return textBuilder.toString();
    }

    public static void main(String[] args) throws Exception {
        ScriptEngineManager engineManager = new ScriptEngineManager();

        JFrame frame = new JFrame();

        DefaultListModel<Resource> resources = new DefaultListModel<>();

        ResourceStore resourceStore = new ResourceStore() {
            @Override
            public List<Resource> getAllResources() {
                return (List<Resource>) Collections.list(
                    ((DefaultMutableTreeNode) ((DefaultTreeModel) overviewPanelActionsResources.getModel()).getRoot()).breadthFirstEnumeration()
                ).stream().map(x -> (Resource) ((DefaultMutableTreeNode)x).getUserObject()).map(Resource.class::cast).collect(Collectors.toList());
            }
        };

        Resource nashhornResource = new AbstractResource(resourceStore) {
            @Override
            public void dettachFrom(Object content, JPanel panel) {

            }

            @Override
            public Object newContent() {
                return new DefaultStyledDocument();
            }

            @Override
            public Object evaluate(Object content) {
                try {
                    ScriptEngine engine = engineManager.getEngineByName("nashorn");

                    String sourceCode = getText((StyledDocument)content);
                    engine.eval(sourceCode);

                    return ((Invocable)engine).invokeFunction("main");
                } catch (ScriptException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            public Language getLanguage() {
                return new Language() {
                    @Override
                    public void attachTo(Object content, JPanel panel) {
                        JTextPane textPane = new JTextPane();
                        textPane.setText(content.toString());
                        textPane.setEditable(false);
                        panel.add(textPane, BorderLayout.CENTER);
                    }

                    @Override
                    public void dettachFrom(Object content, JPanel panel) {

                    }

                    @Override
                    public Object newContent() {
                        return "Nashorn";
                    }

                    @Override
                    public Object evaluate(Object content) {
                        return content.toString();
                    }
                };
            }
        };
        nashhornResource.setName("Nashorn");
        nashhornResource.setContent(nashhornResource.getLanguage().newContent());
        DefaultComboBoxModel<Language> languages = new DefaultComboBoxModel<>();

        JPanel editorPanel = new JPanel(new BorderLayout());

        /*setAsDropTarget(editorPanel, (component, language) -> {
            Resource resource = new Resource() {
                String name = "Resource" + resources.size();
                Object content = language.newContent();

                @Override
                public Language getLanguage() {
                    return language;
                }

                public String getName() {
                    return name;
                }

                @Override
                public void attachTo(Object content, JPanel panel) {

                }

                @Override
                public void dettachFrom(Object content, JPanel panel) {

                }

                @Override
                public Object newContent() {
                    return null;
                }

                @Override
                public Object evaluate(Object content) {
                    return null;
                }

                @Override
                public void setName(String name) {
                    this.name = name;
                }

                @Override
                public Object getContent() {
                    return content;
                }

                @Override
                public void setContent(Object content) {
                    this.content = content;
                }

                @Override
                public String toString() {
                    return getName();
                }
            };
            resources.addElement(resource);
            setResource(editorPanel, languages, resource);
        });*/

        JPanel overviewPanel = new JPanel(new BorderLayout());
        JPanel overviewPanelActions = new JPanel();
        overviewPanelActions.add(new JButton("Add language..."));
        overviewPanelActions.add(new JButton("Add text..."));
        overviewPanel.add(overviewPanelActions, BorderLayout.NORTH);
        JList overviewPanelActionsLanguages = new JList<Language>(languages);
        overviewPanelActionsLanguages.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return TransferHandler.LINK;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                JList source = (JList) c;
                return new DataHandler(source.getSelectedValue(), DataFlavor.javaJVMLocalObjectMimeType);
            }
        });
        overviewPanelActionsLanguages.setDragEnabled(true);
        overviewPanelActionsLanguages.setDropMode(DropMode.ON);

        overviewPanelActionsResources = new JTree(new ResourceNode(nashhornResource));
        overviewPanelActionsResources.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                if (overviewPanelActionsResources.getSelectionCount() == 1) {
                    DefaultMutableTreeNode resourceNode = (DefaultMutableTreeNode) overviewPanelActionsResources.getSelectionPath().getLastPathComponent();
                    Resource resource = (Resource) resourceNode.getUserObject();
                    setResource(editorPanel, languages, resource);
                } else {
                    setResource(editorPanel, languages, null);
                }
            }
        });
        overviewPanelActionsResources.setDragEnabled(true);
        overviewPanelActionsResources.setEditable(true);
        /*overviewPanelActionsResources.getModel().addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)e.getTreePath().getLastPathComponent();
                DefaultMutableTreeNode childTreeNode = (DefaultMutableTreeNode) treeNode.getChildAt(e.getChildIndices()[0]);
                String resourceName = (Resource)childTreeNode.getUserObject();
                resource.setName((String)childTreeNode.getUserObject());
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {

            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {

            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {

            }
        });*/
        //overviewPanelActionsResources.setTransferHandler(new TransferHandler());

        /*setAsDropTarget(overviewPanelActionsResources, (component, language) -> {
            Resource resource = new Resource() {
                String name = "Resource" + resources.size();
                Object content = language.newContent();

                @Override
                public Language getLanguage() {
                    return language;
                }

                public String getName() {
                    return name;
                }

                private Language getAsLanguage() {
                    return (Language) getLanguage().evaluate(this.getContent());
                }

                @Override
                public void attachTo(Object content, JPanel panel) {
                    getAsLanguage().attachTo(content, panel);
                }

                @Override
                public void dettachFrom(Object content, JPanel panel) {
                    getAsLanguage().dettachFrom(content, panel);
                }

                @Override
                public Object newContent() {
                    return getAsLanguage().newContent();
                }

                @Override
                public Object evaluate(Object content) {
                    return getAsLanguage().evaluate(content);
                }

                @Override
                public void setName(String name) {
                    this.name = name;
                }

                @Override
                public Object getContent() {
                    return content;
                }

                @Override
                public void setContent(Object content) {
                    this.content = content;
                }

                @Override
                public String toString() {
                    return getName();
                }
            };
            resources.addElement(resource);
            //setResource(editorPanel, languages, resource);
        });*/

        JSplitPane overviewPanelSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, overviewPanelActionsLanguages, overviewPanelActionsResources);
        overviewPanelSplitPane.setResizeWeight(0.25);
        overviewPanel.add(overviewPanelSplitPane, BorderLayout.CENTER);

        JSplitPane contentPaneSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, overviewPanelActionsResources, editorPanel);
        contentPaneSplit.setResizeWeight(0.15);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.add(contentPaneSplit, BorderLayout.CENTER);

        frame.setContentPane(contentPane);

        frame.setSize(1024, 768);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextPane evaluationPanel = new JTextPane();
        contentPane.add(evaluationPanel, BorderLayout.SOUTH);

        JToolBar toolBar = new JToolBar();

        toolBar.add(new AbstractAction("Evaluate") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (overviewPanelActionsResources.getSelectionCount() == 1) {
                    DefaultMutableTreeNode resourceNode = (DefaultMutableTreeNode)overviewPanelActionsResources.getSelectionPath().getLastPathComponent();
                    Resource resource = (Resource)resourceNode.getUserObject();
                    Language language = resource.getLanguage();
                    Object obj = language.evaluate(resource.getContent());
                    evaluationPanel.setText(obj.toString());
                }
            }
        });
        toolBar.add(new AbstractAction("New") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (overviewPanelActionsResources.getSelectionCount() == 1) {
                    DefaultMutableTreeNode parentResourceNode = (DefaultMutableTreeNode)overviewPanelActionsResources.getSelectionPath().getLastPathComponent();

                    newResource(overviewPanelActionsResources, resourceStore, parentResourceNode);
                }
            }
        });
        contentPane.add(toolBar, BorderLayout.NORTH);

        createExample(overviewPanelActionsResources, resourceStore);

        frame.setVisible(true);
    }

    private static <T extends JComponent> void setAsDropTarget(T component, BiConsumer<T, Language> languageHandler) throws TooManyListenersException {
        component.setTransferHandler(new TransferHandler() {
            @Override
            public boolean importData(JComponent comp, Transferable t) {
                return true;
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    Language language = (Language)support.getTransferable().getTransferData(support.getDataFlavors()[0]);
                    languageHandler.accept(component, language);

                } catch (UnsupportedFlavorException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return true;
            }

            @Override
            public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
                return true;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return true;
            }
        });
        component.getDropTarget().addDropTargetListener(new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(dtde.getDropAction());
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {

            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {

            }

            @Override
            public void dragExit(DropTargetEvent dte) {

            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                dtde.dropComplete(true);
            }
        });
    }

    private static void setResource(JPanel editorPanel, ComboBoxModel<Language> languages, Resource resource) {
        editorPanel.removeAll();

        if(resource != null) {
            resource.attachTo(editorPanel);
        }

        editorPanel.revalidate();
        editorPanel.repaint();
    }
}
