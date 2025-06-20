package de.dmos.rtsync.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import de.dmos.rtsync.client.RTProjectClientOperationSync;
import de.dmos.rtsync.client.RTSyncProjectClient;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.project.RTProjectData;
import de.dmos.rtsync.server.project.RTSyncProjectServer;
import de.dmos.rtsync.server.project.RTSyncProjectServerController;
import de.dmos.rtsync.util.SharedObjectHelper;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedString;

/**
 * Performs synchronization tests where updates to the shared model are only expected to be sucessful, if the sender is
 * online during the update.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RTSyncProjectServer.class)
class OnlineSynchronizationOFProjectModelsIT extends AbstractSynchronizationIT
{
  private static final Logger LOG = LoggerFactory.getLogger(OnlineSynchronizationOFProjectModelsIT.class);

  RTSyncProjectServerController	_server;
  RTSyncProjectClient			_client1;
  RTSyncProjectClient			_client2;
  RTProjectClientOperationSync	_client1Sync;
  RTProjectClientOperationSync	_client2Sync;

  @BeforeEach
  void setupServerAnd2Clients(
	@Autowired ConfigurableApplicationContext serverContext,
	@Autowired RTSyncProjectServerController server)
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
	_client1 = new RTSyncProjectClient("Client 1", Color.GREEN);
	_client2 = new RTSyncProjectClient("Client 2", Color.BLUE);
	_client1Sync = _client1.getSync();
	_client2Sync = _client2.getSync();
	_connectionHandler1 = _client1Sync.getConnectionHandler();
	_connectionHandler2 = _client2Sync.getConnectionHandler();
	_connectionListener1 = new ProjectClientConnectionListener(_client1Sync);
	_connectionListener2 = new ProjectClientConnectionListener(_client2Sync);
	Awaitility.await().atMost(maxServerStartupDuration).until(() -> _serverContext.isRunning());
  }

  @AfterEach
  void shutdown2ClientSyncs()
  {
	_client1Sync.close();
	_client2Sync.close();
  }

  @DirtiesContext
  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void synchronizeSharedString()
  {
	_connectionHandler1.startSynchronizingWithServer(_uri);
	_connectionHandler2.startSynchronizingWithServer(_uri);
	String project = "project A";
	RTProjectData pData1 = _client1Sync.getOrCreateRTProjectData(project);
	RTProjectData pData2 = _client2Sync.getOrCreateRTProjectData(project);

	String sharedStringID = "shared string 1";
	String c1Message1 = "C1: Hi, I am client one!\n";
	String c2Message1 = "C2: Hi, I am client two.\n";
	Model model1 = pData1.getModel();
	Model model2 = pData2.getModel();

	waitUntilBothClientsAreReadyToSynchronize();

	SharedString ss1 = model1.newString();
	ss1.set(c1Message1);
	model1.set(sharedStringID, ss1);

	waitUntilClientModelObjectsEqual(project);
	assertNotNull(_connectionHandler1.getSelfSubscriber());
	assertNotNull(_connectionHandler2.getSelfSubscriber());

	SharedString ss2 = model2.get(sharedStringID);
	ss2.append(c2Message1);

	waitUntilClientModelObjectsEqual(project);

	String sharedStringState = ss1.get();
	String c1Replacement = "I am client one.\n";
	String c2Replacement = "I am client 2.\n";
	String c1Message2 = sharedStringState.replaceFirst(c1Message1, c1Replacement);
	String c2Message2 = sharedStringState.replaceFirst(c2Message1, c2Replacement);

	try (CloseableLock lock = pData2.getControl().lock())
	{
	  ss1.set(c1Message2);
	  waitForReceivedVersion(_connectionHandler1, pData1.getControl(), 5);
	  ss2.set(c2Message2);
	}

	String expectedNewSharedStringState = c1Message2.replaceFirst(c2Message1, c2Replacement);
	waitUntilClientModelObjectsEqual(project);
	assertEquals(expectedNewSharedStringState, ((SharedString) model1.get(sharedStringID)).get());
  }

  @DirtiesContext
  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void testAutoClose()
  {
	_server.setAutoCloseProjects(true);
	_connectionHandler1.startSynchronizingWithServer(_uri);
	_connectionHandler2.startSynchronizingWithServer(_uri);
	String projectA = "project A";
	String projectB = "project B";
	String projectC = "project C";
	_client1Sync.getOrCreateRTProjectData(projectA);
	_client2Sync.getOrCreateRTProjectData(projectB);
	waitUntilServerProjectsEqual(Set.of(projectA, projectB));
	_client1Sync.closeProject(projectA);
	waitUntilServerProjectsEqual(Set.of(projectB));
	_client1Sync.getOrCreateRTProjectData(projectB);
	_client1Sync.getOrCreateRTProjectData(projectC);
	waitUntilServerProjectsEqual(Set.of(projectB, projectC));
	_client2Sync.getOrCreateRTProjectData(projectC);
	_client2Sync.closeProject(projectB);
	waitUntilServerProjectsEqual(Set.of(projectB, projectC), Duration.ofMillis(200));
	_client1Sync.getOrCreateRTProjectData(projectA);
	waitUntilServerProjectsEqual(Set.of(projectA, projectB, projectC));
	_connectionHandler1.stopSynchronizingWithServer();
	waitUntilServerProjectsEqual(Set.of(projectC));
	_connectionHandler2.stopSynchronizingWithServer();
	waitUntilServerProjectsEqual(Set.of());
  }

  /**
   * Waits until the two client models' objects with the given id are equal.
   */
  protected void waitUntilClientModelObjectsEqual(String project, Collection<String> ids)
  {
	Model m1 = _client1Sync.getOrCreateRTProjectData(project).getModel();
	Model m2 = _client1Sync.getOrCreateRTProjectData(project).getModel();
	waitForSync(
	  "synchronization of both client's models",
	  () -> ids.stream().allMatch(id -> SharedObjectHelper.sharedObjectEquals(m1.get(id), m2.get(id))),
	  maxLocalSyncDuration);
  }

  /**
   * Waits until the two client models' objects with the given id are equal.
   */
  protected void waitUntilServerProjectsEqual(Set<String> expectedProjects)
  {
	waitForSync(
	  "server to open/close projects so that exactly {} are open",
	  () -> expectedProjects.equals(new HashSet<>(Arrays.asList(_server.getProjectNames()))),
	  maxLocalSyncDuration);
  }

  protected void waitUntilServerProjectsEqual(Set<String> expectedProjects, Duration delay)
  {
	LOG
	.debug(
	  "Awaiting at least {} for the server to open/close projects so that exactly {} are open.",
	  delay,
	  expectedProjects);
	Awaitility
	.await()
	.atMost(maxLocalSyncDuration)
	.pollDelay(delay)
	.pollInterval(pollInterval)
	.until(() -> expectedProjects.equals(new HashSet<>(Arrays.asList(_server.getProjectNames()))));
  }

  /**
   * Waits until the two client models' objects are equal
   */
  protected void waitUntilClientModelObjectsEqual(String project)
  {
	Model m1 = _client1Sync.getOrCreateRTProjectData(project).getModel();
	Model m2 = _client2Sync.getOrCreateRTProjectData(project).getModel();
	waitForSync(
	  "synchronization of both client's models",
	  () -> SharedObjectHelper.getDifferences(m1, m2),
	  Collection::isEmpty,
	  getModelDifferenceListener(m1, m2),
	  maxLocalSyncDuration);
  }

  class ProjectClientConnectionListener extends ClientConnectionListener
  {
	RTProjectClientOperationSync _sync;

	ProjectClientConnectionListener(RTProjectClientOperationSync sync)
	{
	  super(sync.getConnectionHandler());
	  _sync = sync;
	}
  }
}
