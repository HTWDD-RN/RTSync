package de.dmos.rtsync.swingui;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.ScrollPane;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.dmos.rtsync.client.ClientSubscriptionContainer;
import de.dmos.rtsync.customotter.AbstractCustomSharedObject;
import de.dmos.rtsync.customotter.SharedObjectContainer;
import de.dmos.rtsync.listeners.SelfSubscriptionSupplier;
import de.dmos.rtsync.swingui.RTModelActionPanel.RTModelActionListener;
import de.dmos.rtsync.swingui.RTModelTreeView.RTModelTreeViewSelectionListener;

/**
 * A panel which displays a project's RTProjectData. A {@link ClientSubscriptionContainer} is required and can be given
 * in the constructor of via {@link #setSubscriptionContainer(ClientSubscriptionContainer)}. This contains a
 * {@link RTModelTreeView} and a {@link RTModelActionPanel} and handles event communication between both.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class RTProjectViewPanel extends JPanel implements RTModelTreeViewSelectionListener, RTModelActionListener
{
  private static final long						serialVersionUID = 1L;

  private final RTModelTreeView					_treeView;
  private final JPanel							_mainPanel;
  private final RTModelActionPanel				_actionPanel;
  private final CardLayout						_mainCardLayout;

  public RTProjectViewPanel(SelfSubscriptionSupplier selfSubscriptionSupplier)
  {
	this(selfSubscriptionSupplier, null);
  }

  public RTProjectViewPanel(SelfSubscriptionSupplier selfSubscriptionSupplier, ClientSubscriptionContainer subscriptionContainer) {
	super();
	_mainCardLayout = new CardLayout();
	setLayout(_mainCardLayout);

	_treeView = new RTModelTreeView(selfSubscriptionSupplier, null);
	_actionPanel = new RTModelActionPanel();
	ScrollPane scrollPane = new ScrollPane();
	scrollPane.add(_treeView);
	scrollPane.setMinimumSize(new Dimension(100, _actionPanel.getMinimumSize().height));

	_mainPanel = new JPanel();
	_mainPanel.setLayout(new BoxLayout(_mainPanel, BoxLayout.X_AXIS));
	_mainPanel.add(scrollPane);
	_mainPanel.add(_actionPanel);

	add(new JLabel("no model to display"), "0");
	add(_mainPanel, "1");

	_treeView.addWeakRTModelTreeViewSelectionListener(this);
	_actionPanel.addAsWeakModelActionListener(this);
	setSubscriptionContainer(subscriptionContainer);
  }

  public void setSubscriptionContainer(ClientSubscriptionContainer subscriptionContainer)
  {
	_treeView.setSubscriptionSupplier(subscriptionContainer);
	_actionPanel.setSubscriptionContainer(subscriptionContainer);

	boolean hasModel = subscriptionContainer != null && subscriptionContainer.getModel() != null;
	_mainCardLayout.show(this, hasModel ? "1" : "0");
  }

  @Override
  public void objectSelected(Object selectedObject, SharedObjectContainer<?> container)
  {
	SwingUtilities.invokeLater(() -> _actionPanel.setActionObjects(selectedObject, container));
  }

  @Override
  public void objectCreated(AbstractCustomSharedObject<?> newObject, SharedObjectContainer<?> container)
  {
	_treeView.expand(container);
	if ( newObject instanceof SharedObjectContainer<?> newContainer )
	{
	  // The RTModelTreeView creates the new node on the EDT. The node must exist in order to be expanded.
	  SwingUtilities.invokeLater(() -> _treeView.expand(newContainer));
	}
  }

  @Override
  public void objectRemoved(Object removeObject, SharedObjectContainer<?> container)
  {
	// Nothing to do because the RTModelTreeView updates itself correctly. We only want to react to created objects.
  }
}
