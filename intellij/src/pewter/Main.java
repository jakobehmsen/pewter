package pewter;

import jdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.activation.DataHandler;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
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
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.TooManyListenersException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class Main {
    private interface Language {
        void attachTo(Object content, JPanel panel);
        void dettachFrom(Object content, JPanel panel);
        Object newContent();
        Object evaluate(Object content);
    }

    private interface Resource extends Language {
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

    private static abstract class AbstractResource implements Resource {
        private String name;
        private Object content;

        @Override
        public String getName() {
            return name;
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
        public void attachTo(Object content, JPanel panel) {
            JEditorPane textPane = new JEditorPane();
            textPane.setDocument((Document) content);
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
            String sourceCode = null;
            try {
                sourceCode = ((Document)content).getText(0, ((Document)content).getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }

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

    private static void createExample(JTree overviewPanelActionsResources) {
        DefaultMutableTreeNode examplePattern = newResource(overviewPanelActionsResources, (DefaultMutableTreeNode) overviewPanelActionsResources.getModel().getRoot());
        try {
            String text = new String(java.nio.file.Files.readAllBytes(Paths.get("src/parsers/Example")));
            ((Document)((Resource)examplePattern.getUserObject()).getContent()).insertString(0, text, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        DefaultMutableTreeNode examplePatternExample = newResource(overviewPanelActionsResources, examplePattern);
        try {
            String text = "HI THERE";
            ((Document)((Resource)examplePatternExample.getUserObject()).getContent()).insertString(0, text, null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private static DefaultMutableTreeNode newResource(JTree overviewPanelActionsResources, DefaultMutableTreeNode parent) {
        DefaultMutableTreeNode parentResourceNode = (DefaultMutableTreeNode)parent;//(DefaultMutableTreeNode)overviewPanelActionsResources.getSelectionPath().getLastPathComponent();

        DefaultMutableTreeNode resourceNode = new DefaultMutableTreeNode();
        Resource resource = new AbstractResource() {
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

    public static void main(String[] args) throws Exception {
        ScriptEngineManager engineManager = new ScriptEngineManager();

        JFrame frame = new JFrame();

        DefaultListModel<Resource> resources = new DefaultListModel<>();

        Resource nashhornResource = new AbstractResource() {
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
                    String sourceCode = ((Document)content).getText(0, ((Document)content).getLength());
                    engine.eval(sourceCode);

                    return ((Invocable)engine).invokeFunction("main");
                } catch (ScriptException e) {
                    e.printStackTrace();
                } catch (BadLocationException e) {
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

        setAsDropTarget(editorPanel, (component, language) -> {
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
        });

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
        JTree overviewPanelActionsResources = new JTree(new DefaultMutableTreeNode(nashhornResource));
        overviewPanelActionsResources.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                if (overviewPanelActionsResources.getSelectionCount() == 1) {
                    DefaultMutableTreeNode resourceNode = (DefaultMutableTreeNode)overviewPanelActionsResources.getSelectionPath().getLastPathComponent();
                    Resource resource = (Resource)resourceNode.getUserObject();
                    setResource(editorPanel, languages, resource);
                } else {
                    setResource(editorPanel, languages, null);
                }
            }
        });

        setAsDropTarget(overviewPanelActionsResources, (component, language) -> {
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
            /*if (overviewPanelActionsResources.editCellAt(resources.size() - 1, 0)) {
                overviewPanelActionsResources.getEditorComponent().requestFocusInWindow();
                ((JTextComponent) overviewPanelActionsResources.getEditorComponent()).selectAll();
                overviewPanelActionsResources.setRowSelectionInterval(resources.size() - 1, resources.size() - 1);
            }*/
            //setResource(editorPanel, languages, resource);
        });

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

                    newResource(overviewPanelActionsResources, parentResourceNode);
                }
            }
        });
        contentPane.add(toolBar, BorderLayout.NORTH);

        createExample(overviewPanelActionsResources);

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
