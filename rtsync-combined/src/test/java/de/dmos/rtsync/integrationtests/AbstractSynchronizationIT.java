package de.dmos.rtsync.integrationtests;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionEvaluationListener;
import org.awaitility.core.EvaluatedCondition;
import org.awaitility.core.TimeoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.stomp.ConnectionLostException;

import de.dmos.rtsync.client.ClientConnectionHandler;
import de.dmos.rtsync.client.ConnectionState;
import de.dmos.rtsync.listeners.ConnectionListener;
import de.dmos.rtsync.listeners.IncompatibleModelListener;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.util.SharedObjectHelper;
import se.l4.otter.engine.EditorControl;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedString;

abstract class AbstractSynchronizationIT
{
  private static final Logger	 LOG					  =
	  LoggerFactory.getLogger(AbstractSynchronizationIT.class);

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
  ClientConnectionHandler		 _connectionHandler1;
  ClientConnectionHandler		 _connectionHandler2;
  ClientConnectionListener		 _connectionListener1;
  ClientConnectionListener		 _connectionListener2;

  protected void waitUntilIncompatibleOperationReceived(ClientConnectionListener listener)
  {
	waitForSync(
	  String
	  .format(
		"%s to receive an incompatible operation",
		listener._connectionHandler.getPreferredName()),
	  () -> listener.getLastReceivedIncompatibleOperation() != null,
	  maxLocalSyncDuration);
  }

  protected void waitForReceivedVersion(
	ClientConnectionHandler connectionHandler,
	EditorControl<?> control,
	long version)
  {
	waitForSync(
	  String.format("for %s to receive version %d from the server", connectionHandler.getPreferredName(), version),
	  () -> control.getLatestVersion() == version,
	  maxLocalSyncDuration);
  }

  protected void waitUntilBothClientsAreReadyToSynchronize()
  {
	waitForSync(
	  "connection of client 1 and 2",
	  () -> _connectionListener1.isReadyToSynchronize() && _connectionListener2.isReadyToSynchronize(),
	  maxConnectDuration);
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

  protected void waitForSync(String awaitedEventString, Callable<Boolean> condition, Duration maxDuration)
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

  abstract class ClientConnectionListener
  implements
  ConnectionListener,
  IncompatibleModelListener
  {
	private static final Logger			  LOG							  =
		LoggerFactory.getLogger(ClientConnectionListener.class);

	private final ClientConnectionHandler _connectionHandler;
	private final List<Throwable>		  _errorsDuringConnectingAttempts = new ArrayList<>();
	private final List<Throwable>		  _errorsWithWorkingConnection	  = new ArrayList<>();
	private boolean						  _resetLocally					  = true;
	private TaggedUserOperation			  _lastReceivedIncompatibleOperation;
	private ConnectionState				  _state						  = ConnectionState.NOT_CONNECTED;

	ClientConnectionListener(ClientConnectionHandler connectionHandler)
	{
	  _connectionHandler = connectionHandler;
	  connectionHandler.addConnectionListener(this);
	}

	public void setResetLocally(boolean resetLocally)
	{
	  _resetLocally = resetLocally;
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
	  if ( throwable instanceof ConnectionLostException )
	  {
		LOG.warn(throwable.getLocalizedMessage());
	  }
	  LOG.error(_connectionHandler.getPreferredName() + " has an error: ", throwable);
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
		_connectionHandler.getPreferredName(),
		_resetLocally ? "local" : "remote");
	  return _resetLocally
		  ? IncompatibleModelResolution.OVERWRITE_LOCAL_CHANGES
			  : IncompatibleModelResolution.OVERWRITE_REMOTE_CHANGES;
	}
  }
}
