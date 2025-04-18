package de.dmos.rtsync.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dmos.rtsync.serializers.MessageSerialization;
import de.dmos.rtsync.test.RTSyncTestHelper;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

class MessageSerializationTest
{
  @Test
  void testTaggedOperationSerialization() throws IOException
  {
	TaggedOperation<Operation<CombinedHandler>> taggedOp =
		RTSyncTestHelper.getStringAsTaggedInsertOperation("Hello!");
	RTSyncTestHelper
	.assertEqualAfterForthAndBackSerialization(MessageSerialization.TAGGED_OPERATION_SERIALIZER, taggedOp);
  }

  @Test
  void testTaggedUserOperationSerialization() throws IOException
  {
	TaggedUserOperation taggedUserOp = RTSyncTestHelper.getStringAsTaggedUserInsertOperation("Hi!", "user1");
	RTSyncTestHelper
	.assertEqualAfterForthAndBackSerialization(
	  MessageSerialization.TAGGED_USER_OPERATION_SERIALIZER,
	  taggedUserOp);
  }

  @Test
  void testColorSerialization() throws IOException
  {
	RTSyncTestHelper.assertEqualAfterForthAndBackSerialization(MessageSerialization.COLOR_SERIALIZER, Color.orange);
  }

  @Test
  void testSubscriberSerialization() throws IOException
  {
	RTSyncTestHelper
	.assertEqualAfterForthAndBackSerialization(
	  MessageSerialization.SUBSCRIBER_SERIALIZER,
	  new Subscriber(42, "Ernst", Color.cyan));
  }

  @Test
  void testSubscriberArraySerialization() throws IOException
  {
	Subscriber[] subscribers = {
	  new Subscriber(42, "Ernst", Color.cyan),
	  new Subscriber(75473, "Claudia", Color.red),
	  new Subscriber(23, "Rolf", Color.blue)};
	Subscriber[] readSubscribers =
		(Subscriber[]) RTSyncTestHelper
		.serializeForthAndBack(MessageSerialization.SUBSCRIBER_ARRAY_SERIALIZER, subscribers);
	assertArrayEquals(subscribers, readSubscribers);
  }

  @Test
  void testCursorsMessageSerialization() throws IOException
  {
	RTSyncTestHelper
	.assertEqualAfterForthAndBackSerialization(
	  MessageSerialization.USER_CURSORS_SERIALIZER,
	  new UserCursors(12345l, List.of(new CursorPosition("sId1", 0), new CursorPosition("sId2", 6))));
  }
}
