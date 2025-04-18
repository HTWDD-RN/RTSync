package de.dmos.rtsync.serializers;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public class TaggedOperationMessageConverter implements MessageConverter
{
  @Override
  public TaggedOperation<Operation<CombinedHandler>> fromMessage(Message<?> message, Class<?> targetClass)
  {
    // TODO: Maybe we should find a better way than to try the MessageSerialization on every message. Not all messages are supposed to be transformed to TaggedOperations are they?
    if ( message.getPayload() instanceof byte[] bytes )
    {
      return MessageSerialization.tryReadTaggedOperation(bytes);
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
