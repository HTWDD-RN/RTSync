package de.dmos.rtsync.client;

import java.awt.Color;
import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import de.dmos.rtsync.client.internalinterfaces.OutgoingClientMessage;
import de.dmos.rtsync.network.CommunicationConstants;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.serializers.MessageSerialization;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

public class ClientSynchronizingThread extends Thread
{
  private static final Logger                        LOG                      =
	  LoggerFactory.getLogger(ClientSynchronizingThread.class);

  private final URI                                  _uri;
  private final BlockingQueue<OutgoingClientMessage> _messageQueue;
  private final WebSocketStompClient                 _wsStompClient;
  protected final StompSessionHandler				 _stompSessionHandler;
  private final ClientConnectionHandler				 _connectionHandler;
  private final List<String>						 _initialSubscriptions;

  protected StompSession							 _stompSession;
  private boolean                                    _terminated              = false;
  private boolean                                    _shouldReconnect;
  private long                                       _connectionTimeoutMillis = 10000;

  public ClientSynchronizingThread(
	URI uri,
	BlockingQueue<OutgoingClientMessage> messageQueue,
	ClientConnectionHandler                   connectionHandler,
	StompSessionHandler stompSessionHandler,
	MessageConverter messageConverter,
	List<String> initialSubscriptions)
  {
	this(
	  uri,
	  messageQueue,
	  connectionHandler,
	  stompSessionHandler,
	  messageConverter,
	  initialSubscriptions,
	  null);
  }

  public ClientSynchronizingThread(
	URI uri,
	BlockingQueue<OutgoingClientMessage> messageQueue,
	ClientConnectionHandler                   connectionHandler,
	StompSessionHandler stompSessionHandler,
	MessageConverter messageConverter,
	List<String> initialSubscriptions,
	ScheduledExecutorService scheduledExecutorService)
  {
	super(
	  (connectionHandler.getPreferredName() != null
	  ? connectionHandler.getPreferredName() + "'s "
		  : "")
	  + "client synchronizing thread");
	_uri = uri;
	_messageQueue = messageQueue;

	WebSocketContainer container = ContainerProvider.getWebSocketContainer();
	container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
	container.setDefaultMaxTextMessageBufferSize(1024 * 1024);

	WebSocketClient wsClient = new StandardWebSocketClient(container);
	if ( CommunicationConstants.USE_SOCK_JS )
	{
	  wsClient = new SockJsClient(List.of(new WebSocketTransport(wsClient)));
	}
	_wsStompClient = new WebSocketStompClient(wsClient);
	scheduledExecutorService =
		scheduledExecutorService != null ? scheduledExecutorService : Executors.newSingleThreadScheduledExecutor();
	_wsStompClient.setTaskScheduler(new ConcurrentTaskScheduler(scheduledExecutorService));
	_wsStompClient.setMessageConverter(messageConverter);
	_wsStompClient.setDefaultHeartbeat(new long[] {10000, 10000});
	_stompSessionHandler = stompSessionHandler;
	_connectionHandler = connectionHandler;
	_initialSubscriptions = initialSubscriptions;
  }

  public boolean isConnected()
  {
	return _stompSession != null && _stompSession.isConnected();
  }

  public String getOwnStompSessionName()
  {
	return _stompSession != null ? _stompSession.getSessionId() : null;
  }

  void setConnectionTimeoutMillis(long newTimeout)
  {
	_connectionTimeoutMillis = newTimeout;
  }

  public void terminate()
  {
	_terminated = true;
	interrupt();
  }

  public void reconnect()
  {
	_shouldReconnect = true;
	interrupt();
  }

  void changePreferredName(String preferredName)
  {
	_messageQueue.add(new BasicMessage(EndpointPaths.APP_SET_OWN_NAME, preferredName));
  }

  void changePreferredColor(Color preferredColor)
  {
	_messageQueue.add(new BasicMessage(EndpointPaths.APP_SET_OWN_COLOR, preferredColor));
  }

  @Override
  public void run()
  {
	LOG.debug("Thread {} started", getName());
	do
	{
	  if ( _shouldReconnect )
	  {
		_shouldReconnect = false;
		interrupted();
		LOG.debug("Thread {} is trying to reconnect.", getName());
	  }
	  else
	  {
		LOG.debug("Thread {} is trying to connect.", getName());
	  }
	  initWsSession();
	  if ( !isConnected() )
	  {
		LOG.warn("Thread {} didn't manage to connect and ends.", getName());
		return;
	  }
	  LOG.debug("Thread {} established the STOMP web socket connection.", getName());
	  //      queryLatest();
	  keepSynchronizingWithServer();
	}
	while (!_terminated && _shouldReconnect);

	if ( isConnected() )
	{
	  Throwable error = null;
	  try
	  {
		_stompSession.disconnect();
	  }
	  catch (MessageDeliveryException mdEx)
	  {
		// This probably means that the server wasn't running, so the disconnect message isn't needed and the exception can be ignored.
	  }
	  catch (IllegalStateException stateEx)
	  {
		error = stateEx;
	  }
	  _connectionHandler.onConnectionStateChanged(ConnectionState.NOT_CONNECTED, error);
	}
	LOG.debug("Thread {} ended.", getName());
  }

  private void initWsSession()
  {
	_connectionHandler.onConnectionStateChanged(ConnectionState.CONNECTING, null);

	WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

	String preferredName = _connectionHandler.getPreferredName();
	if ( preferredName != null && !preferredName.isBlank() )
	{
	  headers.set(CommunicationConstants.PREFERRED_NAME_HEADER, preferredName);
	}
	Color preferredColor = _connectionHandler.getPreferredColor();
	if ( preferredColor != null )
	{
	  String colorString = MessageSerialization.COLOR_SERIALIZER.colorToString(preferredColor);
	  headers.set(CommunicationConstants.PREFERRED_COLOR_HEADER, colorString);
	}

	_stompSession = null;
	try
	{
	  _stompSession = _wsStompClient
		  .connectAsync(_uri.toString(), headers, _stompSessionHandler)
		  .get(_connectionTimeoutMillis, TimeUnit.MILLISECONDS);
	  _connectionHandler.onConnectionStateChanged(ConnectionState.INITIAL_SUBSCRIBING, null);

	  // The subscriptions are done here instead of the stompSessionHandler's afterConnect method because that may cause a MessageDeliveryException due to concurrent sending.
	  // We prefer to do the concurrency handling here.
	  _initialSubscriptions.forEach(s -> _stompSession.subscribe(s, _stompSessionHandler));
	}
	catch (InterruptedException intEx)
	{
	  Thread.currentThread().interrupt();
	  _connectionHandler.onConnectionStateChanged(ConnectionState.NOT_CONNECTED, intEx);
	}
	catch (IllegalArgumentException | TimeoutException | ExecutionException ex)
	{
	  _connectionHandler.onConnectionStateChanged(ConnectionState.NOT_CONNECTED, ex);
	}
  }

  // not used currently.
  //  public void queryLatest()
  //  {
  //    _messageQueue.add(new OutgoingClientMessage(EndpointPaths.APP_GET_LATEST_OPERATION, null));
  //  }

  private void keepSynchronizingWithServer()
  {
	while (!_terminated && !_shouldReconnect)
	{
	  OutgoingClientMessage currentMessage = waitForNextOutgoingMessage();
	  if ( currentMessage == null )
	  {
		continue;
	  }

	  try
	  {
		currentMessage.send(_stompSession, _stompSessionHandler);
	  }
	  catch (IllegalStateException | MessageDeliveryException ex)
	  {
		_connectionHandler.onException(ex);
	  }
	}
  }

  @SuppressWarnings("all")
  private OutgoingClientMessage waitForNextOutgoingMessage()
  {
	try
	{
	  return _messageQueue.take();
	}
	catch (InterruptedException e)
	{
	  interrupted();
	}
	return null;
  }
}