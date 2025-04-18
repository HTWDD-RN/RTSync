package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.util.Tuple;

import de.dmos.rtsync.client.ClientSubscriptionContainer;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.customotter.CustomSharedList;
import de.dmos.rtsync.customotter.CustomSharedMap;
import de.dmos.rtsync.customotter.SharedObjectContainer;
import de.dmos.rtsync.listeners.SelfSubscriptionSupplier;
import de.dmos.rtsync.swingui.RTSharedObjectContainerAdapter.SharedObjectContainerUpdater;
import de.dmos.rtsync.util.SharedObjectHelper;
import de.dmos.rtsync.util.WeakLinkedList;

public class RTModelTreeView extends JTree implements SharedObjectContainerUpdater
{
  /**
   * docme: serialVersionUID
   */
  private static final long											  serialVersionUID = -8944286236777415307L;

  private static final Logger										  LOG			   =
	  LoggerFactory.getLogger(RTModelTreeView.class);

  private final WeakLinkedList<RTModelTreeViewSelectionListener>	  _selectionListeners;
  private final RTSharedObjectContainerAdapter						  _containerAdapter;
  /**
   * Stores the container's nodes together with their expansion state. The later is kept here because
   * 'getExpandedDescendants(node)' doesn't always consider a node's expanded parent as expanded.
   */
  private final Map<SharedObjectContainer<?>, DefaultMutableTreeNode> _containerNodes  = new HashMap<>();

  private TreeSelectionListener										  _jTreeSelectionListener;
  private Tuple<SharedObjectContainer<?>, Object>					  _currentSelection;
  private DefaultTreeModel											  _defaultTreeModel;
  private CustomModel												  _customModel;

  public RTModelTreeView(SelfSubscriptionSupplier selfSubscriptionSupplier)
  {
	this(selfSubscriptionSupplier, null);
  }

  public RTModelTreeView(
	SelfSubscriptionSupplier selfSubscriptionSupplier,
	ClientSubscriptionContainer subscriptionContainer)
  {
	super();
	setRootVisible(false);
	setShowsRootHandles(true);
	selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
	_selectionListeners = new WeakLinkedList<>();
	setCellRenderer(new RTModelTreeRenderer());
	_customModel = subscriptionContainer != null ? subscriptionContainer.getModel() : null;
	_containerAdapter = new RTSharedObjectContainerAdapter(this, selfSubscriptionSupplier, subscriptionContainer);
	createTree();
  }

  public void setSubscriptionSupplier(ClientSubscriptionContainer subscriptionContainer)
  {
	_customModel = subscriptionContainer != null ? subscriptionContainer.getModel() : null;
	_containerAdapter.setSubscriptionSupplier(subscriptionContainer);
	createTree();
  }

  public void setExpandedState(SharedObjectContainer<?> container, boolean state)
  {
	DefaultMutableTreeNode node = _containerNodes.get(container);
	if ( node == null )
	{
	  return;
	}
	TreePath treePath = new TreePath(node.getPath());
	if ( state )
	{
	  expandPath(treePath);
	}
	else
	{
	  collapsePath(treePath);
	}
  }

  public void expand(SharedObjectContainer<?> container)
  {
	setExpandedState(container, true);
  }

  public void collapse(SharedObjectContainer<?> container)
  {
	setExpandedState(container, false);
  }

  public void addWeakRTModelTreeViewSelectionListener(RTModelTreeViewSelectionListener listener)
  {
	_selectionListeners.addAsWeakReference(listener);
	if ( _jTreeSelectionListener == null )
	{
	  _jTreeSelectionListener = createJTreeSelectionListener();
	  addTreeSelectionListener(_jTreeSelectionListener);
	}
  }

  public void removeWeakRTModelTreeViewSelectionListener(RTModelTreeViewSelectionListener listener)
  {
	_selectionListeners.removeReferencedObject(listener);
  }

  private void setDefaultModel(DefaultTreeModel defaultModel)
  {
	_defaultTreeModel = defaultModel;
	setModel(defaultModel);
  }

  private void createTree()
  {
	_containerNodes.clear();
	if ( _customModel != null )
	{
	  DefaultMutableTreeNode root =
		  new DefaultMutableTreeNode(new NodeTextWithColor("root", _customModel.getRoot(), null));
	  addChildrenToTreeNode(root, _customModel.getRoot());
	  setDefaultModel(new DefaultTreeModel(root, true));
	  expandPath(new TreePath(root.getPath()));
	}
	else
	{
	  setDefaultModel(new DefaultTreeModel(null));
	}
  }

  private void addChildrenToTreeNode(DefaultMutableTreeNode parent, SharedObjectContainer<?> container)
  {
	_containerNodes.put(container, parent);
	List<Insertion> insertions = _containerAdapter.getInsertions(container);

	if ( container instanceof CustomSharedList<?> sharedList )
	{
	  _containerAdapter.listenToChanges(sharedList, false);
	  int i = -1;
	  for ( Object element : sharedList.toList() )
	  {
		i++;
		addChildToTreeNode(parent, String.valueOf(i), i, element, insertions);
	  }
	}
	else if ( container instanceof CustomSharedMap customMap )
	{
	  SortedMap<String, Object> map = customMap.toMap();
	  _containerAdapter.listenToChanges(customMap, false);
	  int i = -1;
	  for ( Entry<String, Object> entry : map.sequencedEntrySet() )
	  {
		i++;
		addChildToTreeNode(parent, entry.getKey(), i, entry.getValue(), insertions);
	  }
	}
  }

  private void addChildToTreeNode(
	MutableTreeNode parent,
	String text,
	int index,
	Object element,
	List<Insertion> insertions)
  {
	Color color = getColorForIndex(index, insertions);
	DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(
	  new NodeTextWithColor(text, element, color),
	  element instanceof SharedObjectContainer);
	parent.insert(newNode, index);
	if ( element instanceof SharedObjectContainer<?> container )
	{
	  addChildrenToTreeNode(newNode, container);
	}
  }

  public Color getColorForIndex(int index, List<Insertion> insertions)
  {
	return insertions == null
		? null
			: insertions
			.stream()
			.filter(ins -> ins.contains(index))
			.map(Selection::getColor)
			.filter(c -> c != null)
			.findAny()
			.orElse(null);
  }

  private void updateTreeSelection(TreePath treePath, boolean pathMustMatch)
  {
	Tuple<SharedObjectContainer<?>, Object> newSelection;
	if ( _customModel == null )
	{
	  newSelection = new Tuple<>(null, null);
	}
	else
	{
	  newSelection = treePath == null ? null : getSelectionForPath(treePath);
	  if ( hasNoObjectSelected(newSelection) )
	  {
		if ( pathMustMatch )
		{
		  LOG.warn("The selected object at {} wasn't found.", treePath);
		}
		newSelection = selectRootPath();
	  }
	}

	if ( selectionEquals(newSelection, _currentSelection) )
	{
	  return;
	}

	_currentSelection = newSelection;
	_selectionListeners
	.toStrongStream()
	.forEach(l -> l.objectSelected(_currentSelection._2(), _currentSelection._1()));
  }

  private boolean hasNoObjectSelected(Tuple<SharedObjectContainer<?>, Object> newSelection)
  {
	return newSelection == null || newSelection._2() == null;
  }

  private Tuple<SharedObjectContainer<?>, Object> selectRootPath()
  {
	setSelectionPath(new TreePath("root"));
	return new Tuple<>(null, _customModel.getRoot());
  }

  private boolean selectionEquals(
	Tuple<SharedObjectContainer<?>, Object> selection1,
	Tuple<SharedObjectContainer<?>, Object> selection2)
  {
	if ( selection1 == null || selection2 == null )
	{
	  return selection1 == null && selection2 == null;
	}
	return selection1._1() == selection2._1() && selection1._2() == selection2._2();
  }

  private Tuple<SharedObjectContainer<?>, Object> getSelectionForPath(TreePath treePath)
  {
	Object[] path = treePath.getPath();
	if ( path.length == 0 )
	{
	  return new Tuple<>(null, null);
	}
	if ( path.length == 1 )
	{
	  return new Tuple<>(null, _customModel.getRoot());
	}

	String[] parentPath = new String[path.length - 2];
	for ( int i = 0; i < path.length - 2; i++ )
	{
	  parentPath[i] = path[i + 1].toString();
	}
	Object parentObject = SharedObjectHelper.getObjectAtPath(_customModel, parentPath);
	if ( !(parentObject instanceof SharedObjectContainer<?> container) )
	{
	  return null;
	}

	Object selectedObject = null;
	try
	{
	  selectedObject = container.getObjectAtPathPart(path[path.length - 1].toString());
	}
	catch (IndexOutOfBoundsException | IllegalArgumentException ex)
	{
	  // Handled by the caller if needed. A null return value is preferred.
	}
	return new Tuple<>(container, selectedObject);
  }

  public static interface RTModelTreeViewSelectionListener
  {
	/**
	 * Called when the selection of the tree changed.
	 */
	void objectSelected(Object selectedObject, SharedObjectContainer<?> container);
  }

  private static record NodeTextWithColor(String text, Object object, Color color)
  {
	@Override
	public final String toString()
	{
	  return text;
	}

	public NodeTextWithColor settingColor(Color newColor)
	{
	  return new NodeTextWithColor(text, object, newColor);
	}
  }

  private static class RTModelTreeRenderer extends DefaultTreeCellRenderer
  {
	/**
	 * docme: serialVersionUID
	 */
	private static final long	serialVersionUID = 1L;

	private static final String	LIST_ICON_PATH	 = "icons/listIcon.png";
	private static final Icon	LIST_ICON		 = new ImageIcon(ClassLoader.getSystemResource(LIST_ICON_PATH));

	@Override
	public Component getTreeCellRendererComponent(
	  JTree tree,
	  Object value,
	  boolean sel,
	  boolean expanded,
	  boolean leaf,
	  int row,
	  boolean hasFocus)
	{
	  Component superComponent =
		  super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
	  if ( !(superComponent instanceof JLabel label
		  && value instanceof DefaultMutableTreeNode node
		  && node.getUserObject() instanceof NodeTextWithColor twc) )
	  {
		return superComponent;
	  }

	  Icon icon;
	  if ( twc.object() instanceof SharedObjectContainer )
	  {
		if ( twc.object() instanceof CustomSharedList<?> )
		{
		  icon = LIST_ICON;
		}
		else
		{
		  icon = expanded ? getOpenIcon() : getClosedIcon();
		}
	  }
	  else
	  {
		icon = leafIcon;
	  }
	  label.setIcon(icon);
	  label.setForeground(twc.color());

	  return superComponent;
	}
  }

  /**
   * Returns a {@link Selection} which stands for the sublist of the currently selected rows which are children of the
   * given container or none if no row matches.
   */
  @Override
  public Selection getSelectionInContainer(SharedObjectContainer<?> container)
  {
	return getSelectionIntervalInNode(_containerNodes.get(container));
  }

  /**
   * Gets the interval of row indices which are descendants of the given node.
   */
  private Selection getSelectionIntervalInNode(DefaultMutableTreeNode node)
  {
	Integer nodeRow = getRowIndexForNode(node);
	if ( nodeRow == null )
	{
	  return null;
	}
	return getSelectionIntervalInNode(node, nodeRow + 1);
  }

  private Selection getSelectionIntervalInNode(DefaultMutableTreeNode node, int firstDescendantRow)
  {
	int descendantCount = countChildrenOfExpandedDescendants(new TreePath(node.getPath()));
	if ( descendantCount == 0 )
	{
	  return null;
	}
	int min = getMinSelectionRow();
	int max = getMaxSelectionRow();
	if ( min < 0 || max < 0 || min >= firstDescendantRow + descendantCount || max < firstDescendantRow )
	{
	  return null;
	}
	int start = Math.max(min - firstDescendantRow, 0);
	int length = Math.min(max - min, descendantCount - 1);
	return new Selection(start, length);
  }

  /**
   * Counts the all currently visible nodes of the one indicated by the given path.
   */
  public int countChildrenOfExpandedDescendants(TreePath treePath)
  {
	int count = 0;
	for ( TreePath tp : enumerationToList(getExpandedDescendants(treePath)) )
	{
	  if ( tp.getLastPathComponent() instanceof TreeNode descendantNode )
	  {
		count += descendantNode.getChildCount();
	  }
	}
	return count;
  }

  public <T> List<T> enumerationToList(Enumeration<T> enumeration)
  {
	List<T> list = new ArrayList<>();
	if ( enumeration == null )
	{
	  return list;
	}
	while (enumeration.hasMoreElements())
	{
	  list.add(enumeration.nextElement());
	}
	return list;
  }

  /**
   * Returns the node for the given container. This may be null.
   */
  public DefaultMutableTreeNode getNode(SharedObjectContainer<?> container)
  {
	return _containerNodes.get(container);
  }

  @Override
  public void updateAllColors()
  {
	if ( !(_defaultTreeModel.getRoot() instanceof DefaultMutableTreeNode node) )
	{
	  return;
	}
	updateChildrensColors(node, null, 0);
	repaint();
  }

  private void updateChildrensColors(DefaultMutableTreeNode node, List<Insertion> insertions, int index)
  {
	if ( !(node.getUserObject() instanceof NodeTextWithColor twc) )
	{
	  return;
	}
	Color color = getColorForIndex(index, insertions);
	node.setUserObject(twc.settingColor(color));
	if ( twc.object instanceof SharedObjectContainer<?> container )
	{
	  List<Insertion> childInsertions = _containerAdapter.getInsertions(container);
	  int childIndex = 0;
	  for ( TreeNode childNode : enumerationToList(node.children()) )
	  {
		if ( childNode instanceof DefaultMutableTreeNode defaultNode )
		{
		  updateChildrensColors(defaultNode, childInsertions, childIndex);
		}
		childIndex++;
	  }
	}
  }

  @Override
  public void updateContainer(
	SharedObjectContainer<?> container,
	Selection selection,
	Set<SharedObjectContainer<?>> removedDescendantContainers)
  {
	SwingUtilities.invokeLater(() -> {
	  LOG.debug("updateContainer({},{},{})", container, selection, removedDescendantContainers);
	  DefaultMutableTreeNode node = _containerNodes.get(container);
	  if ( node == null )
	  {
		return;
	  }

	  TreePath containerPath = new TreePath(node.getPath());
	  List<TreePath> expandedTreePaths = enumerationToList(getExpandedDescendants(containerPath));
	  TreePath[] selectionPaths = getSelectionPaths();

	  node.removeAllChildren();
	  addChildrenToTreeNode(node, container);
	  _defaultTreeModel.nodeStructureChanged(node);

	  expandedTreePaths = findPathsInNewStructure(node, containerPath, expandedTreePaths);
	  expandedTreePaths.forEach(tp -> expandPath(tp));
	  if ( selectionPaths != null && selectionPaths.length > 0 )
	  {
		setSelectionPaths(
		  findPathsInNewStructure(node, containerPath, Arrays.asList(selectionPaths)).toArray(TreePath[]::new));
	  }
	  //	  selectInNode(node, selection);
	});
  }

  private List<TreePath> findPathsInNewStructure(
	DefaultMutableTreeNode parentNode,
	TreePath parentNodePath,
	List<TreePath> oldPaths)
  {
	int pathCount = parentNodePath.getPathCount();
	return oldPaths
		.parallelStream()
		.map(p -> parentNodePath.isDescendant(p) ? findPathInNewStructure(parentNode, pathCount, p) : p)
		.filter(p -> p != null)
		.toList();
  }

  private TreePath findPathInNewStructure(TreeNode node, int containerPathCount, TreePath descendantPath)
  {
	Object[] path = descendantPath.getPath();
	//	TreeNode[] newPath = new TreeNode[path.length];
	//	System.arraycopy(path, 0, newPath, 0, containerPathCount);
	for ( int i = containerPathCount; i < path.length; i++ )
	{
	  path[i] = node = findChildByObject(node, descendantPath.getPathComponent(i));
	  if ( node == null )
	  {
		return null;
	  }
	}
	return new TreePath(path);
  }

  private DefaultMutableTreeNode findChildByObject(TreeNode node, Object oldNode)
  {
	NodeTextWithColor oldTwc = getTextWithColorFromNode(oldNode);
	if ( oldTwc == null )
	{
	  return null;
	}
	Object oldObject = oldTwc.object();

	return (DefaultMutableTreeNode) enumerationToList(node.children())
		.stream()
		.filter(c -> {
		  NodeTextWithColor twc = getTextWithColorFromNode(c);
		  return twc != null && twc.object() == oldObject;
		})
		.findAny()
		.orElse(null);
  }

  private NodeTextWithColor getTextWithColorFromNode(Object node)
  {
	return node instanceof DefaultMutableTreeNode dn && dn.getUserObject() instanceof NodeTextWithColor twc
		? twc
			: null;
  }

  private TreeNode findChildByName(TreeNode node, String oldPathPart)
  {
	return enumerationToList(node.children())
		.stream()
		.filter(c -> oldPathPart.equals(c.toString()))
		.findAny()
		.orElse(null);
  }

  private void selectInNode(DefaultMutableTreeNode node, Selection selection)
  {
	if ( selection == null )
	{
	  return;
	}
	Integer index = getRowIndexForNode(node);
	if ( index == null )
	{
	  return;
	}
	int firstDescendantRow = index + 1;
	selection = selection.move(firstDescendantRow);
	LOG.debug("Selecting rows {} - {}", selection.getStart(), selection.getEnd());
	setSelectionInterval(selection.getStart(), selection.getEnd());
  }

  private Integer getRowIndexForNode(DefaultMutableTreeNode node)
  {
	int[] rows = getSelectionModel().getRowMapper().getRowsForPaths(new TreePath[] {new TreePath(node.getPath())});
	if ( rows == null )
	{
	  return null;
	}
	return rows[0] < 0 ? null : rows[0];
  }

  TreeSelectionListener createJTreeSelectionListener()
  {
	return new TreeSelectionListener() {
	  private boolean _inUpdate = false;

	  @Override
	  public void valueChanged(TreeSelectionEvent e)
	  {
		if ( !_inUpdate )
		{
		  _inUpdate = true;
		  updateTreeSelection(e.getPath(), true);
		  _inUpdate = false;
		}
	  }
	};
  }
}
