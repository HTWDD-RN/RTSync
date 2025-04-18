package de.dmos.rtsync.serializers;

import java.util.Map;

import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.SimpleStateMessage;
import de.dmos.rtsync.message.Subscriber;

public class SimpleStateMessageSerializer extends GenericMapSerializer<SimpleStateMessage>
{
  private static final String FIELD_SELF_SUBSCRIBER	= "subscriber";
  private static final String FIELD_STATE	   = "state";

  public SimpleStateMessageSerializer()
  {
	super(
	  Map
	  .of(
			FIELD_SELF_SUBSCRIBER,
		MessageSerialization.SUBSCRIBER_SERIALIZER,
		FIELD_STATE,
		MessageSerialization.RT_STATE_SERIALIZER));
  }

  @Override
  protected SimpleStateMessage createFromMap(Map<String, Object> map)
  {
	return new SimpleStateMessage(
	  (Subscriber) map.get(FIELD_SELF_SUBSCRIBER),
	  (RTState) map.get(FIELD_STATE));
  }

  @Override
  protected Map<String, Object> objectToMap(SimpleStateMessage obj)
  {
	return Map
		.of(
		  FIELD_SELF_SUBSCRIBER,
		  obj.selfSubscriber(),
		  FIELD_STATE,
		  obj.rtState());
  }
}
