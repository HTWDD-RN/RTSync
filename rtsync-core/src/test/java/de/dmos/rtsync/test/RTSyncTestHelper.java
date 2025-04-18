package de.dmos.rtsync.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import de.dmos.rtsync.customotter.CustomEditor;
import de.dmos.rtsync.customotter.CustomHistory;
import de.dmos.rtsync.customotter.CustomModelBuilder;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.serializers.MessageSerialization;
import se.l4.exobytes.Serializer;
import se.l4.otter.engine.DefaultEditorControl;
import se.l4.otter.engine.InMemoryOperationHistory;
import se.l4.otter.engine.LocalOperationSync;
import se.l4.otter.engine.OperationHistory;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedString;
import se.l4.otter.operations.OTType;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedDelta;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.combined.CombinedTypeBuilder;
import se.l4.otter.operations.internal.combined.DefaultCombinedDelta;
import se.l4.otter.operations.string.StringDelta;
import se.l4.otter.operations.string.StringHandler;

public class RTSyncTestHelper
{
  private static int id      = 15;
  private static int tokenId = 5000;

  public static Operation<CombinedHandler> getStringInsertOperation(String string)
  {
	return getStringInsertOperation("id-" + (id++), string);
  }

  public static Operation<CombinedHandler> getStringInsertOperation(String id, String string)
  {
	Operation<StringHandler> stringOp = StringDelta.builder().insert(string).done();
	return CombinedDelta.builder().update(id, "string", stringOp).done();
  }

  public static TaggedOperation<Operation<CombinedHandler>> getStringAsTaggedInsertOperation(String string)
  {
	return new TaggedOperation<>(1, "token-" + (tokenId++), getStringInsertOperation(string));
  }

  public static TaggedUserOperation getStringAsTaggedUserInsertOperation(String string)
  {
	return getStringAsTaggedUserInsertOperation(string, null);
  }

  public static TaggedUserOperation getStringAsTaggedUserInsertOperation(String string, String user)
  {
	return new TaggedUserOperation(getStringAsTaggedInsertOperation(string), user);
  }

  public static OperationHistory<Operation<CombinedHandler>> createInMemoryOperationHistory()
  {
	return new InMemoryOperationHistory<>(
		new CombinedTypeBuilder().build(),
		new DefaultCombinedDelta<>(o -> o).done());
  }

  public static LocalOperationSync<Operation<CombinedHandler>> createLocalOperationSync()
  {
	return new LocalOperationSync<>(new DefaultEditorControl<>(createInMemoryOperationHistory()));
  }

  public static CustomEditor<Operation<CombinedHandler>> createLocalSyncedInMemoryEditor()
  {
	return new CustomEditor<>(createLocalOperationSync());
  }

  public static Model createLocalSyncedInMemoryModel()
  {
	return new CustomModelBuilder(createLocalSyncedInMemoryEditor()).build();
  }

  @SuppressWarnings("unchecked")
  public static <T extends Operation<CombinedHandler>> CustomHistory<T> createCustomHistory() {

	return new CustomHistory<>(
		(OTType<T>) MessageSerialization.COMBINED_TYPE,
		(T) new DefaultCombinedDelta<>(o -> o).done(),
		true);
  }

  /**
   * Checks, whether the value of the given sharedString equals the result of concatenating the two given strings in any
   * order after appending them to the given fixedStart.
   */
  public static boolean sharedStringIs(
	SharedString sharedString,
	String fixedStart,
	String string1,
	String string2)
  {
	String actualString = sharedString.get();
	String possibility1 = fixedStart + string1 + string2;
	String possibility2 = fixedStart + string2 + string1;
	return possibility1.equals(actualString) || possibility2.equals(actualString);
  }

  /**
   * Checks, whether the value of the given sharedString equals the result of concatenating the two given strings in any
   * order.
   */
  public static boolean sharedStringIs(SharedString sharedString, String added1, String added2)
  {
	return sharedStringIs(sharedString, "", added1, added2);
  }

  /**
   * Asserts, that the value of the given sharedString equals the result of concatenating the two given strings in any
   * order.
   */
  public static void assertSharedString(SharedString sharedString, String added1, String added2)
  {
	assertTrue(sharedStringIs(sharedString, added1, added2), () -> "The shared string '"
		+ sharedString.get()
		+ "' doesn't match any of the expected ones: '"
		+ added1
		+ added2
		+ "' or '"
		+ added2
		+ added1
		+ "'.");
  }

  public static void assertSharedString(SharedString sharedString, String fixedStart, String added1, String added2)
  {
	assertTrue(
	  sharedStringIs(sharedString, fixedStart, added1, added2),
	  () -> "The shared string '"
		  + sharedString.get()
		  + "' doesn't match any of the expected ones: '"
		  + fixedStart
		  + added1
		  + added2
		  + "' or '"
		  + fixedStart
		  + added2
		  + added1
		  + "'.");
  }

  /**
   * Runs the given function for the given number of times in order to help find bugs with race conditions.
   */
  public static void runMultipleTimes(int times, Runnable runnable)
  {
	for ( int i = 0; i < times; i++ )
	{
	  runnable.run();
	}
  }

  public static <T> T serializeForthAndBack(Serializer<T> serializer, T object) throws IOException
  {
	byte[] bytes = MessageSerialization.toByteArray(serializer, object);
	return MessageSerialization.read(serializer, bytes);
  }

  public static <T> void assertEqualAfterForthAndBackSerialization(Serializer<T> serializer, T object)
	  throws IOException
  {
	T readObject = serializeForthAndBack(serializer, object);
	assertEquals(object, readObject);
  }
}
