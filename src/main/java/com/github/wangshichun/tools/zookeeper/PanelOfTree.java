package com.github.wangshichun.tools.zookeeper;

import org.apache.zookeeper.data.Stat;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;

import static com.github.wangshichun.tools.zookeeper.Main.*;

/**
 * Created by wangshichun on 2016/5/24.
 */
public class PanelOfTree extends JPanel {
    private ZKUtil zkUtil;
    private String path = "/"; // 查看zk的哪个路径的数据
    private DefaultMutableTreeNode currentNode; // 当前选中的节点
    private DefaultMutableTreeNode rootNode; // 根节点
    private java.util.List<String> children = new LinkedList<>(); // 当前路径下的子路径
    private String pathFilter = ""; // 过滤子节点的表达式
    private Runnable setStatForPathRunnable; // 查看按钮（树结构中单击节点）调用
    private Runnable setChildrenForPathRunnable; // 设置子节点的时候调用

    PanelOfTree(ZKUtil zkUtil) {
        this.zkUtil = zkUtil;
        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);

        // 子节点过滤
        final JPanel subHeadPanel = new JPanel();
        this.add(subHeadPanel);
        setConstraints(1, 1, true, 1.0, null, layout, subHeadPanel);
        initSubHeadPanel(subHeadPanel);

        // 树结构
        JTree jTree = new JTree();
        initTree(jTree);
        final JScrollPane scrollPane = new JScrollPane(jTree);
        add(scrollPane);
        setConstraints(1, 1, false, 0.7, null, layout, scrollPane);

        // 信息查看、操作
        final JPanel sidePanel = new JPanel();
        add(sidePanel);
        sidePanel.setBorder(new LineBorder(Color.black, 1, true));
        setConstraints(1, 1, true, 0.3, 1.0, layout, sidePanel);
        initSidePanel(sidePanel);
    }

    private void initTree(final JTree tree) {
        rootNode = new DefaultMutableTreeNode("/");
        currentNode = rootNode;
        final DefaultTreeModel defaultTreeModel = new DefaultTreeModel(rootNode);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setModel(defaultTreeModel);

//        tree.setExpandsSelectedPaths(true);
//        tree.expandPath(new TreePath(rootNode));

        setChildrenForPathRunnable = new Runnable() {
            @Override
            public void run() {
                java.util.List<String> list = new ArrayList<>();
                list.addAll(children);
                for (int i  = currentNode.getChildCount() - 1; i >= 0 ; i--) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) currentNode.getChildAt(i);
                    String name = (String) node.getUserObject();
                    if (list.contains(name)) {
                        list.remove(name); // 已存在的节点不变
                    } else {
                        node.removeFromParent(); // 删除已不存在的节点
                    }
                }

                for (int i = list.size() - 1; i >= 0; i--) { // 新增的节点按顺序插入到相应的位置
                    String name = list.get(i);
                    int pos = 0;
                    for (int k = currentNode.getChildCount() - 1; k >= 0; k--) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) currentNode.getChildAt(k);
                        if (name.compareTo((String) node.getUserObject()) > 0) {
                            pos = k + 1;
                            break;
                        }
                    }
                    currentNode.insert(new DefaultMutableTreeNode(name), pos);
                }

                TreePath treePath = new TreePath(currentNode.getPath());
                if (!tree.isExpanded(treePath))
                    tree.expandPath(treePath);
//                else
//                    tree.collapsePath(treePath);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        tree.updateUI();
                    }
                });
            }
        };

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                TreePath treePath = e.getPath();
                if (null != treePath) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                    if (node != null) {
                        path = joinPath(new TreePath(node.getPath()));
                        System.out.println(path);
                        currentNode = node;
                        setStatForPathRunnable.run();
                    }
                }
            }
        });

        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel,
                                                          boolean expanded,
                                                          boolean leaf, int row,
                                                          boolean hasFocus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                pathFilter = pathFilter.toLowerCase();
                if (pathFilter.length() > 0 && value.toString().toLowerCase().contains(pathFilter))
                    label.setBorder(new LineBorder(Color.ORANGE, 2));
                else
                    label.setBorder(new LineBorder(Color.WHITE, 2));
                return label;
            }
        });
    }

    private void refreshParentNode() {
        String parent = path.substring(0, path.lastIndexOf("/"));
        if (parent.length() > 0 && currentNode.getParent() != null) {
            path = parent;

            currentNode = (DefaultMutableTreeNode) currentNode.getParent();
            refreshNode(currentNode);
        }
    }

    private void refreshNode(DefaultMutableTreeNode node) {
        if (node == null)
            return;
        setStatForPathRunnable.run();
    }

    private String joinPath(TreePath treePath) {
        Object[] paths = treePath.getPath();
        StringBuilder builder = new StringBuilder();
        for (Object p : paths) {
            if ("/".equals(p.toString()))
                continue;
            else
                builder.append("/").append(p);
        }
        if (builder.length() == 0)
            return "/";

        return builder.toString();
    }

    private void initSidePanel(final JPanel panel) {
        GridBagLayout gridBagLayout = new GridBagLayout();
        panel.setLayout(gridBagLayout);
        JLabel label = new JLabel("路径：");
        final JTextField textField = new JTextField();
        textField.setEditable(false);
        panel.add(label);
        panel.add(textField);
        setConstraints(2, 1, true, null, null, gridBagLayout, label);
        setConstraints(2, 1, true, null, null, gridBagLayout, textField);

        final JTextArea textForStat = new JTextArea();
        textForStat.setLineWrap(false);
        textForStat.setColumns(40);
        textForStat.setEditable(false);
        textForStat.setBackground(Color.lightGray);

        JPanel tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(tempPanel);
        tempPanel.add(textForStat);
        setConstraints(2, 1, true, 1.0, null, gridBagLayout, tempPanel);

        label = new JLabel("数据：");
        panel.add(label);
        setConstraints(2, 1, true, null, null, gridBagLayout, label);

        final JTextArea textForData = new JTextArea();
        textForData.setLineWrap(true);
        textForData.setColumns(40);
        textForData.setRows(10);
        JScrollPane jScrollPane = new JScrollPane(textForData);
        panel.add(jScrollPane);
        jScrollPane.setPreferredSize(new Dimension(450, 200));
        jScrollPane.setMaximumSize(new Dimension(450, 200));
        setConstraints(2, 1, true, 0.0, null, gridBagLayout, jScrollPane);

        // 新建按钮
        final JButton createButton = new JButton("新建(不带数据)");
        final JButton createWithDataButton = new JButton("新建(带数据)");
        panel.add(createButton);
        panel.add(createWithDataButton);
        setConstraints(1, 1, false, null, null, gridBagLayout, createButton);
        setConstraints(1, 1, true, null, null, gridBagLayout, createWithDataButton);
        createButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isZKConnected(zkUtil.isAlreadyInit()))
                    return;

                String name = JOptionPane.showInputDialog("请输入节点名称（不是绝对路径，只是不含斜杠/的名称）：");
                if (name == null || name.length() == 0)
                    return;

                name = path + "/" + name;
                if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(null, String.format("确定要创建[%s]节点吗？", name)))
                    return;

                String status = zkUtil.create(name, null);
                if (status.contains("创建成功")) {
                    refreshNode(currentNode);
                }
                JOptionPane.showMessageDialog(null, status);
            }
        });
        createWithDataButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isZKConnected(zkUtil.isAlreadyInit()))
                    return;

                String name = JOptionPane.showInputDialog("请输入节点名称（不是绝对路径，只是不含斜杠/的名称）：");
                if (name == null || name.length() == 0)
                    return;

                name = path + "/" + name;
                String data = textForData.getText();
                if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(null, String.format("确定要创建[%s]节点，\n且将数据设置为【%s】(UTF-8)吗？", name, (data.length() > 20 ? data.substring(0, 20) + "...." : data))))
                    return;

                String status = zkUtil.create(name, data.getBytes(StandardCharsets.UTF_8));
                if (status.contains("创建成功")) {
                    refreshNode(currentNode);
                }
                JOptionPane.showMessageDialog(null, status);
            }
        });


        // 修改、删除按钮
        final JButton deleteButton = new JButton("删除此节点");
        final JButton updateButton = new JButton("修改数据为如上文本框中的数据");
        panel.add(deleteButton);
        panel.add(updateButton);
        setConstraints(1, 1, false, null, null, gridBagLayout, deleteButton);
        setConstraints(1, 1, true, null, null, gridBagLayout, updateButton);
        deleteButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isZKConnected(zkUtil.isAlreadyInit()))
                    return;
                if (!checkRootPath(path))
                    return;
                if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(null, String.format("确定要删除节点[%s]吗？", path)))
                    return;
                String msg = zkUtil.delete(path);
                if ("删除成功".equals(msg)) {
                    refreshParentNode();
                }
                JOptionPane.showMessageDialog(null, msg);
            }
        });
        updateButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isZKConnected(zkUtil.isAlreadyInit()))
                    return;
                if (!checkRootPath(path))
                    return;
                String data = textForData.getText();
                if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(null, String.format("确定要修改节点[%s]\n的数据为\n【%s】(UTF-8)吗？", path, (data.length() > 20 ? data.substring(0, 20) + "...." : data))))
                    return;
                String msg = zkUtil.update(path, data.getBytes(StandardCharsets.UTF_8));
                if ("数据设置成功".equals(msg)) {
                    refreshNode(currentNode);
                }
                JOptionPane.showMessageDialog(null, msg);
            }
        });


        setStatForPathRunnable = new Runnable() {
            @Override
            public void run() {
                if (path.length() > 1 && path.endsWith("/"))
                    path = path.substring(0, path.length() - 1);

                textField.setText(path);
                textForStat.setText("");
                textForData.setText("");
                children.clear();
//                textField.setColumns(path.length() + 3);

                Stat stat = zkUtil.exists(path);
                if (null == stat) {
                    textForStat.setText("不存在");
                    setChildrenForPathRunnable.run();
                    return;
                }
                StringBuilder builder = new StringBuilder();
                builder.append("Czxid: ").append(stat.getCzxid()).append("\n");
                builder.append("Mzxid: ").append(stat.getMzxid()).append("\n");
                builder.append("Ctime: ").append(stat.getCtime()).append("\n");
                builder.append("Mtime: ").append(stat.getMtime()).append("\n");
                builder.append("Version: ").append(stat.getVersion()).append("\n");
                builder.append("Cversion: ").append(stat.getCversion()).append("\n");
                builder.append("Aversion: ").append(stat.getAversion()).append("\n");
                builder.append("EphemeralOwner: ").append(stat.getEphemeralOwner()).append("\n");
                builder.append("DataLength: ").append(stat.getDataLength()).append("\n");
                builder.append("NumChildren: ").append(stat.getNumChildren()).append("\n");
                builder.append("Pzxid: ").append(stat.getPzxid()).append("\n");
                textForStat.setText(builder.toString());

                String data = zkUtil.getData(path);
                if (data != null)
                    textForData.setText(data);

                if (stat.getNumChildren() > 0) {
                    children = zkUtil.children(path);
                }
                setChildrenForPathRunnable.run();
            }
        };
    }

    private void initSubHeadPanel(JPanel subHeadPanel) {
        subHeadPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("过滤子节点列表：");
        subHeadPanel.add(label);
        final JTextField filterTextField = new JTextField();
        filterTextField.setColumns(20);
        subHeadPanel.add(filterTextField);
        filterTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                pathFilter = filterTextField.getText().trim();
                setChildrenForPathRunnable.run();
            }
        });
        filterTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                pathFilter = filterTextField.getText().trim();
                setChildrenForPathRunnable.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                pathFilter = filterTextField.getText().trim();
                setChildrenForPathRunnable.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                pathFilter = filterTextField.getText().trim();
                setChildrenForPathRunnable.run();
            }
        });
    }

    // 连接上zookeeper的时候调用
    void whenOpenZookeeper() {
        if (!zkUtil.isAlreadyInit())
            return;
        children = zkUtil.children((String) currentNode.getUserObject());
        if (setChildrenForPathRunnable != null) {
            setChildrenForPathRunnable.run();
        }
    }

    // 断开zookeeper连接的时候调用
    void whenCloseZookeeper() {
        if (zkUtil.isAlreadyInit())
            return;
        children.clear();
        currentNode = rootNode;
        if (setChildrenForPathRunnable != null)
            setChildrenForPathRunnable.run();
    }
}
