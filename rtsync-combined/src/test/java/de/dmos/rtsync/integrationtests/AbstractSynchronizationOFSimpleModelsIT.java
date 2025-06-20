package de.dmos.rtsync.integrationtests;

import java.util.Collection;
import java.util.List;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import de.dmos.rtsync.client.RTSyncSimpleClient;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.server.simple.RTSyncSimpleServerController;
import de.dmos.rtsync.test.VersionChangeListener;
import de.dmos.rtsync.util.SharedObjectHelper;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedString;

abstract class AbstractSynchronizationOFSimpleModelsIT extends AbstractSynchronizationIT
{
  private static final Logger  LOG = LoggerFactory.getLogger(AbstractSynchronizationOFSimpleModelsIT.class);

  RTSyncSimpleServerController	 _server;
  RTSyncSimpleClient			 _client1;
  RTSyncSimpleClient			 _client2;

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
	_connectionListener1 = new SimpleClientConnectionListener(_client1);
	_connectionListener2 = new SimpleClientConnectionListener(_client2);
	Awaitility.await().atMost(maxServerStartupDuration).until(() -> _serverContext.isRunning());
  }

  @AfterEach
  public void shutdown2ClientSyncs()
  {
	_client1.getSync().close();
	_client2.getSync().close();
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

  protected void waitForReceivedVersion(RTSyncSimpleClient client, long version)
  {
	waitForReceivedVersion(client.getConnectionHandler(), client.getControl(), version);
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

  class SimpleClientConnectionListener extends ClientConnectionListener implements SubscriberListener
  {
	SimpleClientConnectionListener(RTSyncSimpleClient client) {
	  super(client.getConnectionHandler());
	  client.getSubscriptionContainer().addWeakSubscriberListener(this);
	  client.getSubscriptionContainer().addWeakIncompatibleModelListener(this);
	}

	private List<Subscriber> _currentSubscribers = null;

	public List<Subscriber> getCurrentSubscribers()
	{
	  return _currentSubscribers;
	}

	@Override
	public void onSubscribersReceived(List<Subscriber> subscribers)
	{
	  _currentSubscribers = subscribers;
	}
  }
}
