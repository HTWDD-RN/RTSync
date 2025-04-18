package de.dmos.rtsync.serializers;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.dmos.rtsync.message.Subscriber;

public class SubscriberSerializer extends GenericMapSerializer<Subscriber>
{
  private static final String FIELD_ID		 = "id";
  private static final String FIELD_NAME	 = "name";
  private static final String FIELD_COLOR	 = "color";
  private static final String FIELD_PROJECTS = "projects";

  public SubscriberSerializer()
  {
	super(
	  Map
	  .of(
		FIELD_ID,
		MessageSerialization.LONG_SERIALIZER,
		FIELD_NAME,
		MessageSerialization.STRING_SERIALIZER,
		FIELD_COLOR,
		MessageSerialization.COLOR_SERIALIZER,
		FIELD_PROJECTS,
		MessageSerialization.STRING_ARRAY_SERIALIZER));
  }

  @Override
  protected Subscriber createFromMap(Map<String, Object> map)
  {
	Object projects = map.get(FIELD_PROJECTS);
	return new Subscriber(
	  (long) map.get(FIELD_ID),
	  (String) map.get(FIELD_NAME),
	  (Color) map.get(FIELD_COLOR),
	  projects != null ? new HashSet<>(Set.of((String[]) projects)) : null);
  }

  @Override
  protected Map<String, Object> objectToMap(Subscriber obj)
  {
	Map<String, Object> map = new HashMap<>();
	map.put(FIELD_ID, obj.getId());
	map.put(FIELD_NAME, obj.getName());
	map.put(FIELD_COLOR, obj.getColor());
	if ( obj.getProjects() != null )
	{
	  map.put(FIELD_PROJECTS, obj.getProjects().toArray(String[]::new));
	}
	return map;
  }
}
