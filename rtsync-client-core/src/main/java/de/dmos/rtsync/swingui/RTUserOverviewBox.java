package de.dmos.rtsync.swingui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.dmos.rtsync.client.ClientConnectionHandler;
import de.dmos.rtsync.client.ConnectionState;
import de.dmos.rtsync.client.SubscriptionSupplier;
import de.dmos.rtsync.listeners.ConnectionListener;
import de.dmos.rtsync.listeners.SelfSubscriberListener;
import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.message.Subscriber;

/**
 * A {@link JPanel} which shows an automatically updating list of users. It should get a {@link SubscriptionSupplier}
 * which can either be provided to its constructor or by calling {@link #setSubscriptionSupplier(SubscriptionSupplier)}.
 */
public class RTUserOverviewBox extends Box
implements
SubscriberListener,
SelfSubscriberListener,
ConnectionListener
{
  private static final Dimension DEFAULT_COLOR_VIEW_SIZE = new Dimension(20, 20);

  private Subscriber       _selfSubscriber;
  private List<Subscriber> _subscribers;
  private ConnectionState		 _currentState;
  private transient SubscriptionSupplier _subscriptionSupplier;

  /**
   * docme: serialVersionUID
   */
  private static final long serialVersionUID = -3877875421111346792L;

  public RTUserOverviewBox(ClientConnectionHandler connectionHandler)
  {
	this(connectionHandler, null);
  }

  public RTUserOverviewBox(ClientConnectionHandler connectionHandler, SubscriptionSupplier subscriptionSupplier)
  {
	super(BoxLayout.Y_AXIS);
	if ( connectionHandler != null )
	{
	  _selfSubscriber = connectionHandler.getSelfSubscriber();
	  connectionHandler.addSelfSubscriberListener(this);
	  connectionHandler.addConnectionListener(this);
	}
	setSubscriptionSupplierBase(subscriptionSupplier);
	updateGUI();
  }

  private void setSubscriptionSupplierBase(SubscriptionSupplier subscriptionSupplier)
  {
	if ( subscriptionSupplier != null )
	{
	  _subscriptionSupplier = subscriptionSupplier;
	  _subscribers = subscriptionSupplier.getSubscribers();
	  subscriptionSupplier.addWeakSubscriberListener(this);
	}
	else
	{
	  _subscribers = null;
	}
  }

  public void setSubscriptionSupplier(SubscriptionSupplier subscriptionSupplier)
  {
	if ( _subscriptionSupplier != null )
	{
	  _subscriptionSupplier.removeWeakSubscriberListener(this);
	}
	setSubscriptionSupplierBase(subscriptionSupplier);
	updateGUI();
  }

  @Override
  public void onOwnNameOrColorChanged(Subscriber selfSubscriber)
  {
	_selfSubscriber = selfSubscriber;
	SwingUtilities.invokeLater(this::updateGUI);
  }

  @Override
  public void onSubscribersReceived(List<Subscriber> subscribers)
  {
	_subscribers = subscribers;
	SwingUtilities.invokeLater(this::updateGUI);
  }

  public void updateGUI()
  {
	setEnabled(_currentState == null || _currentState.isConnected());
	if ( _currentState != null && _currentState.isConnected() )
	{
	  List<Component> updatedComponents = getUpdatedComponents();
	  removeAll();
	  updatedComponents.forEach(c -> add(c));
	  add(Box.createVerticalGlue());
	  SwingUtilities.invokeLater(() -> {
		SwingUIHelper.revalidateTopComponent(this);
	  });
	}
  }

  private List<Component> getUpdatedComponents()
  {
	if ( _subscribers == null || _subscribers.isEmpty() )
	{
	  if ( _selfSubscriber == null )
	  {
		return List.of(new JLabel("no users received from server"));
	  }
	  else
	  {
		return List.of(componentForSubscriber(_selfSubscriber));
	  }
	}
	else
	{
	  return _subscribers.stream().map(this::componentForSubscriber).toList();
	}
  }

  protected Component componentForSubscriber(Subscriber subscriber)
  {
	String prefix = _selfSubscriber != null && subscriber.getId() == _selfSubscriber.getId() ? "* " : "  ";
	Box box = Box.createHorizontalBox();
	JLabel colorLabel = new JLabel(" ");
	colorLabel.setMinimumSize(DEFAULT_COLOR_VIEW_SIZE);
	colorLabel.setMaximumSize(DEFAULT_COLOR_VIEW_SIZE);
	colorLabel.setPreferredSize(DEFAULT_COLOR_VIEW_SIZE);
	colorLabel.setBackground(subscriber.getColor());
	colorLabel.setOpaque(true);
	box.add(colorLabel);
	box.add(Box.createHorizontalStrut(4));
	box.add(new JLabel(prefix + subscriber.getName()));
	box.add(Box.createHorizontalGlue());
	return box;
  }

  @Override
  public void onConnectionStateChanged(ConnectionState currentState, Throwable throwable)
  {
	_currentState = currentState;
	SwingUtilities.invokeLater(this::updateGUI);
  }

  @Override
  public void onException(Throwable throwable)
  {
	// only onConnectionStateChanged matters.
  }
}
