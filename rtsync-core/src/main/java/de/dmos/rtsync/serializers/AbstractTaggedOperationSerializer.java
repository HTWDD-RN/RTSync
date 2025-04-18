package de.dmos.rtsync.serializers;

import java.util.Map;
import java.util.TreeMap;

import se.l4.exobytes.Serializer;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public abstract class AbstractTaggedOperationSerializer<T extends TaggedOperation<Operation<CombinedHandler>>>
extends GenericMapSerializer<T>
{
  private static final String FIELD_HISTORY_ID = "historyId";
  private static final String FIELD_TOKEN      = "token";
  private static final String FIELD_OPERATIONS = "operations";

  protected AbstractTaggedOperationSerializer(Map<String, Serializer<?>> fieldSerializers)
  {
    super(fieldSerializers);
  }

  protected static Map<String, Serializer<?>> createSerializerMap(
    Serializer<Operation<CombinedHandler>> combinedSerializer)
  {
    Map<String, Serializer<?>> serializers = new TreeMap<>();
    serializers.put(FIELD_HISTORY_ID, MessageSerialization.LONG_SERIALIZER);
    serializers.put(FIELD_TOKEN, MessageSerialization.STRING_SERIALIZER);
    serializers.put(FIELD_OPERATIONS, combinedSerializer);
    return serializers;
  }

  protected TaggedOperation<Operation<CombinedHandler>> createBaseTaggedOperationFromMap(Map<String, Object> map)
  {
    return new TaggedOperation<>(
        (long) map.get(FIELD_HISTORY_ID),
        (String) map.get(FIELD_TOKEN),
        (Operation<CombinedHandler>) map.get(FIELD_OPERATIONS));
  }

  protected Map<String, Object> baseObjectToMap(TaggedOperation<Operation<CombinedHandler>> obj)
  {
    Map<String, Object> fieldMap = new TreeMap<>();
    fieldMap.put(FIELD_HISTORY_ID, obj.getHistoryId());
    fieldMap.put(FIELD_TOKEN, obj.getToken());
    fieldMap.put(FIELD_OPERATIONS, obj.getOperation());
    return fieldMap;
  }
}
