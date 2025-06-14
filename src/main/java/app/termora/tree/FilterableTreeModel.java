package app.termora.tree;

import app.termora.Disposable;
import app.termora.DocumentAdaptor;
import com.formdev.flatlaf.extras.components.FlatTextField;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FilterableTreeModel implements TreeModel, Disposable {
    private final TreeModel originalModel;
    private final EventListenerList eventListener = new EventListenerList();
    private Filter[] filters = new Filter[0];
    private final TreeModelListener originalModelListener = new TreeModelListener() {
        @Override
        public void treeNodesChanged(TreeModelEvent e) {
            rebuildAndNotify(e);
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
            rebuildAndNotify(e);
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
            rebuildAndNotify(e);
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
            rebuildAndNotify(e);
        }
    };
    private final Map<Object, FilterNode> filteredTree = new LinkedHashMap<>();
    private final JTree tree;

    public final FlatTextField filterableTextField = new FlatTextField();
    public boolean expand = false;

    public FilterableTreeModel(JTree tree) {
        this.tree = tree;
        this.originalModel = tree.getModel();
        this.originalModel.addTreeModelListener(originalModelListener);

        filterableTextField.getDocument().addDocumentListener(new DocumentAdaptor() {
            @Override
            public void changedUpdate(@NotNull DocumentEvent e) {
                rebuildAndNotify(null);
            }
        });

        // 初始化构建过滤树
        rebuildFilteredTree();
    }

    @Override
    public Object getRoot() {
        return originalModel.getRoot();
    }

    @Override
    public Object getChild(Object parent, int index) {
        return getFilteredChildren(parent)[index];
    }

    @Override
    public int getChildCount(Object parent) {
        return getFilteredChildren(parent).length;
    }

    @Override
    public boolean isLeaf(Object node) {
        // 如果原模型中是叶子节点，直接返回
        if (originalModel.isLeaf(node)) {
            return true;
        }
        // 如果不是叶子节点，但过滤后没有子节点，也算是叶子节点
        return getFilteredChildren(node).length == 0;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // originalModel.valueForPathChanged(path, newValue);
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        return ArrayUtils.indexOf(getFilteredChildren(parent), child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        eventListener.add(TreeModelListener.class, l);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        eventListener.remove(TreeModelListener.class, l);
    }

    /**
     * 重建过滤树并通知监听器
     */
    private void rebuildAndNotify(TreeModelEvent event) {
        rebuildFilteredTree();
        notifyTreeStructureChanged(event);
        if (expand) {
            expandAllNodes(tree, getRoot(), new TreePath(getRoot()));
        }
    }

    /**
     * 递归展开所有有子节点的路径
     */
    private void expandAllNodes(JTree tree, Object node, TreePath path) {
        // 展开当前路径
        tree.expandPath(path);

        // 递归展开所有子节点
        Object[] children = getFilteredChildren(node);
        for (Object child : children) {
            if (!isLeaf(child)) {
                TreePath childPath = path.pathByAddingChild(child);
                expandAllNodes(tree, child, childPath);
            }
        }
    }

    /**
     * 重建过滤后的树结构
     */
    private void rebuildFilteredTree() {
        filteredTree.clear();
        buildFilteredTree(getRoot(), null);
    }

    /**
     * 递归构建过滤后的树结构
     *
     * @param node   当前节点
     * @param parent 父节点
     */
    private void buildFilteredTree(Object node, Object parent) {
        List<Object> filteredChildren = new ArrayList<>();

        // 获取原始子节点
        int originalChildCount = originalModel.getChildCount(node);
        for (int i = 0; i < originalChildCount; i++) {
            Object child = originalModel.getChild(node, i);

            if (originalModel.isLeaf(child)) {
                // 叶子节点：检查是否通过过滤器
                if (passesFilter(child)) {
                    filteredChildren.add(child);
                }
            } else {
                // 非叶子节点：递归处理子节点
                buildFilteredTree(child, node);

                // 如果子节点有通过过滤的内容，或者节点本身通过过滤，则包含该节点
                FilterNode childFilterNode = filteredTree.get(child);
                if ((childFilterNode != null && childFilterNode.children().length > 0) || passesFilter(child)) {
                    filteredChildren.add(child);
                }
            }
        }

        // 将当前节点的过滤结果保存
        filteredTree.put(node, new FilterNode(
                node,
                parent,
                filteredChildren.toArray(),
                passesFilter(node)
        ));
    }

    /**
     * 检查节点是否通过所有过滤器
     *
     * @param node 要检查的节点
     * @return true如果通过所有过滤器
     */
    private boolean passesFilter(Object node) {
        return Arrays.stream(filters).allMatch(filter -> filter.filter(node));
    }

    /**
     * 获取节点的过滤后子节点
     *
     * @param parent 父节点
     * @return 过滤后的子节点数组
     */
    private Object[] getFilteredChildren(Object parent) {
        FilterNode filterNode = filteredTree.get(parent);
        if (filterNode == null) {
            return new Object[0];
        }
        return filterNode.children();
    }

    /**
     * 通知所有监听器树结构已改变
     */
    private void notifyTreeStructureChanged(TreeModelEvent event) {
        TreeModelListener[] listeners = eventListener.getListeners(TreeModelListener.class);
        if (listeners.length > 0) {
            TreeModelEvent evt = new TreeModelEvent(this, event == null ? new Object[]{getRoot()} : event.getPath());
            for (TreeModelListener listener : listeners) {
                listener.treeStructureChanged(evt);
            }
        }
    }

    /**
     * 添加过滤器
     *
     * @param filter 要添加的过滤器
     */
    public void addFilter(Filter filter) {
        filters = ArrayUtils.add(filters, filter);
        rebuildAndNotify(null);
    }

    /**
     * 移除过滤器
     *
     * @param filter 要移除的过滤器
     */
    public void removeFilter(Filter filter) {
        filters = ArrayUtils.removeElement(filters, filter);
        rebuildAndNotify(null);
    }

    public void filter() {
        rebuildAndNotify(null);
    }

    /**
     * 清除所有过滤器
     */
    public void clearFilters() {
        filters = new Filter[0];
        rebuildAndNotify(null);
    }

    @Override
    public void dispose() {
        filters = new Filter[0];
        filteredTree.clear();
        originalModel.removeTreeModelListener(originalModelListener);
    }

    /**
     * 过滤节点的记录类
     *
     * @param node     节点对象
     * @param parent   父节点
     * @param children 过滤后的子节点数组
     * @param matched  节点本身是否匹配过滤条件
     */
    private record FilterNode(Object node, Object parent, Object[] children, boolean matched) {
    }
}
