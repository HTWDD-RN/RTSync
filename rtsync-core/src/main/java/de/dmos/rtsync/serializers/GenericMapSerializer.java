package de.dmos.rtsync.serializers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import se.l4.exobytes.Serializer;
import se.l4.exobytes.streaming.StreamingInput;
import se.l4.exobytes.streaming.StreamingOutput;
import se.l4.exobytes.streaming.Token;

/**
 * A {@link Serializer} that serializes objects by using a map of expected fields and their corresponding serializers.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 * @param <T> The object type for this serializer
 */
public abstract class GenericMapSerializer<T> implements Serializer<T>
{
  private final Map<String, Serializer<?>> _fieldSerializers;

  protected GenericMapSerializer(Map<String, Serializer<?>> fieldSerializers)
  {
    _fieldSerializers = fieldSerializers;
  }

  @Override
  public T read(StreamingInput in) throws IOException
  {
    Map<String, Object> readFields = new HashMap<>();

    in.next(Token.OBJECT_START);

    while (in.peek() != Token.OBJECT_END)
    {
      in.next(Token.VALUE);
      String key = in.readString();
      Serializer<?> serializer = _fieldSerializers.getOrDefault(key, null);
      Object value;
      if (serializer == null) {
        value = in.readDynamic();
      } else {
        value = serializer.read(in);
      }
      readFields.put(key, value);
    }

    in.next(Token.OBJECT_END);

    return createFromMap(readFields);
  }

  @Override
  public void write(T object, StreamingOutput out) throws IOException
  {
    Map<String, Object> fieldMap = objectToMap(object);

    out.writeObjectStart(fieldMap.size());

    for ( Map.Entry<String, ?> entry : fieldMap.entrySet() )
    {
      if ( entry.getValue() == null )
      {
        continue;
      }

      out.writeString(entry.getKey());

      @SuppressWarnings("unchecked")
      Serializer<Object> serializer = (Serializer<Object>) _fieldSerializers.getOrDefault(entry.getKey(), null);
      if ( serializer == null )
      {
        out.writeDynamic(entry.getValue());
      }
      else
      {
        serializer.write(entry.getValue(), out);
      }
    }

    out.writeObjectEnd();
  }

  protected abstract T createFromMap(Map<String, Object> map);

  protected abstract Map<String, Object> objectToMap(T obj);
}
