//package de.dmos.rtsync.integrationtests;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import java.io.IOException;
//import java.util.List;
//import java.util.Map;
//
//import org.awaitility.Awaitility;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.context.ConfigurableApplicationContext;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.annotation.DirtiesContext.MethodMode;
//
//import de.dmos.rtsync.server.simple.RTSyncSimpleServer;
//import de.dmos.rtsync.server.simple.RTSyncSimpleServerController;
//import de.dmos.rtsync.util.SharedObjectHelper;
//import se.l4.otter.lock.CloseableLock;
//import se.l4.otter.model.Model;
//import se.l4.otter.model.SharedList;
//import se.l4.otter.model.SharedMap;
//import se.l4.otter.model.SharedString;
//
//@DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RTSyncSimpleServer.class)
//class SynchronizationOFSimpleModelsWithMixedConnectionStatesIT extends AbstractSynchronizationOFSimpleModelsIT
//{
//  private static final Logger    LOG                      = LoggerFactory.getLogger(SynchronizationOFSimpleModelsWithMixedConnectionStatesIT.class);
//
//  @Override
//  @BeforeEach
//  public void setupServerAnd2Clients(
//	@Autowired ConfigurableApplicationContext serverContext,
//	@Autowired RTSyncSimpleServerController server)
//  {
//	super.setupServerAnd2Clients(serverContext, server);
//  }
//
//  @Override
//  @AfterEach
//  public void shutdown2ClientSyncs()
//  {
//	super.shutdown2ClientSyncs();
//  }
//
//  @DirtiesContext
//  @Test
//  void synchronizeSharedString() throws IOException, InterruptedException
//  {
//	_server.setRejectOldHistoryIds(false);
//	_client1.startSynchronizingWithServer(_uri);
//	_client2.startSynchronizingWithServer(_uri);
//	String sharedStringID = "shared string 1";
//	//    Set<String> ids = Set.of(sharedStringID);
//	String c1Message1 = "C1: Hi, I am client one!\n";
//	String c2Message1 = "C2: Hi, I am client two.\n";
//	Model model1 = _client1.getModel();
//	Model model2 = _client2.getModel();
//
//	//    VersionChangeListener changeListener1 = VersionChangeListener.addAlternativeVersionListener(model1);
//	//    VersionChangeListener changeListener2 = VersionChangeListener.addAlternativeVersionListener(model2);
//
//	_client1.getEditor().addListener(chg -> LOG.info("changed object: {}", _client1.changeEventToString(chg)));
//	_client2.getEditor().addListener(chg -> LOG.info("changed object: {}", _client2.changeEventToString(chg)));
//
//	SharedString oldSs1 = model1.newString();
//	oldSs1.set(c1Message1);
//	model1.set(sharedStringID, oldSs1);
//
//	waitUntilBothClientsAreReadyToSynchronize();
//	waitUntilClientModelObjectsEqual();
//	//    waitUntilMatchingSharedStringVersionReceived(sharedStringID, changeListener1, changeListener2);
//
//	SharedString ss2 = model2.get(sharedStringID);
//	ss2.append(c2Message1);
//
//	waitUntilClientModelObjectsEqual();
//	assertNotNull(_client1.getSelfSubscriber());
//	assertNotNull(_client2.getSelfSubscriber());
//
//	SharedString newSs1 = model1.get(sharedStringID);
//	String sharedStringState = newSs1.get();
//	String c1Replacement = "I am client one.\n";
//	String c2Replacement = "I am client 2.\n";
//	String c1Message2 = sharedStringState.replaceFirst(c1Message1, c1Replacement);
//	String c2Message2 = sharedStringState.replaceFirst(c2Message1, c2Replacement);
//
//	// The lock ensures, that client 2's shared string is changed locally after client 1's string but before the remote change of client 1 takes effect on client 2.
//	// This way we provoke concurrency. Another effect is that this prevents client 2 from overwriting client 1's change.
//	try (CloseableLock lock = _client2.getControl().lock())
//	{
//	  newSs1.set(c1Message2);
//	  waitForReceivedVersion(_client1, 5);
//	  ss2.set(c2Message2);
//	}
//
//	String expectedNewSharedStringState = c1Message2.replaceFirst(c2Message1, c2Replacement);
//	waitUntilClientModelObjectsEqual();
//	assertEquals(expectedNewSharedStringState, ((SharedString) model1.get(sharedStringID)).get());
//  }
//
//  @DirtiesContext
//  @Test
//  void synchronizeMixedModel() throws IOException, InterruptedException
//  {
//	_server.setRejectOldHistoryIds(false);
//	_client1.startSynchronizingWithServer(_uri);
//	_client2.startSynchronizingWithServer(_uri);
//	Model model1 = _client1.getModel();
//	Model model2 = _client2.getModel();
//
//	// SharedObjects of client 1.
//	String listId = "list1";
//	SharedList<String> subList1 = model1.newList();
//	// tuples like 'new Tuple(232.112, "_a.")' are not supported. See StreamingOutput.writeDynamic for supported types.
//	// There are conversion issues between sending and reading:
//	//   int -> long
//	//   float -> double  (The default JsonInput turns it into a long! Therefore, we use CustomJsonInput.)
//	byte b = 50;
//	short s = 2500;
//	int i = 100;
//	long l = 66600000022l;
//	float f = -21.1f;
//	double d = 5236346226.12314543643;
//	List<Object> numbers = List.of(b, s, i, l, f, d);
//	List<Object> list1Contents = List.of("l1s1", "l1s2", "l1s3", 1, 1.23, subList1, numbers);
//	List<String> subListStrings = List.of("sl1s1", "sl1s2");
//	SharedList<Object> sharedList1 = model1.newList();
//	model1.set(listId, sharedList1);
//	sharedList1.addAll(list1Contents);
//	subList1.addAll(subListStrings);
//
//	// SharedObjects of client 2.
//	String mapId = "map";
//	SharedMap subMap = model2.newMap();
//	String subMapId = "subobject";
//	Map<String, Object> mapData = Map.of("temp", 22.3, "state", "good", subMapId, subMap);
//	SharedMap sharedMap2 = model2.newMap();
//	model2.set(mapId, sharedMap2);
//	mapData.forEach((k, v) -> sharedMap2.set(k, v));
//	Map<String, Object> subMapData = Map.of("c1", "z", "c2", "y");
//	subMapData.forEach((k, v) -> subMap.set(k, v));
//
//	waitUntilBothClientsAreReadyToSynchronize();
//	waitUntilClientModelObjectsEqual();
//
//	SharedList<Object> list2 = model2.get(listId);
//	assertTrue(SharedObjectHelper.sharedListEquals(list2, list1Contents));
//	SharedMap map1 = model1.get(mapId);
//	assertTrue(SharedObjectHelper.sharedMapIncludesAll(map1, mapData));
//
//	// Also compare the sub map
//	SharedMap subMap1 = map1.get(subMapId);
//	assertTrue(SharedObjectHelper.sharedMapIncludesAll(subMap1, subMapData));
//  }
//
//  @DirtiesContext
//  @Test
//  void resetAfterIncompatibleListInModel() throws IOException, InterruptedException
//  {
//	_server.setRejectOldHistoryIds(true);
//	_client1.startSynchronizingWithServer(_uri);
//	_client2.startSynchronizingWithServer(_uri);
//	Model model1 = _client1.getModel();
//	Model model2 = _client2.getModel();
//
//	String listId = "main list";
//	List<String> listContents1 = List.of("entry 1", "entry 2", "entry 3", "entry 4");
//
//	SharedList<String> list1 = model1.newList();
//	list1.insertAll(0, listContents1);
//	model1.set(listId, list1);
//
//	waitUntilBothClientsAreReadyToSynchronize();
//	waitUntilClientModelObjectsEqual();
//
//	assertTrue(SharedObjectHelper.sharedListEquals(model2.get(listId), listContents1));
//	_client2.stopSynchronizingWithServer();
//
//	list1.removeRange(1, 3);
//	list1.add("new entry at the end");
//	List<String> expectedResult = List.of("entry 1", "entry 4", "new entry at the end");
//
//	SharedList<String> list2 = model2.get(listId);
//	list2.add("client2's appended entry");
//	list2.removeRange(0, 3);
//
//	Awaitility
//	.await()
//	.atMost(maxLocalSyncDuration)
//	.pollInterval(localSyncPollInterval)
//	.until(() -> _client1.getControl().getLatestVersion() == 5);
//	_client2.startSynchronizingWithServer(_uri);
//	waitUntilBothClientsAreReadyToSynchronize();
//	waitUntilIncompatibleOperationReceive(_connectionHandler2);
//	waitUntilClientModelObjectsEqual();
//	assertTrue(SharedObjectHelper.sharedListEquals(model2.get(listId), expectedResult));
//
//  }
//
//  @DirtiesContext
//  @Test
//  void resetAfterIncompatibleStringInModel() throws IOException, InterruptedException
//  {
//	_server.setRejectOldHistoryIds(true);
//	_client1.startSynchronizingWithServer(_uri);
//	_client2.startSynchronizingWithServer(_uri);
//	Model model1 = _client1.getModel();
//	Model model2 = _client2.getModel();
//
//	String stringId = "s1";
//	String content1 = "Hello World of Synchronization!";
//
//	SharedString ss1 = model1.newString();
//	ss1.set(content1);
//	model1.set(stringId, ss1);
//
//	waitUntilBothClientsAreReadyToSynchronize();
//	waitUntilClientModelObjectsEqual();
//
//	SharedString ss2 = model2.get(stringId);
//	assertEquals(content1, ss2.get());
//	_client2.stopSynchronizingWithServer();
//
//	String newContent1 = "Hi!";
//	ss1.set(newContent1);
//	String newContent2 = "abc";
//	ss2.set(newContent2);
//
//	waitForReceivedVersion(_client1, 4);
//
//	_client2.startSynchronizingWithServer(_uri);
//	waitUntilBothClientsAreReadyToSynchronize();
//	waitUntilIncompatibleOperationReceive(_connectionHandler2);
//
//	waitUntilClientModelObjectsEqual();
//	assertEquals(newContent1, ((SharedString) model2.get(stringId)).get());
//	assertEquals(newContent1, ((SharedString) model1.get(stringId)).get());
//  }
//}
