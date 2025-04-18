package de.dmos.rtsync.client;

import java.util.List;

import org.springframework.messaging.simp.stomp.StompSession.Subscription;

import de.dmos.rtsync.client.internalinterfaces.CursorUpdater;
import de.dmos.rtsync.client.internalinterfaces.ReceiptHandler;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.listeners.CursorsListener;
import de.dmos.rtsync.listeners.IncompatibleModelListener;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.util.WeakLinkedList;

/**
 * A container for RTSync related data which groups local subscribers for one model.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class ClientSubscriptionContainer
implements
CursorsHandler,
SubscriptionSupplier,
ReceiptHandler<Subscription>
{
  private final WeakLinkedList<IncompatibleModelListener> _incompatibleModelListeners;
  private final WeakLinkedList<SubscriberListener>				_subscriberListeners;
  private final WeakLinkedList<CursorsListener>					_cursorsListeners;
  private final ClientConnectionHandler							_clientConnectionHandler;
  private final CursorUpdater									_cursorUpdater;

  private CustomModel									  _model;
  private List<Subscriber>										_subscribers;
  private Subscription									  _subscription;


  public ClientSubscriptionContainer(ClientConnectionHandler clientConnectionHandler, CursorUpdater cursorUpdater)
  {
	_clientConnectionHandler = clientConnectionHandler;
	_cursorUpdater = cursorUpdater;
	_incompatibleModelListeners = new WeakLinkedList<>();
	_subscriberListeners = new WeakLinkedList<>();
	_cursorsListeners = new WeakLinkedList<>();
  }

  public ClientConnectionHandler getClientConnectionHandler()
  {
	return _clientConnectionHandler;
  }

  public Subscription getSubscription()
  {
	return _subscription;
  }

  public CustomModel getModel()
  {
	return _model;
  }

  void setModel(CustomModel model)
  {
	_model = model;
  }

  /**
   * Adds the {@link IncompatibleModelListener} as a weak reference to the list. Better don't use an anonymous function
   * as listener because it may get deinitialized quickly.
   */
  public void addWeakIncompatibleModelListener(IncompatibleModelListener listener)
  {
	_incompatibleModelListeners.addAsWeakReference(listener);
  }

  public boolean removeWeakIncompatibleModelListener(IncompatibleModelListener listener)
  {
	return _incompatibleModelListeners.removeReferencedObject(listener);
  }

  /**
   * Adds the {@link SubscriberListener} as a weak reference to the list. Better don't use an anonymous function as
   * listener because it may get deinitialized quickly.
   */
  @Override
  public void addWeakSubscriberListener(SubscriberListener listener)
  {
	_subscriberListeners.addAsWeakReference(listener);
  }

  @Override
  public boolean removeWeakSubscriberListener(SubscriberListener listener)
  {
	return _subscriberListeners.removeReferencedObject(listener);
  }

  @Override
  public List<Subscriber> getSubscribers()
  {
	return _subscribers;
  }

  public void onSubscribersReceived(List<Subscriber> subscribers)
  {
	_subscribers = subscribers;
	_subscriberListeners.toStrongStream().forEach(l -> l.onSubscribersReceived(subscribers));
	Subscriber selfSubscriber = _clientConnectionHandler.getSelfSubscriber();
	if ( selfSubscriber != null )
	{
	  subscribers
	  .stream()
	  .filter(s -> s.getId() == selfSubscriber.getId())
	  .findAny()
	  .ifPresent(
		_clientConnectionHandler::onOwnNameOrColorChanged);
	}
  }

  public IncompatibleModelResolution onIncompatibleOperationReceived(TaggedUserOperation receivedOperation)
  {
	return _incompatibleModelListeners
		.toStrongStream()
		.map(l -> l.onIncompatibleModelReceived(receivedOperation))
		.filter(resolution -> resolution != IncompatibleModelResolution.NONE)
		.findFirst()
		.orElse(IncompatibleModelResolution.NONE);
  }

  @Override
  public void addCursorsListener(CursorsListener listener)
  {
	_cursorsListeners.addAsWeakReference(listener);
  }

  @Override
  public boolean removeCursorsListener(CursorsListener listener)
  {
	return _cursorsListeners.removeReferencedObject(listener);
  }

  @Override
  public void updateCursor(CursorPosition position)
  {
	_cursorUpdater.updateCursor(position);
  }

  public void onUserCursorsReceived(UserCursors userCursors)
  {
	Subscriber selfSubscriber = _clientConnectionHandler.getSelfSubscriber();
	if ( userCursors.userId().equals(selfSubscriber.getId()) )
	{
	  return;
	}
	_cursorsListeners.toStrongStream().forEach(l -> l.onUserCursorsReceived(userCursors));
  }

  @Override
  public void receiptReceived(Subscription receipt)
  {
	_subscription = receipt;
  }
}
