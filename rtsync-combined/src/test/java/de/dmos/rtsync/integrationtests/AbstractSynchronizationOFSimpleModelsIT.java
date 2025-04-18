package de.dmos.rtsync.integrationtests;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionEvaluationListener;
import org.awaitility.core.EvaluatedCondition;
import org.awaitility.core.TimeoutEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import de.dmos.rtsync.client.ClientConnectionHandler;
import de.dmos.rtsync.client.ConnectionState;
import de.dmos.rtsync.client.RTSyncSimpleClient;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.listeners.ConnectionListener;
import de.dmos.rtsync.listeners.IncompatibleModelListener;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.server.simple.RTSyncSimpleServerController;
import de.dmos.rtsync.test.VersionChangeListener;
import de.dmos.rtsync.util.SharedObjectHelper;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedString;

abstract class AbstractSynchronizationOFSimpleModelsIT
{
  private static final Logger    LOG                      = LoggerFactory.getLogger(AbstractSynchronizationOFSimpleModelsIT.class);

  // The tests have different intentions about the expected behaviour concerning the merge or rejection of model differences.
  // So mind the setting '_server.setRejectOldHistoryIds(bool)'.

  // Note that timeouts from Awaitility.await() cause tests to fail and @afterEach to run. That shuts down the server and clients
  // which in turn may asynchronously cause disconnects and exceptions at other places.
  // So if tests fail, check if it's because the following timeout durations are too low by raising them.

  static final Duration          maxServerStartupDuration = Duration.ofMillis(10000);
  static final Duration			 pollInterval			  = Duration.ofMillis(100);
  // A Test has failed with a maxConnectDuration of only 700ms.
  static final Duration			 maxConnectDuration		  = Duration.ofMillis(1200);
  // This should be high enough to give clients in the tougher tests enough time to receive an exception from the server, query
  // the current operation, apply it locally and sent the result to the server which must then send it to the other client that
  // must also apply it.
  // A Test has failed with a maxLocalSyncDuration of only 300ms.
  static final Duration			 maxLocalSyncDuration	  = Duration.ofMillis(900);

  @LocalServerPort
  int                            _port;
  URI                            _uri;
  ConfigurableApplicationContext _serverContext;
  RTSyncSimpleServerController	 _server;
  RTSyncSimpleClient			 _client1;
  RTSyncSimpleClient			 _client2;
  ClientConnectionListener       _connectionListener1;
  ClientConnectionListener       _connectionListener2;
  ClientConnectionHandler		 _connectionHandler1;
  ClientConnectionHandler		 _connectionHandler2;

  @BeforeEach
  public void setupServerAnd2Clients(
	@Autowired ConfigurableApplicationContext serverContext,
	@Autowired RTSyncSimpleServerController server)
  {
	_serverContext = serverContext;
	_server = server;
	_uri = new DefaultUriBuilderFactory()
		.builder()
		.scheme("ws")
		.host("localhost")
		.port(_port)
		.path(EndpointPaths.WEB_SOCKET)
		.build();
	_client1 = new RTSyncSimpleClient("Client 1", null);
	_client2 = new RTSyncSimpleClient("Client 2", null);
	_connectionHandler1 = _client1.getConnectionHandler();
	_connectionHandler2 = _client2.getConnectionHandler();
	_connectionListener1 = new ClientConnectionListener(_client1);
	_connectionListener2 = new ClientConnectionListener(_client2);
	Awaitility.await().atMost(maxServerStartupDuration).until(() -> _serverContext.isRunning());
  }

  @AfterEach
  public void shutdown2ClientSyncs()
  {
	_client1.getSync().close();
	_client2.getSync().close();
  }

  protected void waitUntilIncompatibleOperationReceived(ClientConnectionListener listener)
  {
	waitForSync(
	  String
	  .format(
		"%s to receive an incompatible operation",
		listener._client.getConnectionHandler().getPreferredName()),
	  () -> listener.getLastReceivedIncompatibleOperation() != null,
	  maxLocalSyncDuration);
  }

  protected void waitForReceivedVersion(RTSyncSimpleClient client, long version)
  {
	waitForSync(
	  String
	  .format(
		"for %s to receive version %d from the server",
		client.getConnectionHandler().getPreferredName(),
		version),
	  () -> client.getControl().getLatestVersion() == version,
	  maxLocalSyncDuration);
  }

  protected void waitUntilBothClientsAreReadyToSynchronize()
  {
	waitForSync(
	  "connection of client 1 and 2",
	  () -> _connectionListener1.isReadyToSynchronize() && _connectionListener2.isReadyToSynchronize(),
	  maxConnectDuration);
  }

  /**
   * Waits until the shared string with the given id in both models is equal or the version listeners have received a
   * match.
   */
  protected void waitUntilMatchingSharedStringVersionReceived(
	String id,
	VersionChangeListener vcl1,
	VersionChangeListener vcl2)
  {
	waitForSync(
	  "both client's models to have received a matching SharedString with the id '{" + id + "}'",
	  () -> makeMatchingVersionsIfPossible(id, vcl1, vcl2),
	  maxLocalSyncDuration);
  }

  protected boolean makeMatchingVersionsIfPossible(
	String id,
	VersionChangeListener vcl1,
	VersionChangeListener vcl2)
  {
	Model m1 = _client1.getModel();
	Model m2 = _client2.getModel();
	SharedString ss1 = m1.get(id);
	SharedString ss2 = m2.get(id);
	if ( ss1 == null || ss2 == null )
	{
	  return false;
	}
	String s1 = ss1.get();
	String s2 = ss2.get();
	if ( s1.equals(s2) )
	{
	  return true;
	}
	if ( s1.equals(vcl2.getReplacedOldValue()) )
	{
	  LOG.info("makeMatchingVersionsIfPossible sets client 2's {} to {}", id, s1);
	  ss2.set(s1);
	  return true;
	}
	if ( s2.equals(vcl1.getReplacedOldValue()) )
	{
	  LOG.info("makeMatchingVersionsIfPossible sets client 1's {} to {}", id, s2);
	  ss1.set(s2);
	  return true;
	}
	return false;
  }

  /**
   * Waits until the two client models' objects are equal
   */
  protected void waitUntilClientModelObjectsEqual()
  {
	CustomModel m1 = _client1.getModel();
	CustomModel m2 = _client2.getModel();
	waitForSync(
	  "synchronization of both client's models",
	  () -> SharedObjectHelper.getDifferences(m1, m2),
	  Collection::isEmpty,
	  getModelDifferenceListener(m1, m2),
	  maxLocalSyncDuration);
  }

  protected ConditionEvaluationListener<List<String>> getModelDifferenceListener(Model m1, Model m2)
  {
	return new ConditionEvaluationListener<>() {
	  List<String> _differences;

	  @Override
	  public void conditionEvaluated(EvaluatedCondition<List<String>> condition)
	  {
		_differences = condition.getValue();
	  }

	  private Object getObjectAtPath(Model model, String path)
	  {
		Object obj = SharedObjectHelper.getObjectAtPath(model, path);
		return obj instanceof SharedString ss ? ss.get() : obj;
	  }

	  @Override
	  public void onTimeout(TimeoutEvent event)
	  {
		StringBuilder builder = new StringBuilder();
		if ( _differences == null )
		{
		  builder.append("The differences have not been checked! Maybe the timeout was too early.");
		}
		else
		{
		  builder.append("The models have the following differences:\n");
		  _differences.forEach(d -> {
			builder.append(d);
			builder.append(": ");
			appendObject(getObjectAtPath(m1, d), builder);
			builder.append(" <> ");
			appendObject(getObjectAtPath(m2, d), builder);
			builder.append("\n");
		  });
		}
		LOG.error(builder.toString());
	  }

	  private void appendObject(Object obj, StringBuilder builder)
	  {
		if ( obj == null )
		{
		  builder.append("null");
		  return;
		}
		builder.append(obj.getClass());
		builder.append(" ");
		builder.append(obj.toString());
	  }
	};
  }

  /**
   * Waits until the two client models' objects with the given id are equal.
   */
  protected void waitUntilClientModelObjectsEqual(Collection<String> ids)
  {
	Model m1 = _client1.getModel();
	Model m2 = _client2.getModel();
	waitForSync(
	  "synchronization of both client's models",
	  () -> ids.stream().allMatch(id -> SharedObjectHelper.sharedObjectEquals(m1.get(id), m2.get(id))),
	  maxLocalSyncDuration);
  }

  private void waitForSync(String awaitedEventString, Callable<Boolean> condition, Duration maxDuration)
  {
	LOG.debug("Awaiting the {}...", awaitedEventString);
	Awaitility.await().atMost(maxDuration).pollInterval(pollInterval).until(condition);
  }

  protected <T> void waitForSync(
	String awaitedEventString,
	Callable<T> supplier,
	Predicate<? super T> predicate,
	ConditionEvaluationListener<T> listener,
	Duration maxDuration)
  {
	LOG.debug("Awaiting the {}...", awaitedEventString);
	Awaitility
	.await()
	.conditionEvaluationListener(listener)
	.atMost(maxDuration)
	.pollInterval(pollInterval)
	.until(supplier, predicate);
  }

  class ClientConnectionListener implements ConnectionListener, SubscriberListener, IncompatibleModelListener
  {
	private static final Logger   LOG                             =
		LoggerFactory.getLogger(ClientConnectionListener.class);

	private final RTSyncSimpleClient _client;
	private final List<Throwable> _errorsDuringConnectingAttempts = new ArrayList<>();
	private final List<Throwable> _errorsWithWorkingConnection    = new ArrayList<>();
	private boolean               _resetLocally                   = true;
	private TaggedUserOperation   _lastReceivedIncompatibleOperation;
	private ConnectionState       _state                          = ConnectionState.NOT_CONNECTED;
	private List<Subscriber>    _currentSubscribers             = null;

	ClientConnectionListener(RTSyncSimpleClient client)
	{
	  _client = client;
	  client.getConnectionHandler().addConnectionListener(this);
	  client.getSubscriptionContainer().addWeakSubscriberListener(this);
	  client.getSubscriptionContainer().addWeakIncompatibleModelListener(this);
	}

	public void setResetLocally(boolean resetLocally)
	{
	  _resetLocally = resetLocally;
	}

	public List<Subscriber> getCurrentSubscribers()
	{
	  return _currentSubscribers;
	}

	public boolean isReadyToSynchronize()
	{
	  return _state == ConnectionState.CONNECTED || _state == ConnectionState.CONNECTED_BAD_SYNCHRONIZATION;
	}

	public List<Throwable> getErrorsDuringConnectingAttempts()
	{
	  return _errorsDuringConnectingAttempts;
	}

	public List<Throwable> getErrorsWithWorkingConnection()
	{
	  return _errorsWithWorkingConnection;
	}

	@Override
	public void onConnectionStateChanged(ConnectionState currentState, Throwable throwable)
	{
	  boolean wasConnecting = _state.equals(ConnectionState.CONNECTING);
	  _state = currentState;
	  if ( throwable != null )
	  {
		handleError(throwable, wasConnecting);
	  }
	}

	@Override
	public void onException(Throwable throwable)
	{
	  handleError(throwable, _state.equals(ConnectionState.CONNECTING));
	}

	private void handleError(Throwable throwable, boolean thrownWhileConnecting)
	{
	  LOG.error(_client.getConnectionHandler().getPreferredName() + " has an error: ", throwable);
	  if ( !thrownWhileConnecting )
	  {
		throwable.printStackTrace();
		_errorsWithWorkingConnection.add(throwable);
	  }
	  else
	  {
		_errorsDuringConnectingAttempts.add(throwable);
	  }
	}

	@Override
	public void onSubscribersReceived(List<Subscriber> subscribers)
	{
	  _currentSubscribers = subscribers;
	}

	public TaggedUserOperation getLastReceivedIncompatibleOperation()
	{
	  return _lastReceivedIncompatibleOperation;
	}

	@Override
	public IncompatibleModelResolution onIncompatibleModelReceived(TaggedUserOperation taggedUserOperation)
	{
	  _lastReceivedIncompatibleOperation = taggedUserOperation;
	  LOG
	  .info(
		"{}: Incompatible model received. Resetting {} model.",
		_client.getConnectionHandler().getPreferredName(),
		_resetLocally ? "local" : "remote");
	  return _resetLocally
		  ? IncompatibleModelResolution.OVERWRITE_LOCAL_CHANGES
			  : IncompatibleModelResolution.OVERWRITE_REMOTE_CHANGES;
	}
  }
}
