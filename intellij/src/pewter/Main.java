package pewter;

import com.sun.glass.events.KeyEvent;
import jdk.nashorn.api.scripting.NashornScriptEngine;
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
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {

    public static class ResourceView extends JLabel implements ResourceListener {
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
        public Object evaluateForUsage(Object content) {
            return null;
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
        DefaultMutableTreeNode nashornNode = (DefaultMutableTreeNode)((DefaultMutableTreeNode) overviewPanelActionsResources.getModel().getRoot()).getChildAt(0);
        DefaultMutableTreeNode examplePattern = newResource(overviewPanelActionsResources, resourceStore, nashornNode);
        try {
            ((Resource)examplePattern.getUserObject()).setName("Pattern");
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
        String name = "Example" + (parent.getChildCount() + 1);
        return newResource(overviewPanelActionsResources, resourceStore, parent, name);
    }

    public static class TreeNodeResource extends AbstractResource {
        private DefaultMutableTreeNode resourceNode;

        public TreeNodeResource(ResourceStore resourceStore, DefaultMutableTreeNode resourceNode) {
            super(resourceStore);
            this.resourceNode = resourceNode;
        }

        @Override
        public Language getLanguage() {
            return (Resource)((DefaultMutableTreeNode)resourceNode.getParent()).getUserObject();
        }

        private String getPath(DefaultMutableTreeNode node) {
            String name = ((Resource)node.getUserObject()).getName();

            if(node.getParent() != null && ((DefaultMutableTreeNode)node.getParent()).getUserObject() instanceof Resource)
                return getPath((DefaultMutableTreeNode)node.getParent()) + "/" + name;

            return name;
        }

        @Override
        public String getPath() {
            return getPath(resourceNode);
        }
    }

    private static DefaultMutableTreeNode newResource(JTree overviewPanelActionsResources, ResourceStore resourceStore, DefaultMutableTreeNode parent, String name) {
        DefaultMutableTreeNode parentResourceNode = (DefaultMutableTreeNode)parent;//(DefaultMutableTreeNode)overviewPanelActionsResources.getSelectionPath().getLastPathComponent();

        DefaultMutableTreeNode resourceNode = new ResourceNode();
        Resource resource = new TreeNodeResource(resourceStore, resourceNode);
        resource.setContent(new DefaultStyledDocument());
        resource.setName(name);
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
                ResourceView lblComp = (ResourceView) StyleConstants.getComponent(ce.getAttributes());
                textBuilder.append(lblComp.resource.getPath());
                i++;
            } else {
                try {
                    int length =  ce.getEndOffset() - i;

                    // Subtract difference if end offset is out of bounds
                    if(ce.getEndOffset() > document.getLength())
                        length -= ce.getEndOffset() - document.getLength();

                    String textPart = document.getText(i, length);
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
                ).stream()
                    .filter(x -> ((DefaultMutableTreeNode) x).getUserObject() instanceof Resource)
                    .map(x -> (Resource) ((DefaultMutableTreeNode)x).getUserObject()).map(Resource.class::cast).collect(Collectors.toList());
            }

            @Override
            public Resource resolveResource(String path) {
                String[] pathParts = path.split("/");

                DefaultMutableTreeNode node = (DefaultMutableTreeNode)overviewPanelActionsResources.getModel().getRoot();

                for(int i = 0; i < pathParts.length; i++) {
                    String name = pathParts[i];
                    node = (DefaultMutableTreeNode)Collections.list(node.children()).stream()
                        .filter(x -> ((Resource) ((DefaultMutableTreeNode) x).getUserObject()).getName().equals(name)).findFirst().get();
                }

                return (Resource)node.getUserObject();
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

            private NashornScriptEngine evaluateWithEngine(Object content) {
                try {
                    ScriptEngine engine = engineManager.getEngineByName("nashorn");

                    engine.put("useFunction", new Function<String, Object>() {
                        @Override
                        public Object apply(String path) {
                            /*String[] pathParts = path.split("/");

                            DefaultMutableTreeNode node = (DefaultMutableTreeNode)overviewPanelActionsResources.getModel().getRoot();

                            for(int i = 0; i < pathParts.length; i++) {
                                String name = pathParts[i];
                                node = (DefaultMutableTreeNode)Collections.list(node.children()).stream()
                                    .filter(x -> ((Resource) ((DefaultMutableTreeNode) x).getUserObject()).getName().equals(name)).findFirst().get();
                            }*/

                            //Resource resource = resourceStore.getAllResources().stream().filter(x -> x.getName().equals(path)).findFirst().get();
                            Resource resource = resourceStore.resolveResource(path);

                            Object object = resource.evaluateForUsage();

                            return object;
                        }
                    });
                    engine.eval("function use(name) {return useFunction.apply(name);}");

                    String sourceCode = getText((StyledDocument)content);
                    engine.eval(sourceCode);

                    return (NashornScriptEngine)engine;
                } catch (ScriptException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            public Object evaluateForUsage(Object content) {
                try {
                    ScriptEngine engine = evaluateWithEngine(content);

                    return engine.eval("this");
                } catch (ScriptException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            public Object evaluate(Object content) {
                try {
                    ScriptEngine engine = evaluateWithEngine(content);

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
                    public Object evaluateForUsage(Object content) {
                        return content.toString();
                    }

                    @Override
                    public Object evaluate(Object content) {
                        return content.toString();
                    }
                };
            }

            @Override
            public String getPath() {
                return "/";
            }
        };
        nashhornResource.setName("Nashorn");
        nashhornResource.setContent(nashhornResource.getLanguage().newContent());
        DefaultComboBoxModel<Language> languages = new DefaultComboBoxModel<>();

        JTabbedPane editorPanel = new JTabbedPane(/*new BorderLayout()*/);

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
            showResource(editorPanel, languages, resource);
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

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(nashhornResource.getLanguage());
        root.add(new ResourceNode(nashhornResource));
        overviewPanelActionsResources = new JTree(root);
        /*overviewPanelActionsResources.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                if (overviewPanelActionsResources.getSelectionCount() == 1) {
                    DefaultMutableTreeNode resourceNode = (DefaultMutableTreeNode) overviewPanelActionsResources.getSelectionPath().getLastPathComponent();
                    Resource resource = (Resource) resourceNode.getUserObject();
                    showResource(editorPanel, languages, resource);
                } else {
                    showResource(editorPanel, languages, null);
                }
            }
        });*/
        overviewPanelActionsResources.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (overviewPanelActionsResources.getSelectionCount() == 1) {
                        DefaultMutableTreeNode resourceNode = (DefaultMutableTreeNode) overviewPanelActionsResources.getSelectionPath().getLastPathComponent();
                        Resource resource = (Resource) resourceNode.getUserObject();
                        showResource(editorPanel, languages, resource);
                    } else {
                        showResource(editorPanel, languages, null);
                    }
                }
            }
        });
        overviewPanelActionsResources.setDragEnabled(true);
        overviewPanelActionsResources.setEditable(true);
        overviewPanelActionsResources.setToggleClickCount(0);
        overviewPanelActionsResources.setRootVisible(false);
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
            //showResource(editorPanel, languages, resource);
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

        toolBar.add(new AbstractAction("Open") {
            @Override
            public void actionPerformed(ActionEvent e) {
                final JFileChooser fc = new JFileChooser();
                if(fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    String filePath = fc.getSelectedFile().getAbsolutePath();

                    NashornScriptEngine projectEngine = (NashornScriptEngine) engineManager.getEngineByName("Nashorn");

                    try {
                        Reader projectReader = java.nio.file.Files.newBufferedReader(Paths.get(filePath));

                        projectEngine.eval(projectReader);

                        //ScriptObjectMirror projectObject = (ScriptObjectMirror) projectEngine.invokeFunction("main");

                        ScriptObjectMirror projectObject = (ScriptObjectMirror) projectEngine.eval("this");

                        DefaultMutableTreeNode root = (DefaultMutableTreeNode)overviewPanelActionsResources.getModel().getRoot();

                        root.removeAllChildren();
                        root.add(new ResourceNode(nashhornResource));

                        ProjectResource projectResource = new DefaultProjectResource(resourceStore, root);

                        //projectEngine.invokeMethod(projectEngine, "loadProject", projectResource);
                        Object ret = projectObject.callMember("loadProject", projectResource);

                        ((DefaultTreeModel)overviewPanelActionsResources.getModel()).reload();
                        overviewPanelActionsResources.revalidate();
                        overviewPanelActionsResources.repaint();

                        overviewPanelActionsResources.expandPath(new TreePath(((DefaultTreeModel)overviewPanelActionsResources.getModel()).getPathToRoot(root.getChildAt(0))));
                        /*((DefaultTreeModel)overviewPanelActionsResources.getModel()).nodeChanged(root);
                        ((DefaultTreeModel)overviewPanelActionsResources.getModel()).nodeStructureChanged(root);*/

                        /*ScriptObjectMirror resources = (ScriptObjectMirror)projectObject.callMember("getResources");
                        resources.values().stream().forEach(x -> {
                            String path = (String)((ScriptObjectMirror)x).callMember("getPath");
                            String content = (String)((ScriptObjectMirror)x).callMember("content");
                            
                        });*/
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    } catch (ScriptException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        toolBar.add(new AbstractAction("Evaluate") {
            @Override
            public void actionPerformed(ActionEvent e) {
                Resource resource = getActiveResource(editorPanel);

                if (resource != null) {
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

    private static Resource getActiveResource(JTabbedPane editorPanel) {
        if(editorPanel.getSelectedIndex() != -1) {
            return ((ResourceView) ((JPanel) editorPanel.getTabComponentAt(editorPanel.getSelectedIndex())).getComponent(0)).resource;
        }

        return null;
    }

    private static void showResource(JTabbedPane editorPanel, ComboBoxModel<Language> languages, Resource resource) {
        OptionalInt existingIndex = IntStream.range(0, editorPanel.getTabCount()).filter(i -> ((ResourceView) ((JPanel) editorPanel.getTabComponentAt(i)).getComponent(0)).resource == resource).findFirst();
        int indexToSelect;

        if(!existingIndex.isPresent()) {
            JPanel tabContent = new JPanel(new BorderLayout());
            resource.attachTo(tabContent);
            editorPanel.addTab(resource.getName(), tabContent);
            JPanel tabComponent = new JPanel(new BorderLayout());
            ResourceView resourceView = new ResourceView(resource);
            tabComponent.add(resourceView, BorderLayout.CENTER);
            JButton closeButton = new JButton("X");
            closeButton.setOpaque(false);
            closeButton.setContentAreaFilled(false);
            closeButton.setBorderPainted(false);
            closeButton.addActionListener(e -> {
                int index = editorPanel.indexOfTabComponent(tabComponent);
                editorPanel.removeTabAt(index);
                resourceView.unbind();
            });
            tabComponent.add(closeButton, BorderLayout.EAST);
            editorPanel.setTabComponentAt(editorPanel.getTabCount() - 1, tabComponent);
            indexToSelect = editorPanel.getTabCount() - 1;
        } else {
            indexToSelect = existingIndex.getAsInt();
        }

        editorPanel.setSelectedIndex(indexToSelect);

        /*resource.addListener(new ResourceListener() {
            @Override
            public void nameChanged(Resource resource) {
            }
        });

        editorPanel.setModel(new DefaultSingleSelectionModel());

        editorPanel.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {

            }
        });*/

        /*editorPanel.removeAll();

        if(resource != null) {

            resource.attachTo(editorPanel);
        }*/

        editorPanel.revalidate();
        editorPanel.repaint();
    }
}
