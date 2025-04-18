package de.dmos.rtsync.message;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dmos.rtsync.serializers.SimpleMessageSerialization;
import de.dmos.rtsync.test.RTSyncTestHelper;

class SimpleMessageSerializationTest
{
  @Test
  void testClientInitMessageSerialization() throws IOException
  {
	Subscriber subscriber = new Subscriber(1, "Frida", Color.magenta);
	TaggedUserOperation userOp = RTSyncTestHelper.getStringAsTaggedUserInsertOperation("Hi!", "Gerhard");
	SimpleStateMessage stateMessage1 = new SimpleStateMessage(subscriber, new RTState(null, userOp));
	RTSyncTestHelper
	.assertEqualAfterForthAndBackSerialization(
	  SimpleMessageSerialization.SIMPLE_STATE_MESSAGE_SERIALIZER,
	  stateMessage1);

	List<UserCursors> cursors = List.of(new UserCursors(123l, List.of(new CursorPosition("abc", 0))));
	SimpleStateMessage stateMessage2 = new SimpleStateMessage(subscriber, new RTState(cursors, userOp));
	RTSyncTestHelper
	.assertEqualAfterForthAndBackSerialization(
	  SimpleMessageSerialization.SIMPLE_STATE_MESSAGE_SERIALIZER,
	  stateMessage2);
  }
}
