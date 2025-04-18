package de.dmos.rtsync.serializers;

import java.util.Map;

import se.l4.exobytes.Serializer;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public class SimpleTaggedOperationSerializer
extends AbstractTaggedOperationSerializer<TaggedOperation<Operation<CombinedHandler>>>
{
  public SimpleTaggedOperationSerializer(Serializer<Operation<CombinedHandler>> combinedSerializer)
  {
    super(AbstractTaggedOperationSerializer.createSerializerMap(combinedSerializer));
  }

  protected SimpleTaggedOperationSerializer(Map<String, Serializer<?>> fieldSerializers)
  {
    super(fieldSerializers);
  }

  @Override
  protected TaggedOperation<Operation<CombinedHandler>> createFromMap(Map<String, Object> map)
  {
    return createBaseTaggedOperationFromMap(map);
  }

  @Override
  protected Map<String, Object> objectToMap(TaggedOperation<Operation<CombinedHandler>> obj)
  {
    return baseObjectToMap(obj);
  }
}
