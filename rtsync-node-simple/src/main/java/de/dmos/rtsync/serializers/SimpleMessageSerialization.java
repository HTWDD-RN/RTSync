package de.dmos.rtsync.serializers;

import java.util.Map;

import org.springframework.messaging.converter.MessageConverter;

import de.dmos.rtsync.message.SimpleStateMessage;

public class SimpleMessageSerialization extends MessageSerialization
{
  public static final SimpleStateMessageSerializer SIMPLE_STATE_MESSAGE_SERIALIZER =
	  new SimpleStateMessageSerializer();

  private static SimpleMessageSerialization		   instance;

  private SimpleMessageSerialization()
  {
	super();
  }

  /**
   * The {@link MessageConverter} that is able to convert all messages between server and client in both directions.
   */
  public static MapCompositeMessageConverter getCombinedMessageConverter()
  {
	if ( SimpleMessageSerialization.instance == null )
	{
	  SimpleMessageSerialization.instance = new SimpleMessageSerialization();
	}
	return instance._combinedMessageConverter;
  }

  @Override
  protected void addMessageConverters(Map<Class<?>, MessageConverter> converterMap)
  {
	super.addMessageConverters(converterMap);

	MessageConverter stateMessageConverter = MessageSerialization
		.createSimpleMessageConverter(
		  SIMPLE_STATE_MESSAGE_SERIALIZER,
		  SimpleStateMessage.class);

	converterMap.put(SimpleStateMessage.class, stateMessageConverter);
  }
}
