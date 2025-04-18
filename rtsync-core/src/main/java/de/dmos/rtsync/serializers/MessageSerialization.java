package de.dmos.rtsync.serializers;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.streaming.CustomJsonInput;
import se.l4.exobytes.Serializer;
import se.l4.exobytes.array.ArraySerializer;
import se.l4.exobytes.internal.streaming.JsonOutput;
import se.l4.exobytes.standard.IntSerializer;
import se.l4.exobytes.standard.LongSerializer;
import se.l4.exobytes.standard.StringSerializer;
import se.l4.exobytes.streaming.StreamingInput;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.combined.CombinedType;
import se.l4.otter.operations.combined.CombinedTypeBuilder;

public abstract class MessageSerialization
{
  private static final Logger						  LOG							   =
	  LoggerFactory.getLogger(MessageSerialization.class);
  public static final CombinedType					  COMBINED_TYPE					   =
	  new CombinedTypeBuilder().build();

  public static final StringSerializer				  STRING_SERIALIZER				   = new StringSerializer();
  public static final ArraySerializer				  STRING_ARRAY_SERIALIZER		   =
	  new ArraySerializer(String.class, STRING_SERIALIZER);
  public static final IntSerializer					  INT_SERIALIZER				   = new IntSerializer();
  public static final LongSerializer				  LONG_SERIALIZER				   = new LongSerializer();
  public static final ColorSerializer				  COLOR_SERIALIZER				   = new ColorSerializer();
  public static final SubscriberSerializer			  SUBSCRIBER_SERIALIZER			   = new SubscriberSerializer();
  public static final ArraySerializer				  SUBSCRIBER_ARRAY_SERIALIZER	   =
	  new ArraySerializer(Subscriber.class, SUBSCRIBER_SERIALIZER);
  public static final SimpleTaggedOperationSerializer TAGGED_OPERATION_SERIALIZER	   =
	  new SimpleTaggedOperationSerializer(MessageSerialization.COMBINED_TYPE.getSerializer());
  public static final TaggedUserOperationSerializer	  TAGGED_USER_OPERATION_SERIALIZER =
	  new TaggedUserOperationSerializer(MessageSerialization.COMBINED_TYPE.getSerializer());
  public static final CursorPositionSerializer		  CURSOR_POSITION_SERIALIZER	   =
	  new CursorPositionSerializer();
  public static final ArraySerializer				  CURSOR_POSITON_ARRAY_SERIALIZER  =
	  new ArraySerializer(CursorPosition.class, CURSOR_POSITION_SERIALIZER);
  public static final UserCursorsSerializer			  USER_CURSORS_SERIALIZER		   =
	  new UserCursorsSerializer();
  public static final ArraySerializer				  USER_CURSORS_LIST_SERIALIZER	   =
	  new ArraySerializer(UserCursors.class, MessageSerialization.USER_CURSORS_SERIALIZER);
  public static final RTStateSerializer				  RT_STATE_SERIALIZER			   = new RTStateSerializer();

  @SuppressWarnings("unchecked")
  public static <T> byte[] toByteArray(Serializer<? extends T> serializer, T object) throws IOException
  {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	try (JsonOutput json = new JsonOutput(out))
	{
	  ((Serializer<T>) serializer).write(object, json);
	}
	return out.toByteArray();
  }

  public static <T> T read(Serializer<T> serializer, /*Class<?> targetClass,*/ byte[] data) throws IOException
  {
	try (StreamingInput in = new CustomJsonInput(new ByteArrayInputStream(data)))
	{
	  return in.readObject(serializer);
	}
  }

  public static <T> T tryRead(Serializer<? extends T> serializer, /*Class<?> targetClass,*/ byte[] data)
  {
	try
	{
	  return read(serializer, /*targetClass,*/ data);
	}
	catch (IOException ioe)
	{
	  LOG.error("Could not deserialize " + data, ioe); // Maybe logging isn't necessary here.
	  return null;
	}
  }

  private static <T> byte[] tryToGetByteArray(Serializer<? extends T> serializer, T object)
  {
	try
	{
	  return toByteArray(serializer, object);
	}
	catch (IOException ioe)
	{
	  LOG.error("Could not serialize " + object, ioe); // Maybe logging isn't necessary here.
	  return null;
	}
  }

  public static TaggedOperation<Operation<CombinedHandler>> readTaggedOperation(byte[] data) throws IOException
  {
	return read(TAGGED_OPERATION_SERIALIZER, data);
  }

  public static byte[] toByteArray(TaggedOperation<Operation<CombinedHandler>> taggedOp) throws IOException
  {
	return toByteArray(
	  taggedOp instanceof TaggedUserOperation ? TAGGED_USER_OPERATION_SERIALIZER : TAGGED_OPERATION_SERIALIZER,
		  taggedOp);
  }

  public static TaggedOperation<Operation<CombinedHandler>> tryReadTaggedOperation(byte[] data)
  {
	return tryRead(TAGGED_OPERATION_SERIALIZER, data);
  }

  public static byte[] tryToGetByteArray(TaggedOperation<Operation<CombinedHandler>> taggedOp)
  {
	return tryToGetByteArray(
	  taggedOp instanceof TaggedUserOperation ? TAGGED_USER_OPERATION_SERIALIZER : TAGGED_OPERATION_SERIALIZER,
		  taggedOp);
  }

  public static TaggedUserOperation readTaggedUserOperation(byte[] data) throws IOException
  {
	return read(TAGGED_USER_OPERATION_SERIALIZER, data);
  }

  public static TaggedUserOperation tryReadTaggedUserOperation(byte[] data)
  {
	return tryRead(TAGGED_USER_OPERATION_SERIALIZER, data);
  }

  public static <T> MessageConverter createSimpleMessageConverter(Serializer<T> serializer, Class<?> clazz)
  {
	return new MessageConverter() {

	  @Override
	  public T fromMessage(Message<?> message, Class<?> targetClass)
	  {
		if ( message.getPayload() instanceof byte[] bytes )
		{
		  return MessageSerialization.tryRead(serializer, bytes);
		}
		return null;
	  }

	  @Override
	  public Message<?> toMessage(Object payload, MessageHeaders headers)
	  {
		byte[] bytes = MessageSerialization.tryToGetByteArray(serializer, payload);
		if ( bytes != null )
		{
		  return MessageBuilder.createMessage(bytes, headers);
		}
		return null;
	  }
	};
  }

  protected MessageSerialization()
  {
	_combinedMessageConverter = createCombinedMessageConverter();
  }

  protected MapCompositeMessageConverter _combinedMessageConverter;

  public MapCompositeMessageConverter getMessageConverter()
  {
	return _combinedMessageConverter;
  }

  protected void addMessageConverters(Map<Class<?>, MessageConverter> converterMap)
  {
	TaggedUserOperationMessageConverter userOpConverter = new TaggedUserOperationMessageConverter();
	MessageConverter colorMessageConverter =
		MessageSerialization.createSimpleMessageConverter(MessageSerialization.COLOR_SERIALIZER, Color.class);
	MessageConverter subscriberMessageConverter = MessageSerialization
		.createSimpleMessageConverter(MessageSerialization.SUBSCRIBER_ARRAY_SERIALIZER, Subscriber[].class);
	MessageConverter cursorsMessageConverter = MessageSerialization
		.createSimpleMessageConverter(MessageSerialization.USER_CURSORS_SERIALIZER, UserCursors.class);
	MessageConverter cursorsListMessageConverter = MessageSerialization
		.createSimpleMessageConverter(MessageSerialization.USER_CURSORS_LIST_SERIALIZER, UserCursors[].class);
	MessageConverter cursorPositionConverter = MessageSerialization
		.createSimpleMessageConverter(MessageSerialization.CURSOR_POSITION_SERIALIZER, CursorPosition.class);

	converterMap
	.putAll(
	  Map
	  .of(
		String.class,
		new StringMessageConverter(),
		Color.class,
		colorMessageConverter,
		Subscriber[].class,
		subscriberMessageConverter,
		TaggedOperation.class,
		userOpConverter,
		TaggedUserOperation.class,
		userOpConverter,
		UserCursors.class,
		cursorsMessageConverter,
		UserCursors[].class,
		cursorsListMessageConverter,
		CursorPosition.class,
		cursorPositionConverter));
  }

  protected MapCompositeMessageConverter createCombinedMessageConverter()
  {
	Map<Class<?>, MessageConverter> converterMap = new HashMap<>();
	addMessageConverters(converterMap);
	return new MapCompositeMessageConverter(converterMap);
  }
}
