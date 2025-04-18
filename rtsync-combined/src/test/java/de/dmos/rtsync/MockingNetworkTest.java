package de.dmos.rtsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dmos.rtsync.client.BasicMessage;
import de.dmos.rtsync.customotter.AbstractOperationSync;
import de.dmos.rtsync.customotter.CustomEditorControl;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.network.RTSyncSimpleNetworkNode;
import de.dmos.rtsync.test.RTSyncTestHelper;
import de.dmos.rtsync.test.VersionChangeListener;
import se.l4.otter.engine.LocalOperationSync;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.SharedString;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

/**
 * These tests are meant to check whether edits from different users would be transformed and composed correctly by all
 * clients in a network.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
class MockingNetworkTest
{
  static final long     DELAY_MIN               = 0;
  static final long     DELAY_MAX               = 100;
  /**
   * The minimal waiting time to pass when waiting for a node to send their updates. This mustn't be too small, because
   * the asynchronous methods need time to trigger.
   *
   * @see {@link LocalOperationSync#waitForEmpty()} where 100ms is used as sleep duration.
   */
  static final Duration pollDelay              = Duration.ofMillis(100);
  // This must be greater than delayMax and should include a time buffer for handling the sending.
  // testConcurrentChangingOfStringWithNodeRoles has failed with a maxAllowedSyncDuration of only 600ms with a
  // pollDelay of 100ms.
  static final Duration	maxAllowedSyncDuration = Duration.ofMillis(700);
  static final Duration pollInterval           = Duration.ofMillis(20);
  static final String   GREETING_ID            = "test greeting";

  @Test
  void testChangingString()
  {
	RTSyncTestHelper.runMultipleTimes(3, () -> {

	  NetworkMockingRTSyncNode client1 = new NetworkMockingRTSyncNode("Agate");
	  NetworkMockingRTSyncNode client2 = new NetworkMockingRTSyncNode("Bruno");
	  NetworkMockingRTSyncNode.listenToNetwork(client1, client2);

	  String text1 = "Good morning!";
	  client1.createGreeting(text1);

	  client2.waitForGreeting(text1);

	  String text2 = "Good evening!";
	  client2.getGreeting().set(text2);
	  NetworkMockingRTSyncNode.waitForIdleNetwork(client1, client2);
	  assertSharedGreeting(text2, client1, client2);
	});
  }

  @Test
  void testChangingKnownString()
  {
	RTSyncTestHelper.runMultipleTimes(3, () -> {

	  NetworkMockingRTSyncNode client1 = new NetworkMockingRTSyncNode("Agate");
	  NetworkMockingRTSyncNode client2 = new NetworkMockingRTSyncNode("Bruno");
	  NetworkMockingRTSyncNode[] network = {client1, client2};
	  NetworkMockingRTSyncNode.listenToNetwork(network);

	  VersionChangeListener versionListener2 =
		  VersionChangeListener.addAlternativeVersionListener(client2.getModel());

	  // Client 2 prepares a shared greeting but doesn't send it yet.
	  String c2Text1 = "Hi, everyone!";
	  client2.suspendAndCreateGreeting(c2Text1);
	  String c1Text1 = "Good morning!";
	  SharedString greeting1 = client1.createGreeting(c1Text1);

	  client2.waitForGreeting(c1Text1);
	  assertEquals(c2Text1, versionListener2.getReplacedOldValue());

	  client2.getMockingSync().resume(); // This should not make client2 overwrite the new version with the old one.

	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);
	  assertSharedGreeting(c1Text1, network);

	  String c1Text2 = "Hi, everyone and good evening!";
	  greeting1.set(c1Text2);
	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);
	  assertSharedGreeting(c1Text2, network);
	});
  }

  @Test
  void testChangingKnownStringWith3Clients()
  {
	RTSyncTestHelper.runMultipleTimes(3, () -> {

	  NetworkMockingRTSyncNode client1 = new NetworkMockingRTSyncNode("Agate");
	  NetworkMockingRTSyncNode client2 = new NetworkMockingRTSyncNode("Bruno");
	  NetworkMockingRTSyncNode client3 = new NetworkMockingRTSyncNode("Charlotte");
	  NetworkMockingRTSyncNode[] network = {client1, client2, client3};
	  NetworkMockingRTSyncNode.listenToNetwork(network);

	  // Client 2 prepares a shared greeting but doesn't send it yet.
	  String c2Text1 = "Hi, everyone!";
	  client2.suspendAndCreateGreeting(c2Text1);

	  // Client 3 also prepares a shared greeting but doesn't send it yet.
	  String c3Text1 = "Hello!";
	  client3.suspendAndCreateGreeting(c3Text1);

	  VersionChangeListener versionListener2 =
		  VersionChangeListener.addAlternativeVersionListener(client2.getModel());
	  VersionChangeListener versionListener3 =
		  VersionChangeListener.addAlternativeVersionListener(client3.getModel());

	  String c1Text1 = "Good morning!";
	  client1.createGreeting(c1Text1);

	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);

	  assertSharedGreeting(c1Text1, client2, client3);
	  assertEquals(c2Text1, versionListener2.getReplacedOldValue());
	  assertEquals(c3Text1, versionListener3.getReplacedOldValue());

	  client2.getMockingSync().resume();
	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);
	  assertSharedGreeting(c1Text1, network);

	  String c2Text2 = "Hi, everyone and good evening!";
	  client2.getGreeting().set(c2Text2);
	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);

	  assertSharedGreeting(c2Text2, client1, client3);

	  client3.getMockingSync().resume();
	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);
	  assertSharedGreeting(c2Text2, network);
	});
  }

  private void assertSharedGreeting(String text, NetworkMockingRTSyncNode... clients)
  {
	for ( int i = 0; i < clients.length; i++ )
	{
	  assertEquals(text, clients[i].getGreeting().get());
	}
  }

  /**
   * Works similar to {@link #testChangingKnownString() but uses a server node to which the other 2 clients connect.}
   */
  @Test
  void testChangingKnownStringWithNodeRoles()
  {
	RTSyncTestHelper.runMultipleTimes(3, () -> {
	  NetworkMockingRTSyncNode client1 = new NetworkMockingRTSyncSimpleClient("Agate");
	  NetworkMockingRTSyncNode client2 = new NetworkMockingRTSyncSimpleClient("Bruno");
	  NetworkMockingRTSyncNode server = new NetworkMockingRTSyncSimpleServer();
	  NetworkMockingRTSyncNode[] network = {client1, client2, server};
	  NetworkMockingRTSyncNode.listenToNetwork(client1, server);
	  NetworkMockingRTSyncNode.listenToNetwork(client2, server);

	  // Client 2 prepares a shared greeting but doesn't send it yet.
	  String c2Text1 = "Hi, everyone!";
	  client2.suspendAndCreateGreeting(c2Text1);

	  VersionChangeListener versionListener2 =
		  VersionChangeListener.addAlternativeVersionListener(client2.getModel());

	  String c1Text1 = "Good morning!";
	  client1.createGreeting(c1Text1);

	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);
	  assertSharedGreeting(c1Text1, network);
	  assertEquals(c2Text1, versionListener2.getReplacedOldValue());

	  client2.getMockingSync().resume();
	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);
	  assertSharedGreeting(c1Text1, network);

	  String c1Text2 = "Hi, everyone and good evening!";
	  client1.getGreeting().set(c1Text2);
	  NetworkMockingRTSyncNode.waitForIdleNetwork(network);
	  assertSharedGreeting(c1Text2, network);
	});
  }

  /**
   * Works the same as {@link #testChangingKnownStringWithNodeRoles()} but doesn't perform those delays which only serve
   * to ensure assertions before all updates are sent. Thus the clients send their updates concurrently.
   */
  @Test
  void testConcurrentChangingOfStringWithNodeRoles()
  {
	RTSyncTestHelper.runMultipleTimes(3, () -> {
	  NetworkMockingRTSyncNode client1 = new NetworkMockingRTSyncSimpleClient("Agate");
	  NetworkMockingRTSyncNode client2 = new NetworkMockingRTSyncSimpleClient("Bruno");
	  NetworkMockingRTSyncNode server = new NetworkMockingRTSyncSimpleServer();
	  NetworkMockingRTSyncNode.listenToNetwork(client1, server);
	  NetworkMockingRTSyncNode.listenToNetwork(client2, server);

	  VersionChangeListener versionListener1 =
		  VersionChangeListener.addAlternativeVersionListener(client1.getModel());
	  VersionChangeListener versionListener2 =
		  VersionChangeListener.addAlternativeVersionListener(client2.getModel());

	  String c1Text1 = "Good morning!";
	  client1.createGreeting(c1Text1);
	  String c2Text1 = "Hi, everyone!";
	  client2.createGreeting(c2Text1);

	  NetworkMockingRTSyncNode.waitForIdleNetwork(client1, client2, server);
	  assertTrue((client2.getGreeting().get().equals(c1Text1)
		  && c2Text1.equals(versionListener2.getReplacedOldValue()))
		|| (client1.getGreeting().get().equals(c2Text1)
			&& c1Text1.equals(versionListener1.getReplacedOldValue())), "No client has changed their version to the other client's version.");


	  //      Awaitility
	  //      .await()
	  //      .atMost(maxAllowedSyncDuration)
	  //      .pollInterval(pollInterval)
	  //      .until(
	  //        () -> (client2.getGreeting().get().equals(c1Text1)
	  //            && c2Text1.equals(versionListener2.getReplacedOldValue()))
	  //        || (client1.getGreeting().get().equals(c2Text1)
	  //            && c1Text1.equals(versionListener1.getReplacedOldValue())));

	  // Both clients have different ideas about how to merge the greeting.
	  // The one whose version is not the official one performs the change.
	  String mergedText;
	  NetworkMockingRTSyncNode mergingClient;
	  if ( versionListener1.getReplacedOldValue() != null )
	  {
		mergedText = "Good morning and Hi to all!";
		mergingClient = client1;
	  }
	  else
	  {
		mergedText = "Hi, everyone and good evening!";
		mergingClient = client2;
	  }
	  mergingClient.getGreeting().set(mergedText);

	  NetworkMockingRTSyncNode.waitForIdleNetwork(client1, server, client2);
	  assertSharedGreeting(mergedText, client1, server, client2);
	});
  }

  //  This commented out test should be thought through again because it isn't usefull with the current implementation.
  //
  //  /**
  //   * Simulates 3 clients which modify a shared string concurrently with different ideas about what its content should
  //   * be.
  //   */
  //  @Test
  //  void testConcurrentChangingOfStringWithNodeRolesWith3Clients()
  //  {
  //    RTSyncTestHelper.runMultipleTimes(5, () -> {
  //      NetworkMockingRTSyncNode client1 = new NetworkMockingRTSyncClient("Agate");
  //      NetworkMockingRTSyncNode client2 = new NetworkMockingRTSyncClient("Bruno");
  //      NetworkMockingRTSyncNode client3 = new NetworkMockingRTSyncClient("Charlotte");
  //      NetworkMockingRTSyncNode server = new NetworkMockingRTSyncServer();
  //      NetworkMockingRTSyncNode[] network = {client1, client2, client3, server};
  //      NetworkMockingRTSyncNode.listenToNetwork(client1, server);
  //      NetworkMockingRTSyncNode.listenToNetwork(client2, server);
  //      NetworkMockingRTSyncNode.listenToNetwork(client3, server);
  //
  //      String c1Text1 = "Good morning!";
  //      String c2Text1 = "Hi, fellows!";
  //      String c3Text1 = "Hello!";
  //      String c3Text2 = "Hello, team!";
  //      SharedString greeting1 = client1.createGreeting("");
  //      SharedString greeting2 = client2.createGreeting("");
  //      SharedString greeting3 = client3.createGreeting(c3Text1);
  //      // All clients contribute their ideas concurrently. Greeting 3 might change asynchronously later, but we want the
  //      // insertion to be done at a specific index. Therefore, we handle greeting3 first, so it doesn't change in between.
  //      greeting3.set(c3Text1);
  //
  //
  //
  //
  //      // Note, that 'greeting3.insert(c3Text1.length() - 1, ", team");' would cause ", team" to be inserted twice.
  //      // There is no Unit Test for SharedStringImpl.insert and SharedStringImpl.remove.
  //      greeting3.set(c3Text2);
  //      greeting1.set(c1Text1);
  //      greeting2.set(c2Text1);
  //
  //      NetworkMockingRTSyncNode.waitForIdleNetwork(3, network);
  //      assertTrue(greeting1.get().contains(c2Text1) && greeting1.get().contains("Hello"));
  //      assertTrue(greeting2.get().contains(c1Text1) && greeting2.get().contains("Hello"));
  //      assertTrue(greeting3.get().contains(c1Text1) && greeting3.get().contains(c2Text1));
  //
  //      // Client 3 concatenates the greetings in the state they see before the replacement made by clients 1 and 2.
  //      // If this isn't done prior to the replacements, then this would restore the old texts c1Text1 and c2Text1,
  //      // because the server doesn't check if those old texts have been replaced but treats them as new texts instead.
  //      String concatenatedBy3 =
  //          "Hello, team, " + c2Text1.toLowerCase().replaceFirst("!", " and ") + c1Text1.toLowerCase();
  //      greeting3.set(concatenatedBy3);
  //      NetworkMockingRTSyncNode.waitForIdleNetwork(2, network);
  //
  //      // Clients 1 and 2 change each other's text in their own shared string.
  //      String replaced2By1 = greeting1.get().replaceFirst("fellows", "everyone");
  //      String replaced1By2 = greeting2.get().replaceFirst("morning", "evening");
  //      greeting1.set(replaced2By1);
  //      greeting2.set(replaced1By2);
  //
  //      NetworkMockingRTSyncNode.waitForIdleNetwork(2, network);
  //      String expectedResult = "Hello, team, hi, everyone and good evening!";
  //      assertEquals(expectedResult, server.getOrCreateGreeting().get());
  //      assertEquals(expectedResult, greeting1.get());
  //      assertEquals(expectedResult, greeting2.get());
  //      assertEquals(expectedResult, greeting3.get());
  //    });
  //  }

  /**
   * This mocks a network connection by connecting {@link LocalOperationSync}s. All nodes which are of this class and
   * not subclasses are considered to be equal network partners. Subclass this to define client and server nodes.
   *
   * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
   * @version $Rev$
   *
   */
  static class NetworkMockingRTSyncNode extends RTSyncSimpleNetworkNode
  {
	private static final Logger LOG = LoggerFactory.getLogger(NetworkMockingRTSyncNode.class);

	protected String            _name;

	NetworkMockingRTSyncNode(String username)
	{
	  this(username, NetworkMockingOperationSync::new);
	}

	NetworkMockingRTSyncNode(String username, OperationSyncCreator opCreator)
	{
	  super(opCreator);
	  _name = username;
	  _editor
	  .addListener(
		op -> LOG.info("{}'s greeting is: {}", username, getGreeting() != null ? getGreeting().get() : "null"));
	  ((NetworkMockingOperationSync) _sync).setNode(this);
	}

	@Override
	protected boolean isServer()
	{
	  return true;
	}

	public static void listenToNetwork(NetworkMockingRTSyncNode... networkNodes)
	{
	  for ( int i = 0; i < networkNodes.length; i++ )
	  {
		for ( int j = 0; j < networkNodes.length; j++ )
		{
		  if ( i != j )
		  {
			networkNodes[i].listenTo(networkNodes[j]);
		  }
		}
	  }
	}

	/**
	 * This method waits at most maxAllowedSyncDuration for the network to handle all receives and sends.
	 *
	 * @param networkNodes A group of nodes that form the network.
	 */
	public static void waitForIdleNetwork(NetworkMockingRTSyncNode... networkNodes)
	{
	  List<NetworkMockingOperationSync> syncs =
		  Arrays.asList(networkNodes).stream().map(n -> n.getMockingSync()).toList();
	  Callable<Boolean> condition = () -> syncs.stream().allMatch(s -> s.isIdle());

	  Awaitility
	  .await()
	  .pollDelay(pollDelay)
	  .atMost(maxAllowedSyncDuration)
	  .pollInterval(pollInterval)
	  .until(condition);
	}

	public void waitForGreeting(String expected)
	{
	  Callable<Boolean> condition = () -> expected.equals(getGreeting() != null ? getGreeting().get() : null);
	  Awaitility
	  .await()
	  .pollDelay(pollDelay)
	  .atMost(maxAllowedSyncDuration)
	  .pollInterval(pollInterval)
	  .until(condition);
	}

	/**
	 * This method waits at most maxAllowedSendDuration for the client's greeting to become one of the two expected
	 * values. Those two expected values are the two possible concatenations of part1 and part2.
	 */
	void waitFor1Of2SharedStringAlternatives(String part1, String part2)
	{
	  Awaitility
	  .await()
	  .atMost(maxAllowedSyncDuration)
	  .pollInterval(pollInterval)
	  .until(() -> RTSyncTestHelper.sharedStringIs(getGreeting(), part1, part2));
	}

	public NetworkMockingOperationSync getMockingSync()
	{
	  return (NetworkMockingOperationSync) super.getSync();
	}

	public void listenTo(NetworkMockingRTSyncNode other)
	{
	  getMockingSync().listenTo(other);
	}

	public SharedString suspendAndCreateGreeting(String newText)
	{
	  getMockingSync().suspend();
	  return createGreeting(newText);
	}

	public SharedString getGreeting()
	{
	  return _model.get(GREETING_ID);
	}

	public SharedString createGreeting(String text)
	{
	  // The commented out line below would not yield the same result because the ID may be transmitted after the receiver
	  // has created their own version of the string.
	  //      return _model.get(GREETING_ID, () -> _model.newString());

	  //      return (SharedString) getModel().getObject(GREETING_ID, "string");

	  SharedString newString = _model.newString();
	  newString.set(text);
	  _model.set(GREETING_ID, newString);
	  return newString;
	}

	static class NetworkMockingOperationSync extends AbstractOperationSync
	{
	  protected static final Logger                                            LOG        =
		  LoggerFactory.getLogger(NetworkMockingOperationSync.class);

	  private final Thread                                                     _triggerThread;
	  private final Thread                                                     _receiveThread;
	  private final BlockingQueue<BasicMessage>					_messageQueue;
	  private final BlockingQueue<TaggedUserOperation>                                  _inQueue;
	  private final Random                                                     _random    = new Random();
	  protected final Collection<Consumer<TaggedUserOperation>> _networkNodes =
		  new ArrayList<>();

	  /**
	   * Connects a network node. Network nodes only listen to those updates which are supposed to be broadcasted over
	   * the network after the operation has been handled internally.
	   */
	  public void connectNetworkNode(Consumer<TaggedUserOperation> networkNode)
	  {
		_networkNodes.add(networkNode);
	  }

	  private boolean                                                          _sending   = false;
	  private boolean                                                          _receiving = false;
	  private List<TaggedUserOperation> _suspended;
	  private NetworkMockingRTSyncNode	_node;

	  NetworkMockingOperationSync(CustomEditorControl<Operation<CombinedHandler>> control)
	  {
		super(control);
		_inQueue = new LinkedBlockingQueue<>();
		_messageQueue = new LinkedBlockingQueue<>();
		_receiveThread =
			new Thread(this::mockReceivingFromNetwork, "network-mocking-operation-sync-receive-thread");
		_triggerThread = new Thread(this::mockSendingOverNetwork, "network-mocking-operation-sync-send-thread");
	  }

	  public void setNode(NetworkMockingRTSyncNode node)
	  {
		assertNull(_node);
		_node = node;
		_receiveThread.start();
		_triggerThread.start();
	  }

	  public String getName()
	  {
		return _node._name;
	  }

	  @Override
	  public void send(TaggedOperation<Operation<CombinedHandler>> op)
	  {
		TaggedUserOperation userOp = new TaggedUserOperation(op, getName());
		try (CloseableLock lock = _control.lock())
		{
		  if ( _suspended != null )
		  {
			_suspended.add(userOp);
			return;
		  }

		  _messageQueue.add(new BasicMessage(EndpointPaths.APP_SEND_OPERATION, userOp));
		}
	  }

	  public void listenTo(NetworkMockingRTSyncNode other)
	  {
		NetworkMockingOperationSync otherSync = other.getMockingSync();
		otherSync.connectNetworkNode(new Connection(this, _inQueue::add));
	  }

	  /**
	   * Suspend sending of events.
	   */
	  public void suspend()
	  {
		if ( _suspended != null )
		{
		  return;
		}

		_suspended = new ArrayList<>();
	  }

	  public void resume()
	  {
		List<TaggedUserOperation> suspended = _suspended;
		_suspended = null;
		suspended.stream().forEach(o -> _messageQueue.add(new BasicMessage("", o)));
	  }

	  private void mockReceivingFromNetwork()
	  {
		while (!Thread.interrupted())
		{
		  try
		  {
			TaggedUserOperation receivedOperation = _inQueue.take();
			_receiving = true;
			long sleepTime = _random.nextLong(DELAY_MIN, DELAY_MAX);
			Thread.sleep(sleepTime);
			onTaggedOperationReceived(receivedOperation, false);
			_receiving = !_inQueue.isEmpty();
		  }
		  catch (InterruptedException e)
		  {
			return;
		  }
		  catch (Exception e)
		  {
			LOG.error(e.getLocalizedMessage(), e);
		  }
		}
	  }

	  public void applyAndSendTaggedOperationToNetwork(TaggedUserOperation taggedOperation)
	  {
		TaggedUserOperation storedOp = storeTaggedOperation(taggedOperation, false);
		LOG.debug("{} sending {} stored as {}.", getName(), taggedOperation, storedOp);
		notifyListeners(storedOp, false);
		_networkNodes.forEach(n -> n.accept(taggedOperation));
	  }

	  private void mockSendingOverNetwork()
	  {
		while (!Thread.interrupted())
		{
		  try
		  {
			BasicMessage outMessage = _messageQueue.take();
			_sending = true;
			TaggedUserOperation taggedOp = (TaggedUserOperation) outMessage.payload();
			applyAndSendTaggedOperationToNetwork(taggedOp);
			_sending = false;
		  }
		  catch (InterruptedException e)
		  {
			return;
		  }
		  catch (Exception e)
		  {
			LOG.error(e.getLocalizedMessage(), e);
		  }
		}
	  }

	  public boolean isIdle()
	  {
		return _messageQueue.isEmpty() && !_sending && _inQueue.isEmpty() && !_receiving;
	  }

	  static record Connection(
		NetworkMockingOperationSync listener,
		Consumer<TaggedUserOperation> onOperationReceived)
	  implements
	  Consumer<TaggedUserOperation>
	  {
		@Override
		public void accept(TaggedUserOperation t)
		{
		  onOperationReceived.accept(t);
		}
	  }
	}
  }

  static class NetworkMockingRTSyncSimpleServer extends NetworkMockingRTSyncNode
  {
	NetworkMockingRTSyncSimpleServer()
	{
	  super("Server", ServerMockingOperationSync::new);
	}

	static class ServerMockingOperationSync extends NetworkMockingOperationSync
	{
	  ServerMockingOperationSync(CustomEditorControl<Operation<CombinedHandler>> control)
	  {
		super(control);
	  }

	  /**
	   * This stores the given {@link TaggedOperation}, notifies local listeners and then the network nodes of the store
	   * result.
	   *
	   * @implNote If the given operation is a {@link TaggedUserOperation}, then the local listeners and network nodes
	   *           are notified of a {@link TaggedUserOperation} with the user information.
	   */
	  @Override
	  public void onTaggedOperationReceived(TaggedUserOperation taggedOp, boolean wholeState)
	  {
		try (CloseableLock lock = _control.lock())
		{
		  TaggedUserOperation storedOp = storeTaggedOperation(taggedOp, wholeState);
		  notifyListeners(storedOp, false);
		  _networkNodes.forEach(n -> n.accept(TaggedUserOperation.toTaggedUserOperation(storedOp)));
		}
	  }
	}
  }

  static class NetworkMockingRTSyncSimpleClient extends NetworkMockingRTSyncNode
  {
	NetworkMockingRTSyncSimpleClient(String username)
	{
	  super(username, ClientMockingOperationSync::new);
	}

	@Override
	protected boolean isServer()
	{
	  return false;
	}

	static class ClientMockingOperationSync extends NetworkMockingOperationSync
	{
	  ClientMockingOperationSync(CustomEditorControl<Operation<CombinedHandler>> control)
	  {
		super(control);
	  }

	  @Override
	  public void applyAndSendTaggedOperationToNetwork(TaggedUserOperation taggedOperation)
	  {
		try (CloseableLock lock = _control.lock())
		{
		  LOG.debug("{} applying {}", getName(), taggedOperation);
		  //          storeTaggedOperation(taggedOperation);
		  //          notifyListeners(storedOp);
		  // The tagged operation is sent with its old historyId. Only the server increases the historyId.
		  _networkNodes.forEach(n -> n.accept(taggedOperation));
		}
	  }
	}
  }
}
