package de.dmos.rtsync.serializers;

import java.util.HashMap;
import java.util.Map;

import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.exobytes.Serializer;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

/**
 * A {@link GenericMapSerializer} which can write {@link TaggedOperation<<Operation<CombinedHandler>>}s and which reads
 * byte arrays as {@link TaggedUserOperation}s
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class TaggedUserOperationSerializer extends AbstractTaggedOperationSerializer<TaggedUserOperation>
{
  private static final String FIELD_USER = "user";
  private static final String FIELD_MERGED_IDS = "mergedIds";

  public TaggedUserOperationSerializer(Serializer<Operation<CombinedHandler>> combinedSerializer)
  {
	super(TaggedUserOperationSerializer.createSerializerMap(combinedSerializer));
  }

  protected static Map<String, Serializer<?>> createSerializerMap(
	Serializer<Operation<CombinedHandler>> combinedSerializer)
  {
	Map<String, Serializer<?>> serializers =
		AbstractTaggedOperationSerializer.createSerializerMap(combinedSerializer);
	serializers = new HashMap<>(serializers);
	serializers.put(FIELD_USER, MessageSerialization.STRING_SERIALIZER);
	serializers.put(FIELD_MERGED_IDS, MessageSerialization.STRING_ARRAY_SERIALIZER);
	return serializers;
  }

  @Override
  protected TaggedUserOperation createFromMap(Map<String, Object> map)
  {
	TaggedOperation<Operation<CombinedHandler>> taggedOp = createBaseTaggedOperationFromMap(map);
	return new TaggedUserOperation(taggedOp, (String) map.get(FIELD_USER), (String[]) map.get(FIELD_MERGED_IDS));
  }

  @Override
  protected Map<String, Object> objectToMap(TaggedUserOperation obj)
  {
	Map<String, Object> fields = baseObjectToMap(obj);
	if ( obj instanceof TaggedUserOperation userOp )
	{
	  fields.put(FIELD_USER, userOp.getUser());
	  fields.put(FIELD_MERGED_IDS, userOp.getMergedIds());
	}
	return fields;
  }
}
