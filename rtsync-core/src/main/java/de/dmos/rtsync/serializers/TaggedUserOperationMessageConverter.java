package de.dmos.rtsync.serializers;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.TaggedOperation;

public class TaggedUserOperationMessageConverter implements MessageConverter
{
  @Override
  public TaggedUserOperation fromMessage(Message<?> message, Class<?> targetClass)
  {
    if ( message.getPayload() instanceof byte[] bytes )
    {
      return MessageSerialization.tryReadTaggedUserOperation(bytes);
    }
    return null;
  }

  @SuppressWarnings("all")
  @Override
  public Message<?> toMessage(Object payload, MessageHeaders headers)
  {
    if ( payload instanceof TaggedOperation taggedOp )
    {
      byte[] bytes = MessageSerialization.tryToGetByteArray(taggedOp);
      if ( bytes != null )
      {
        return MessageBuilder.createMessage(bytes, headers);
      }
    }
    return null;
  }

}
