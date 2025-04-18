package de.dmos.rtsync.client;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.ConnectionLostException;

import de.dmos.rtsync.client.internalinterfaces.ClientOperationSync;
import de.dmos.rtsync.client.internalinterfaces.HasPreferredNameAndColor;
import de.dmos.rtsync.listeners.ConnectionListener;
import de.dmos.rtsync.listeners.SelfSubscriberListener;
import de.dmos.rtsync.listeners.SelfSubscriptionSupplier;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.util.WeakLinkedList;

/**
 * Represents a client in a network. Listeners can register to several events and are stored internally in weak lists.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class ClientConnectionHandler
implements
SelfSubscriptionSupplier,
HasPreferredNameAndColor,
ConnectionListener
{
  private static final Logger						   LOG				 =
	  LoggerFactory.getLogger(ClientConnectionHandler.class);

  private final WeakLinkedList<ConnectionListener>	   _connectionListeners;
  private final WeakLinkedList<SelfSubscriberListener> _selfSubscriberListeners;
  private final ClientOperationSync					   _clientSync;

  private String									   _preferredName;
  private Color										   _preferredColor;
  private Subscriber								   _selfSubscriber;
  private boolean									   _reconnectOnError = true;
  private ConnectionState							   _currentState	 = ConnectionState.NOT_CONNECTED;

  protected ClientConnectionHandler(ClientOperationSync clientSync)
  {
	this(clientSync, null, null);
  }

  protected ClientConnectionHandler(ClientOperationSync clientSync, String preferredName)
  {
	this(clientSync, preferredName, null);
  }

  public ClientConnectionHandler(ClientOperationSync clientSync, String preferredName, Color preferredColor)
  {
	super();
	_clientSync = clientSync;
	_connectionListeners = new WeakLinkedList<>();
	_selfSubscriberListeners = new WeakLinkedList<>();
	_preferredName = preferredName;
	_preferredColor = preferredColor;
  }

  public ConnectionState getConnectionState()
  {
	return _currentState;
  }

  public void startSynchronizingWithServer(URI uri)
  {
	_clientSync.startSynchronizingWithServer(uri);
  }

  public void stopSynchronizingWithServer()
  {
	_clientSync.stopSynchronizingWithServer();
  }

  /**
   * Adds the {@link ConnectionListener} as a weak reference to the list. Better don't use an anonymous function as
   * listener because it may get deinitialized quickly.
   */

  public void addConnectionListener(ConnectionListener listener)
  {
	_connectionListeners.addAsWeakReference(listener);
  }

  public boolean removeConnectionListener(ConnectionListener listener)
  {
	return _connectionListeners.removeReferencedObject(listener);
  }

  /**
   * Adds the {@link SelfSubscriberListener} as a weak reference to the list. Better don't use an anonymous function as
   * listener because it may get deinitialized quickly.
   */

  @Override
  public void addSelfSubscriberListener(SelfSubscriberListener listener)
  {
	_selfSubscriberListeners.addAsWeakReference(listener);
  }

  @Override
  public boolean removeSelfSubscriberListener(SelfSubscriberListener listener)
  {
	return _selfSubscriberListeners.removeReferencedObject(listener);
  }

  public boolean isReconnectOnError()
  {
	return _reconnectOnError;
  }

  public void setReconnectOnError(boolean reconnectOnError)
  {
	_reconnectOnError = reconnectOnError;
  }

  /**
   * {@inheritDoc}
   *
   * @implNote When establishing a new connection to a server, then the preferred name is sent in a http header.
   * @implNote If connected while this is called, then a message containing the new name is sent to the server.
   */
  public void setPreferredName(String preferredName)
  {
	_preferredName = preferredName;
	ClientSynchronizingThread syncThread = _clientSync.getSyncThread();
	if ( syncThread != null )
	{
	  syncThread.changePreferredName(preferredName);
	}
  }

  @Override
  public String getPreferredName()
  {
	return _preferredName;
  }

  @Override
  public Color getPreferredColor()
  {
	return _preferredColor;
  }

  /**
   * Gets this client's {@link Subscriber} that is seen by the network. Note, that this doesn't have to match the
   * preferred settings.
   */
  @Override
  public Subscriber getSelfSubscriber()
  {
	return _selfSubscriber;
  }

  /**
   * {@inheritDoc}
   *
   * @implNote When establishing a new connection to a server, then the preferred color is sent in a http header.
   * @implNote If connected while this is called, then a message containing the new color is sent to the server.
   */
  public void setPreferredColor(Color preferredColor)
  {
	_preferredColor = preferredColor;
	ClientSynchronizingThread syncThread = _clientSync.getSyncThread();
	if ( syncThread != null )
	{
	  syncThread.changePreferredColor(preferredColor);
	}
  }

  @Override
  public void onConnectionStateChanged(ConnectionState currentState, Throwable throwable)
  {
	_currentState = currentState;

	_connectionListeners.toStrongStream().forEach(l -> l.onConnectionStateChanged(currentState, throwable));

	ClientSynchronizingThread syncThread = _clientSync.getSyncThread();
	if ( currentState == ConnectionState.NOT_CONNECTED
		&& throwable != null
		&& _reconnectOnError
		&& syncThread != null )
	{
	  syncThread.reconnect();
	}
  }

  @Override
  public void onException(Throwable throwable)
  {
	if ( (throwable instanceof IllegalStateException || throwable instanceof IOException) && _reconnectOnError )
	{
	  if ( !_clientSync.isConnected() )
	  {
		notifyListenersOfException(throwable);
	  }
	  _clientSync.getSyncThread().reconnect();
	}
	else if ( throwable instanceof ConnectionLostException || throwable instanceof CompletionException )
	{
	  onConnectionStateChanged(ConnectionState.NOT_CONNECTED, throwable);
	  _clientSync.stopSynchronizingWithServer();
	}
	else if ( throwable instanceof MessageDeliveryException )
	{
	  notifyListenersOfException(throwable);
	}
	else
	{
	  notifyListenersOfException(throwable);
	  LOG.error("unexpected exception", throwable);
	}
  }

  private void notifyListenersOfException(Throwable throwable)
  {
	_connectionListeners.toStrongStream().forEach(l -> l.onException(throwable));
  }

  public void onOwnNameOrColorChanged(Subscriber subscriber)
  {
	_selfSubscriber = subscriber;
	_selfSubscriberListeners.toStrongStream().forEach(l -> l.onOwnNameOrColorChanged(subscriber));
  }
}
