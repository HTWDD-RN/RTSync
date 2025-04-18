package de.dmos.rtsync.serializers;

import java.util.Map;

import org.springframework.messaging.converter.MessageConverter;

import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.Subscriber;

public class ProjectMessageSerialization extends MessageSerialization
{
  private static ProjectMessageSerialization instance;

  private ProjectMessageSerialization()
  {
	super();
  }

  /**
   * The {@link MessageConverter} that is able to convert all messages between server and client in both directions.
   */
  public static MapCompositeMessageConverter getCombinedMessageConverter()
  {
	if ( ProjectMessageSerialization.instance == null )
	{
	  ProjectMessageSerialization.instance = new ProjectMessageSerialization();
	}
	return instance._combinedMessageConverter;
  }

  @Override
  protected void addMessageConverters(Map<Class<?>, MessageConverter> converterMap)
  {
	super.addMessageConverters(converterMap);

	MessageConverter projectListConverter = MessageSerialization
		.createSimpleMessageConverter(MessageSerialization.STRING_ARRAY_SERIALIZER, String[].class);
	MessageConverter stateMessageConverter =
	  MessageSerialization.createSimpleMessageConverter(RT_STATE_SERIALIZER, RTState.class);
	MessageConverter subscriberConverter = MessageSerialization
		.createSimpleMessageConverter(MessageSerialization.SUBSCRIBER_SERIALIZER, Subscriber.class);

	converterMap.put(RTState.class, stateMessageConverter);
	converterMap.put(Subscriber.class, subscriberConverter);
	converterMap.put(String[].class, projectListConverter);
  }
}
