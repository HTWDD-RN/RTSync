package de.dmos.rtsync.client;

import java.awt.Color;
import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSessionHandler;

import de.dmos.rtsync.client.internalinterfaces.ClientOperationSync;
import de.dmos.rtsync.client.internalinterfaces.CursorUpdater;
import de.dmos.rtsync.client.internalinterfaces.OutgoingClientMessage;
import de.dmos.rtsync.client.internalinterfaces.SimpleStompSessionSync;
import de.dmos.rtsync.customotter.AbstractOperationSync;
import de.dmos.rtsync.customotter.CustomEditorControl;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.SimpleStateMessage;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.serializers.SimpleMessageSerialization;
import jakarta.annotation.PreDestroy;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.ComposeException;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.TransformException;
import se.l4.otter.operations.combined.CombinedHandler;

public class RTSimpleClientOperationSync extends AbstractOperationSync
implements
ClientOperationSync,
SimpleStompSessionSync,
CursorUpdater
{
  private static final Logger						   LOG					 =
	  LoggerFactory.getLogger(RTSimpleClientOperationSync.class);

  private static final List<String>					  INITIAL_SUBSCRIPTIONS	= List
	  .of(
		EndpointPaths.USER_QUEUE_EXCEPTION,
		EndpointPaths.TOPIC_SUBSCRIBERS,
		EndpointPaths.TOPIC_OPERATIONS,
		EndpointPaths.TOPIC_CURSORS,
		EndpointPaths.APP_INIT_CLIENT);

  protected final BlockingQueue<OutgoingClientMessage> _messageQueue;
  protected final StompSessionHandler				 _stompSessionHandler;
  private final ClientConnectionHandler				   _connectionHandler;
  private final ClientSubscriptionContainer			   _subscriptionContainer;

  private ClientSynchronizingThread					   _connectionThread;
  //  private boolean                                    _synchronizationRunning;

  private ExecutorService                            _executorService    = null;

  /**
   * Indicates whether the server notified the client about an exeption. The client assumes that this is because of a
   * synchronization problem.
   */
  private boolean                                    _badSynchronization = false;
  private boolean									   _subscribed;

  public RTSimpleClientOperationSync(
	CustomEditorControl<Operation<CombinedHandler>> control,
	String preferredName,
	Color preferredColor)
  {
	super(control);
	_connectionHandler = new ClientConnectionHandler(this, preferredName, preferredColor);
	_subscriptionContainer = new ClientSubscriptionContainer(_connectionHandler, this);
	_messageQueue = new LinkedBlockingQueue<>();
	_stompSessionHandler = new SimpleStompSessionHandler(this);
	Runtime.getRuntime().addShutdownHook(new Thread(this::stopSynchronizingWithServer));
  }

  protected ClientSynchronizingThread createSynchronizingThread(URI uri)
  {
	return new ClientSynchronizingThread(
	  uri,
	  _messageQueue,
	  _connectionHandler,
	  _stompSessionHandler,
	  SimpleMessageSerialization.getCombinedMessageConverter(),
	  INITIAL_SUBSCRIPTIONS);
  }

  public void setExecutorService(ExecutorService executorService)
  {
	_executorService = executorService;
  }

  @Override
  public boolean isConnected() {
	return _connectionThread != null && _connectionThread.isConnected();
  }

  @Override
  public void send(TaggedOperation<Operation<CombinedHandler>> op)
  {
	if ( _badSynchronization )
	{
	  return;
	}
	_messageQueue.add(new BasicMessage(EndpointPaths.APP_SEND_OPERATION, op));
  }

  @Override
  public void close()
  {
	_messageQueue.clear();
	stopSynchronizingWithServer();
  }

  @Override
  public void startSynchronizingWithServer(URI uri)
  {
	//    _synchronizationRunning = true;
	_subscribed = false;
	terminateConnectionThreadIfPossible();

	_connectionThread = createSynchronizingThread(uri);
	try
	{
	  if ( _executorService != null )
	  {
		_executorService.submit(_connectionThread);
	  }
	  else
	  {
		_connectionThread.start();
	  }
	}
	catch (IllegalThreadStateException itse)
	{
	  itse.printStackTrace();
	}
	catch (Exception ex)
	{
	  ex.printStackTrace();
	}
  }

  @Override
  @PreDestroy
  public void stopSynchronizingWithServer()
  {
	terminateConnectionThreadIfPossible();
  }

  private void terminateConnectionThreadIfPossible()
  {
	if ( _connectionThread != null )
	{
	  _connectionThread.terminate();
	  _connectionThread = null;
	}
  }

  @Override
  public void onServerMessageException(StompHeaders headers, String exceptionMessage)
  {
	onSynchronizationProblem(new Exception(exceptionMessage), true);
  }

  public void onSynchronizationProblem(Throwable throwable, boolean queryLatest)
  {
	if ( !_badSynchronization )
	{
	  _badSynchronization = true;
	  removeSendOperationsFromMessageQueue();
	  _connectionHandler.onConnectionStateChanged(ConnectionState.CONNECTED_BAD_SYNCHRONIZATION, throwable);
	}
	if ( queryLatest )
	{
	  _messageQueue.add(new SubscribeMessage(EndpointPaths.APP_GET_LATEST_OPERATION));
	}
  }

  private void removeSendOperationsFromMessageQueue()
  {
	_messageQueue
	.removeIf(
	  m -> m instanceof BasicMessage basicMessage
	  && EndpointPaths.APP_SEND_OPERATION.equals(basicMessage.destination()));
  }

  @Override
  public void onSubscribersReceived(StompHeaders headers, List<Subscriber> subscribers)
  {
	_subscriptionContainer.onSubscribersReceived(subscribers);
  }

  @Override
  public void onCursorsMessageReceived(StompHeaders headers, UserCursors cursorsMessage)
  {
	_subscriptionContainer.onUserCursorsReceived(cursorsMessage);
  }

  @Override
  public void updateCursor(CursorPosition position)
  {
	_messageQueue.add(new BasicMessage(EndpointPaths.APP_UPDATE_OWN_CURSORS, position));
  }

  @Override
  public void onStateMessageReceived(StompHeaders headers, SimpleStateMessage stateMessage)
  {
	_connectionHandler.onOwnNameOrColorChanged(stateMessage.selfSubscriber());
	_connectionHandler.onConnectionStateChanged(ConnectionState.CONNECTED, null);
	onTaggedOperationReceived(stateMessage.rtState().taggedOperation(), true);
	if ( stateMessage.rtState().userCursors() != null )
	{
	  stateMessage.rtState().userCursors().forEach(_subscriptionContainer::onUserCursorsReceived);
	}
  }

  @Override
  public void onTaggedOperationReceived(StompHeaders headers, TaggedUserOperation userOp, boolean isWholeState)
  {
	onTaggedOperationReceived(userOp, isWholeState);
  }

  @Override
  public void onTaggedOperationReceived(TaggedUserOperation userOp, boolean wholeState) throws TransformException
  {
	try (CloseableLock lock = _control.lock())
	{
	  if ( !_subscribed || (_badSynchronization && wholeState) )
	  {
		_badSynchronization = false;
		_subscribed = true;
		_connectionHandler.onConnectionStateChanged(ConnectionState.CONNECTED, null);
	  }
	  TaggedUserOperation storedOperation = storeTaggedOperation(userOp, wholeState);
	  LOG.debug("TaggedOperation received: {} stored as {}", userOp, storedOperation);
	  notifyListeners(storedOperation, wholeState);
	}
	catch (ComposeException | TransformException ex)
	{
	  onSynchronizationProblem(ex, !wholeState);
	  if ( wholeState )
	  {
		IncompatibleModelResolution resolution = _subscriptionContainer.onIncompatibleOperationReceived(userOp);
		if ( resolution == IncompatibleModelResolution.OVERWRITE_LOCAL_CHANGES )
		{
		  _badSynchronization = false;
		  _control.setBaseOperation(userOp);
		  notifyListeners(userOp, true);
		  //          _editor.resetToOperation(receivedOperation);
		}
		else if ( resolution == IncompatibleModelResolution.OVERWRITE_REMOTE_CHANGES )
		{
		  // TODO: Overwrite the server's changes.
		  throw new RuntimeException("The function to overwrite the server's state is not yet available.");
		}
	  }
	}
  }

  public ClientSubscriptionContainer getSubscriptionContainer()
  {
	return _subscriptionContainer;
  }

  @Override
  public void onException(Throwable throwable)
  {
	_connectionHandler.onException(throwable);
  }

  @Override
  public ClientConnectionHandler getConnectionHandler()
  {
	return _connectionHandler;
  }

  @Override
  public ClientSynchronizingThread getSyncThread()
  {
	return _connectionThread;
  }
}
