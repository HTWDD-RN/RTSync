package de.dmos.rtsync.swingui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.dmos.rtsync.client.RTSyncSimpleClient;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.customotter.CustomSharedList;
import de.dmos.rtsync.customotter.CustomSharedMap;
import de.dmos.rtsync.customotter.UserChangeEvent;
import de.dmos.rtsync.message.Subscriber;
import se.l4.otter.model.SharedString;
import se.l4.otter.model.spi.DataValues;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedDelta;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.list.ListDelta;
import se.l4.otter.operations.list.ListHandler;

/**
 * Tests the {@link RTModelTreeView}. This is done in this project because the {@link RTModelTreeView} needs a
 * constructor objects whose implementations are not available in rtsync-client-core.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
class RTModelTreeViewTest
{
  private static final String LIST	   = "list";
  private static final String MAIN_MAP = "main map";

  private RTSyncSimpleClient  _client;
  private RTModelTreeView _treeView;
  private CustomModel	  _model;
  private TreeCellRenderer	  _renderer;
  private CustomSharedMap		   _mainMap;
  private CustomSharedList<Object> _list;
  private SharedString			   _string;

  @BeforeEach
  void createTreeView() throws InvocationTargetException, InterruptedException
  {
	_client = new RTSyncSimpleClient();
	_model = _client.getModel();

	// Create some model objects
	_mainMap = _model.newMap();
	_model.set(MAIN_MAP, _mainMap);
	_list = _model.newList();
	_mainMap.set(LIST, _list);
	_string = _model.newString();
	_list.add(_string);

	_treeView = new RTModelTreeView(_client.getConnectionHandler(), _client.getSubscriptionContainer());
	_treeView.setRootVisible(false);
	_renderer = _treeView.getCellRenderer();
	_treeView.expand(_list);
	Awaitility.await().atMost(Duration.ofMillis(250)).until(() -> _treeView.getRowCount() == 3);
	waitForGUIUpdate();
  }

  private void assertBasicStructure()
  {
	TreeNode root = (TreeNode) _treeView.getModel().getRoot();
	TreeNode mapNode = root.getChildAt(0);
	assertEquals(MAIN_MAP, mapNode.toString());
	TreeNode listNode = mapNode.getChildAt(0);
	assertEquals(LIST, listNode.toString());
	TreeNode stringNode = listNode.getChildAt(0);
	assertFalse(stringNode.getAllowsChildren());
  }

  @Test
  void basicModelViewTest()
  {
	assertBasicStructure();
  }

  @Test
  void getSelectionInContainerTest()
  {
	_treeView.setSelectionRow(2);
	String[] expectedSelectionPath = new String[] {"root", MAIN_MAP, LIST, "0"};
	assertPathEquals(expectedSelectionPath, _treeView.getSelectionPath());
	assertEquals(new Selection(0, 0), _treeView.getSelectionInContainer(_list));
	assertEquals(new Selection(1, 0), _treeView.getSelectionInContainer(_mainMap));
	assertEquals(null, _treeView.getSelectionInContainer(_model.getRoot()));

	_treeView.setSelectionRow(1);
	expectedSelectionPath = new String[] {"root", MAIN_MAP, LIST};
	assertPathEquals(expectedSelectionPath, _treeView.getSelectionPath());
	assertEquals(null, _treeView.getSelectionInContainer(_list));
	assertEquals(new Selection(0, 0), _treeView.getSelectionInContainer(_mainMap));
	assertEquals(null, _treeView.getSelectionInContainer(_model.getRoot()));
  }

  @Test
  void remoteInsertTest() throws InvocationTargetException, InterruptedException
  {
	_treeView.setSelectionRow(2);
	String[] expectedSelectionPath = new String[] {"root", MAIN_MAP, LIST, "0"};

	// Simulates the insertion done by another user.
	SharedString ss = _model.newString();
	String otherUserName = "user B";

	Operation<ListHandler> listOp = ListDelta.builder().insert(DataValues.toData(ss)).retain(1).done();
	Operation<CombinedHandler> cOp = CombinedDelta.builder().update(_list.getObjectId(), "list", listOp).done();
	UserChangeEvent<Operation<CombinedHandler>> event = new UserChangeEvent<>(cOp, false, otherUserName);
	_client.getModel().editorChanged(event);

	waitForGUIUpdate();

	assertEquals(4, _treeView.getRowCount());
	assertBasicStructure();
	TreePath newTreePath = _treeView.getPathForRow(2);
	assertPathEquals(expectedSelectionPath, newTreePath);
	expectedSelectionPath[3] = "1";
	assertPathEquals(expectedSelectionPath, _treeView.getSelectionPath());
	assertNode(newTreePath, "0", null);

	// Simulates how the other user changes their color.
	Color otherUserColor = Color.MAGENTA;
	List<Subscriber> subscribers = List.of(new Subscriber(1234, otherUserName, otherUserColor));
	_client.getSubscriptionContainer().onSubscribersReceived(subscribers);

	waitForGUIUpdate();

	assertEquals(4, _treeView.getRowCount());
	assertPathEquals(expectedSelectionPath, _treeView.getSelectionPath());
	assertNode(_treeView.getPathForRow(2), "0", otherUserColor);
	assertNode(_treeView.getPathForRow(3), "1", null);
  }

  void assertNode(TreePath treePath, String text, Color color)
  {
	assertNode((TreeNode) treePath.getLastPathComponent(), text, color);
  }

  void assertNode(TreeNode node, String text, Color color)
  {
	Component component = _renderer.getTreeCellRendererComponent(_treeView, node, false, false, true, 0, false);
	JLabel label = (JLabel) component;
	assertEquals(text, label.getText());
	if ( color != null )
	{
	  assertEquals(color, component.getForeground());
	}
  }

  void assertPathEquals(String[] expected, TreePath treePath)
  {
	String[] actual = Arrays.asList(treePath.getPath()).stream().map(Object::toString).toArray(String[]::new);
	assertArrayEquals(expected, actual);
  }

  void waitForGUIUpdate() throws InvocationTargetException, InterruptedException
  {
	SwingUtilities.invokeAndWait(() -> {
	});
  }
}
