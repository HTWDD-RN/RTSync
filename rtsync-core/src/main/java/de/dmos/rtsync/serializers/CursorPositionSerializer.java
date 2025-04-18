package de.dmos.rtsync.serializers;

import java.util.HashMap;
import java.util.Map;

import de.dmos.rtsync.message.CursorPosition;

public class CursorPositionSerializer extends GenericMapSerializer<CursorPosition>
{
  private static final String FIELD_ID			= "id";
  private static final String FIELD_END_INDEX	= "end";
  private static final String FIELD_START_INDEX	= "start";

  protected CursorPositionSerializer()
  {
	super(
	  Map
	  .of(
		FIELD_ID,
		MessageSerialization.STRING_SERIALIZER,
		FIELD_END_INDEX,
		MessageSerialization.INT_SERIALIZER,
		FIELD_START_INDEX,
		MessageSerialization.INT_SERIALIZER));
  }

  @Override
  protected CursorPosition createFromMap(Map<String, Object> map)
  {
	Object start = map.get(FIELD_START_INDEX);
	return new CursorPosition(
	  (String) map.get(FIELD_ID),
	  start != null ? (Integer) start : null,
		  (int) map.get(FIELD_END_INDEX));
  }

  @Override
  protected Map<String, Object> objectToMap(CursorPosition obj)
  {
	Map<String, Object> map = new HashMap<>();
	map.put(FIELD_ID, obj.id());
	map.put(FIELD_END_INDEX, obj.endIndex());
	if ( obj.startIndex() != null )
	{
	  map.put(FIELD_START_INDEX, obj.startIndex());
	}
	return map;
  }
}
