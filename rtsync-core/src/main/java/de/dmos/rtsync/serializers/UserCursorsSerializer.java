package de.dmos.rtsync.serializers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.UserCursors;

public class UserCursorsSerializer extends GenericMapSerializer<UserCursors>
{
  private static final String FIELD_USER_ID	= "userId";
  private static final String		FIELD_CURSORS = "cursors";

  public UserCursorsSerializer()
  {
	super(
	  Map
	  .of(
		FIELD_USER_ID,
		MessageSerialization.LONG_SERIALIZER,
		FIELD_CURSORS,
		MessageSerialization.CURSOR_POSITON_ARRAY_SERIALIZER));
  }

  @Override
  protected UserCursors createFromMap(Map<String, Object> map)
  {
	CursorPosition[] cursors = (CursorPosition[]) map.get(FIELD_CURSORS);
	return new UserCursors((Long) map.get(FIELD_USER_ID), cursors != null ? Arrays.asList(cursors) : null);
  }

  @Override
  protected Map<String, Object> objectToMap(UserCursors obj)
  {
	Map<String, Object> map = new HashMap<>();
	map.put(FIELD_USER_ID, obj.userId());
	if ( obj.cursors() != null )
	{
	  map.put(FIELD_CURSORS, obj.cursors().toArray(CursorPosition[]::new));
	}
	return map;
  }
}
