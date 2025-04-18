package de.dmos.rtsync.serializers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;

public class RTStateSerializer extends GenericMapSerializer<RTState>
{
  private static final String FIELD_SUBSCRIBER_CURSORS	= "subscribers";
  private static final String FIELD_OPERATION      = "operation";

  public RTStateSerializer()
  {
	super(
	  Map
	  .of(
		FIELD_SUBSCRIBER_CURSORS,
		MessageSerialization.USER_CURSORS_LIST_SERIALIZER,
		FIELD_OPERATION,
		MessageSerialization.TAGGED_USER_OPERATION_SERIALIZER));
  }

  @Override
  protected RTState createFromMap(Map<String, Object> map)
  {
	UserCursors[] cursors = (UserCursors[]) map.get(FIELD_SUBSCRIBER_CURSORS);
	return new RTState(
	  cursors != null ? Arrays.asList(cursors) : null,
		  (TaggedUserOperation) map.get(FIELD_OPERATION));
  }

  @Override
  protected Map<String, Object> objectToMap(RTState obj)
  {
	Map<String, Object> map = new HashMap<>();
	map.put(FIELD_OPERATION, obj.taggedOperation());
	if ( obj.userCursors() != null )
	{
	  map.put(FIELD_SUBSCRIBER_CURSORS, obj.userCursors().toArray(UserCursors[]::new));
	}
	return map;
  }
}
