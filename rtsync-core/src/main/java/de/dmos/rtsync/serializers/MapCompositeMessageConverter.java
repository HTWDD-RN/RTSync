package de.dmos.rtsync.serializers;

import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;

/**
 * A {@link MessageConverter} that works similarly to a {@link CompositeMessageConverter} but chooses the appropriate
 * MessageConverter by looking up the class in a map instead of trying all succession.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */

public class MapCompositeMessageConverter implements MessageConverter
{
  private final Map<Class<?>, MessageConverter> _converterMap;

  public MapCompositeMessageConverter(Map<Class<?>, MessageConverter> converterMap)
  {
	_converterMap = converterMap;
  }

  @Override
  public Object fromMessage(Message<?> message, Class<?> targetClass)
  {
	MessageConverter targetConverter = _converterMap.get(targetClass);
	return targetConverter != null ? targetConverter.fromMessage(message, targetClass) : null;
  }

  @Override
  public Message<?> toMessage(Object payload, MessageHeaders headers)
  {
	MessageConverter targetConverter = _converterMap.get(payload.getClass());
	return targetConverter != null ? targetConverter.toMessage(payload, headers) : null;
  }

  public void registerMessageConverter(Class<?> clazz, MessageConverter converter)
  {
	_converterMap.put(clazz, converter);
  }
}
