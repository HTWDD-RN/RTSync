package de.dmos.rtsync.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import de.dmos.rtsync.server.simple.RTSyncSimpleServer;
import de.dmos.rtsync.util.SharedObjectHelper;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedList;
import se.l4.otter.model.SharedMap;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RTSyncSimpleServer.class)
class OnlineSynchronizationOFSimpleModelsIT extends AbstractSynchronizationOFSimpleModelsIT
{
  private static final Logger LOG = LoggerFactory.getLogger(OnlineSynchronizationOFSimpleModelsIT.class);

  @DirtiesContext
  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void synchronizeSharedString()
  {
	_server.setRejectOldHistoryIds(false);
	_connectionHandler1.startSynchronizingWithServer(_uri);
	_connectionHandler2.startSynchronizingWithServer(_uri);
	String sharedStringID = "shared string 1";
	String c1Message1 = "C1: Hi, I am client one!\n";
	String c2Message1 = "C2: Hi, I am client two.\n";
	Model model1 = _client1.getModel();
	Model model2 = _client2.getModel();

	_client1.getEditor().addListener(chg -> LOG.info("changed object: {}", _client1.changeEventToString(chg)));
	_client2.getEditor().addListener(chg -> LOG.info("changed object: {}", _client2.changeEventToString(chg)));

	waitUntilBothClientsAreReadyToSynchronize();

	SharedString ss1 = model1.newString();
	ss1.set(c1Message1);
	model1.set(sharedStringID, ss1);

	waitUntilClientModelObjectsEqual();
	assertNotNull(_connectionHandler1.getSelfSubscriber());
	assertNotNull(_connectionHandler2.getSelfSubscriber());

	SharedString ss2 = model2.get(sharedStringID);
	ss2.append(c2Message1);

	waitUntilClientModelObjectsEqual();

	String sharedStringState = ss1.get();
	String c1Replacement = "I am client one.\n";
	String c2Replacement = "I am client 2.\n";
	String c1Message2 = sharedStringState.replaceFirst(c1Message1, c1Replacement);
	String c2Message2 = sharedStringState.replaceFirst(c2Message1, c2Replacement);

	// The lock ensures, that client 2's shared string is changed locally after client 1's string but before the remote change of client 1 takes effect on client 2.
	// This way we provoke concurrency. Another effect is that this prevents client 2 from overwriting client 1's change.
	try (CloseableLock lock = _client2.getControl().lock())
	{
	  ss1.set(c1Message2);
	  waitForReceivedVersion(_client1, 5);
	  ss2.set(c2Message2);
	}

	String expectedNewSharedStringState = c1Message2.replaceFirst(c2Message1, c2Replacement);
	waitUntilClientModelObjectsEqual();
	assertEquals(expectedNewSharedStringState, ((SharedString) model1.get(sharedStringID)).get());
  }

  @DirtiesContext
  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void synchronizeMixedModel()
  {
	_server.setRejectOldHistoryIds(false);
	_connectionHandler1.startSynchronizingWithServer(_uri);
	_connectionHandler2.startSynchronizingWithServer(_uri);
	Model model1 = _client1.getModel();
	Model model2 = _client2.getModel();

	// SharedObject data of client 1.
	String listId = "list1";
	boolean boo = true;
	byte b = 50;
	short s = 2500;
	int i = 100;
	long l = 66600000022l;
	float f = -21.1f;
	double d = 5236346226.12314543643;
	List<Object> numbers = List.of(b, s, i, l, f, d, boo);
	List<String> subListStrings = List.of("sl1s1", "sl1s2");

	// SharedObject data of client 2.
	String mapId = "map";
	String subMapId = "subobject";
	Map<String, Object> subMapData = Map.of("c1", "z", "c2", "y");

	waitUntilBothClientsAreReadyToSynchronize();

	// SharedObjects of client 1.
	SharedList<String> subList1 = model1.newList();
	List<Object> list1Contents = List.of("l1s1", "l1s2", "l1s3", 1, 1.23, subList1, numbers);
	SharedList<Object> sharedList1 = model1.newList();
	model1.set(listId, sharedList1);
	sharedList1.addAll(list1Contents);
	subList1.addAll(subListStrings);

	// SharedObjects of client 2.
	SharedMap subMap = model2.newMap();
	Map<String, Object> mapData = Map.of("temp", 22.3, "state", "good", subMapId, subMap);
	SharedMap sharedMap2 = model2.newMap();
	model2.set(mapId, sharedMap2);
	mapData.forEach(sharedMap2::set);
	subMapData.forEach(subMap::set);

	waitUntilClientModelObjectsEqual();

	SharedList<Object> list2 = model2.get(listId);
	assertTrue(SharedObjectHelper.sharedListEquals(list2, list1Contents));
	SharedMap map1 = model1.get(mapId);
	assertTrue(SharedObjectHelper.sharedMapEquals(map1, mapData));

	SharedMap subMap1 = map1.get(subMapId);
	assertTrue(SharedObjectHelper.sharedMapEquals(subMap1, subMapData));
  }

  @DirtiesContext
  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void adaptToOnlineModelAfterReconnectWithListInModel()
  {
	_server.setRejectOldHistoryIds(true);
	_connectionHandler1.startSynchronizingWithServer(_uri);
	_connectionHandler2.startSynchronizingWithServer(_uri);
	Model model1 = _client1.getModel();
	Model model2 = _client2.getModel();

	String listId = "main list";
	List<String> listContents1 = List.of("entry 1", "entry 2", "entry 3", "entry 4");

	waitUntilBothClientsAreReadyToSynchronize();

	SharedList<String> list1 = model1.newList();
	list1.insertAll(0, listContents1);
	model1.set(listId, list1);

	waitUntilClientModelObjectsEqual();

	assertTrue(SharedObjectHelper.sharedListEquals(model2.get(listId), listContents1));
	_connectionHandler2.stopSynchronizingWithServer();

	list1.removeRange(1, 3);
	list1.add("new entry at the end");
	List<String> expectedResult = List.of("entry 1", "entry 4", "new entry at the end");

	SharedList<String> list2 = model2.get(listId);
	list2.add("client2's appended entry");
	list2.removeRange(0, 3);

	Awaitility
	.await()
	.atMost(maxLocalSyncDuration)
	.pollInterval(pollInterval)
	.until(() -> _client1.getControl().getLatestVersion() == 5);

	_connectionHandler2.startSynchronizingWithServer(_uri);
	waitUntilBothClientsAreReadyToSynchronize();
	waitUntilClientModelObjectsEqual();
	assertTrue(SharedObjectHelper.sharedListEquals(model2.get(listId), expectedResult));
  }

  @DirtiesContext
  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void adaptToOnlineModelAfterReconnectWithStringInModel()
  {
	_server.setRejectOldHistoryIds(true);
	_connectionHandler1.startSynchronizingWithServer(_uri);
	_connectionHandler2.startSynchronizingWithServer(_uri);
	Model model1 = _client1.getModel();
	Model model2 = _client2.getModel();

	String stringId = "s1";
	String content1 = "Hello World of Synchronization!";

	waitUntilBothClientsAreReadyToSynchronize();

	SharedString ss1 = model1.newString();
	ss1.set(content1);
	model1.set(stringId, ss1);

	waitUntilClientModelObjectsEqual();

	SharedString ss2 = model2.get(stringId);
	assertEquals(content1, ss2.get());
	_connectionHandler2.stopSynchronizingWithServer();

	String newContent1 = "Hi!";
	ss1.set(newContent1);
	String newContent2 = "abc";
	ss2.remove(0, 2);
	ss2.append(newContent2);

	waitForReceivedVersion(_client1, 4);

	_connectionHandler2.startSynchronizingWithServer(_uri);
	waitUntilBothClientsAreReadyToSynchronize();
	waitUntilClientModelObjectsEqual();
	assertEquals(newContent1, ((SharedString) model2.get(stringId)).get());
	assertEquals(newContent1, ((SharedString) model1.get(stringId)).get());
  }
}
