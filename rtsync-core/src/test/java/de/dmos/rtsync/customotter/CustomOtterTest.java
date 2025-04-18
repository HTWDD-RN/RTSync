package de.dmos.rtsync.customotter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.Test;

import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.test.RTSyncTestHelper;
import de.dmos.rtsync.test.SharedMapUpdateListener;
import de.dmos.rtsync.util.SharedObjectHelper;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedList;
import se.l4.otter.model.SharedString;
import se.l4.otter.model.spi.DataValues;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedDelta;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.map.MapDelta;
import se.l4.otter.operations.map.MapHandler;
import se.l4.otter.operations.string.StringDelta;
import se.l4.otter.operations.string.StringHandler;

class CustomOtterTest
{
  @Test
  void testReset()
  {
    CustomEditor<Operation<CombinedHandler>> editor =
        (CustomEditor<Operation<CombinedHandler>>) RTSyncTestHelper.createLocalSyncedInMemoryEditor();
    Model model = new CustomModelBuilder(editor).build();

    String value1 = "String 1";
    String value2 = "Some more text";
    String value3 = "something completely different";
    String idInModel = "e1";
    String ssId = "ss1";

    Operation<MapHandler> putIntoMapOperation =
        MapDelta.builder().set(idInModel, DataValues.toData(null), List.of("ref", ssId, "string")).done();
    Operation<StringHandler> stringOp = StringDelta.builder().insert(value1).done();
    Operation<CombinedHandler> cOp1 =
        CombinedDelta.builder().update(ssId, "string", stringOp).update("root", "map", putIntoMapOperation).done();

    TaggedUserOperation userOp1 = new TaggedUserOperation(1, "t1", cOp1, null);
    editor.onTaggedUserOperationReceived(userOp1, false);
    SharedString ss1 = model.get(idInModel);
    assertEquals(value1, ss1.get());

    Operation<StringHandler> stringOp2 = StringDelta.builder().retain(value1.length()).insert(value2).done();
    Operation<CombinedHandler> cOp2 = CombinedDelta.builder().update(ssId, "string", stringOp2).done();
    TaggedUserOperation userOp2 = new TaggedUserOperation(2, "t2", cOp2, null);
    editor.onTaggedUserOperationReceived(userOp2, false);
    assertEquals(ss1, model.get(idInModel));
    assertEquals(value1 + value2, ss1.get());

    SharedMapUpdateListener updateListener = new SharedMapUpdateListener();
    model.addChangeListener(updateListener);

    editor.onTaggedUserOperationReceived(userOp1, true);

    assertEquals(ss1, model.get(idInModel));
    assertEquals(value1, ss1.get());
    assertTrue(updateListener.getUpdates().isEmpty()); // The SharedString value changed, but not the model

    String newSsId = "otherSsId";
    Operation<MapHandler> putIntoMapOperation2 =
        MapDelta.builder().set(idInModel, DataValues.toData(null), List.of("ref", newSsId, "string")).done();
    Operation<StringHandler> stringOp3 = StringDelta.builder().insert(value3).done();
    Operation<CombinedHandler> cOp3 = CombinedDelta
        .builder()
        .update(newSsId, "string", stringOp3)
        .update("root", "map", putIntoMapOperation2)
        .done();
    TaggedUserOperation userOp3 = new TaggedUserOperation(6, "t500", cOp3, null);
    editor.onTaggedUserOperationReceived(userOp3, true);

    SharedString ss2 = model.get(idInModel);
    assertNotEquals(ss1, ss2); // The SharedString's id was changed, so this is another SharedString
    assertEquals(value3, ss2.get());
    List<Triple<String, Object, Object>> updateList = updateListener.getUpdates();
    assertEquals(1, updateList.size());
    assertEquals(Triple.of(idInModel, ss1, ss2), updateList.get(0));
  }

  /**
   * Tests {@link SharedList#removeRange(int, int)} of the used {@link CustomSharedList}. This test should lead to the
   * same result as {@link #testCustomSharedListRemove()}.
   */
  @Test
  void testCustomSharedListRemoveRange()
  {
    SharedList<String> list = RTSyncTestHelper.createLocalSyncedInMemoryModel().newList();
    list.insertAll(0, List.of("entry 1", "entry 2", "entry 3", "entry 4"));
    list.removeRange(1, 3);
    list.add("new entry");
    assertTrue(SharedObjectHelper.sharedListEquals(list, List.of("entry 1", "entry 4", "new entry")));
  }

  @Test
  void testCustomSharedListRemoveRange2()
  {
    SharedList<String> list = RTSyncTestHelper.createLocalSyncedInMemoryModel().newList();
    list.insertAll(0, List.of("entry 1", "entry 2", "entry 3", "entry 4"));
    list.add("new entry");
    list.removeRange(0, 3);
    assertTrue(SharedObjectHelper.sharedListEquals(list, List.of("entry 4", "new entry")));
  }

  @Test
  void testCustomSharedListRemove()
  {
    SharedList<String> list = RTSyncTestHelper.createLocalSyncedInMemoryModel().newList();
    list.insertAll(0, List.of("entry 1", "entry 2", "entry 3", "entry 4"));
    list.remove(2);
    list.remove(1);
    list.add("new entry");
    assertTrue(SharedObjectHelper.sharedListEquals(list, List.of("entry 1", "entry 4", "new entry")));
  }
}
