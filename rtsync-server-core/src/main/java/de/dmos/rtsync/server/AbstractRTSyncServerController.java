package de.dmos.rtsync.server;

import java.awt.Color;
import java.security.Principal;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.DefaultStompSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.network.CommunicationConstants;
import de.dmos.rtsync.network.EndpointPaths;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public class AbstractRTSyncServerController
{
  private static final Logger			LOG					 =
	  LoggerFactory.getLogger(AbstractRTSyncServerController.class);

  protected final SimpUserRegistry									_simpUserRegistry;
  protected final SimpMessagingTemplate								_simpMessagingTemplate;

  /**
   * This makes the server refuse updates sent via {@link #sendOperation} if they are based on an outdated historyId.
   * That way, the client is forced to fetch the current state, apply it and send their update again.
   *
   * Note that this might be too strict and it could suffice to reject only changes of model objects that have been
   * updated in the meantime. Another idea would be to try to transform the received operation with the operation of a
   * the matching old id which the server has stored in its history and then compose the following operations on that to
   * get the newest state.
   */
  protected boolean													_rejectOldHistoryIds = false;

  protected AbstractRTSyncServerController(
	SimpUserRegistry simpUserRegistry,
	SimpMessagingTemplate simpMessagingTemplate)
  {
	super();
	_simpUserRegistry = simpUserRegistry;
	_simpMessagingTemplate = simpMessagingTemplate;
  }

  public boolean isRejectOldHistoryIds()
  {
	return _rejectOldHistoryIds;
  }

  public void setRejectOldHistoryIds(boolean rejectOldHistoryIds)
  {
	_rejectOldHistoryIds = rejectOldHistoryIds;
  }

  @EventListener
  public void onDisconnectEvent(SessionDisconnectEvent event)
  {
	Principal principal = event.getUser();
	LOG.debug("{} disconnected", principal != null ? principal.getName() : "<unknown>");
	// This event gets fired before the user is removed from the SimpUserRegistry, so we postpone the broadcast.
	// This doesn't need to run on the main thread, but SwingUtilities.invokeLater is a quick solution.
	SwingUtilities.invokeLater(this::broadCastAllSubscribers);
  }

  @MessageMapping(EndpointPaths.PATH_SET_OWN_NAME)
  public void setUserName(@Payload String newName, Principal principal)
  {
	LOG.debug("{} called setUserName({})", principal.getName(), newName);
	if ( principal instanceof SimpleUnauthorizedPrincipal simplePrincipal )
	{
	  simplePrincipal.setName(newName);
	}
	else
	{
	  logUnexpectedPrincipalError(principal);
	  return;
	}
	broadCastAllSubscribers();
  }

  @MessageMapping(EndpointPaths.PATH_SET_OWN_COLOR)
  public void setUserColor(@Payload Color newColor, Principal principal)
  {
	LOG.debug("{} called setUserColor({})", principal.getName(), newColor);
	if (principal instanceof SimpleUnauthorizedPrincipal simplePrincipal) {
	  if ( newColor == null )
	  {
		simplePrincipal.resetColor();
	  }
	  else
	  {
		simplePrincipal.setColor(newColor);
	  }
	} else {
	  handleException(new Throwable("Unexpected principal: " + principal));
	}
	broadCastAllSubscribers();
  }

  protected Stream<Subscriber> getSubscriberStream()
  {
	return _simpUserRegistry
		.getUsers()
		.stream()
		.map(u -> getSubscriber(u.getPrincipal()));
  }

  public void broadCastAllSubscribers()
  {
	_simpMessagingTemplate
	.convertAndSend(EndpointPaths.TOPIC_SUBSCRIBERS, getSubscriberStream().toArray(Subscriber[]::new));
  }

  protected Subscriber getSubscriber(Principal principal)
  {
	if ( principal instanceof SimpleUnauthorizedPrincipal simplePrincipal )
	{
	  return simplePrincipal.getSubscriber();
	}
	logUnexpectedPrincipalError(principal);
	return new Subscriber(0, principal.getName(), null);
  }

  private void logUnexpectedPrincipalError(Principal principal)
  {
	LOG.error("User {} has an unexpected principal: {}", principal.getName(), principal);
  }

  public void handleException(Throwable throwable, String sender)
  {
	String message = logExceptionAndGetMessage(throwable);
	_simpMessagingTemplate.convertAndSendToUser(sender, EndpointPaths.QUEUE_EXCEPTION, message);
  }

  @MessageExceptionHandler
  @SendToUser(EndpointPaths.QUEUE_EXCEPTION)
  public String handleException(Throwable exception)
  {
	return logExceptionAndGetMessage(exception);
  }

  protected String logExceptionAndGetMessage(Throwable exception)
  {
	LOG.error("handleException called", exception);
	return CommunicationConstants.SERVER_EXCEPTION_MESSAGE_START + exception.getMessage();
  }

  protected String getOldHistoryIdMessage(
	TaggedOperation<Operation<CombinedHandler>> taggedOp,
	long currentHistoryId)
  {
	StringBuilder builder = new StringBuilder(CommunicationConstants.SERVER_EXCEPTION_MESSAGE_START);
	builder.append(CommunicationConstants.MESSAGE_START_OLD_HISTORY_ID);
	builder.append(taggedOp.getHistoryId());
	builder.append(" < ");
	builder.append(currentHistoryId);
	return builder.toString();
  }

  // Unfortunately, this doesn't work because we can't reuse the same subscriptionId See also the comment block 30 lines below.
  //
  //  /**
  //   * This initializes new clients by subscribing them to relevant topics and sending them the latest value. By using
  //   * this API the client saves much time and network overhead compared to sending multiple single subscriptions and
  //   * requests.
  //   */
  //  @SubscribeMapping(EndpointPaths.INIT_SUBSCRIPTIONS)
  //  public ClientInitMessage initSubscriptions(
  //    Principal principal,
  //    MessageHeaders headers,
  //    MessageHeaderAccessor accessor)
  //  {
  //    LOG.debug("{} called initSubscriptions", principal.getName());
  //    String sessionId = (String) headers.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
  //    String subscriptionId = (String) headers.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
  //    if ( sessionId == null || subscriptionId == null )
  //    {
  //      LOG
  //      .warn(
  //        "{} sent {}:{} and {}:{}. Both must not be null for subscriptions, so no subscription is registered!",
  //        principal.getName(),
  //        SimpMessageHeaderAccessor.SESSION_ID_HEADER,
  //        sessionId,
  //        SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER,
  //        subscriptionId);
  //    }
  //    else
  //    {
  //      subscribe(sessionId, subscriptionId, EndpointPaths.USER_QUEUE_EXCEPTION);
  //      // It would have been nice, if we could have subscribed the client to multiple topics at once but unfortunately, that doesn't work so easyly.
  //      // The following handling of the subscription IDs doesn't work, because the client doesn't connect the new ids with their handler.
  //      // If we reused the same id, then it is overwritten in the SubscriptionRegistry by the next subscription with the same id.
  //      // Furthermore, this prevents the subscriptions from being found if the user should ever try
  //      // to unsubscribe from the subscription id he provided.
  //      // subscribe(sessionId, subscriptionId + "-topic-subscribers", EndpointPaths.TOPIC_SUBSCRIBERS);
  //      // subscribe(sessionId, subscriptionId + "-topic-operations", EndpointPaths.TOPIC_OPERATIONS);
  //
  //      broadCastAllSubscribers();
  //    }
  //
  //    return new ClientInitMessage(getSubscriber(principal), getLatestOperationAsUserOperation());
  //  }

  /**
   * Adds an internal subscription for the client with the given sessionId to the given topic. This is supposed to work
   * as if the client had sent a subscribe message to that topic.
   */
  public void subscribe(String sessionId, String subscriptionId, String topic)
  {
	SimpMessageHeaderAccessor subscribersAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
	subscribersAccessor.setDestination(topic);
	subscribersAccessor.setSessionId(sessionId);
	subscribersAccessor.setSubscriptionId(subscriptionId);
	Message<byte[]> subscribeMessage =
		MessageBuilder.createMessage(DefaultStompSession.EMPTY_PAYLOAD, subscribersAccessor.toMessageHeaders());
	_simpMessagingTemplate.send(subscribeMessage);
  }

  // The solution to broadcast to all except the sender was found on:  https://stackoverflow.com/questions/61828894/spring-websocket-how-to-send-to-all-subscribers-except-the-message-sender
  /**
   * Broadcasts the payload to all users except the given one (usually the sender).
   *
   * @param destination The target user specific destination to which receivers must have subscribed before. Note that
   *          this must start with "/queue/" and not with "/topic/". The user's subscription must start with
   *          "/user/queue/".
   * @param payload The payload to send to all users except the given one.
   * @param principal The principal of the user to which the message is not sent.
   */
  protected void broadcastToOthers(
	String destination,
	Object payload,
	Principal principal)
  {
	String user = principal != null ? principal.getName() : null;
	if ( user != null )
	{
	  List<String> subscribers = _simpUserRegistry
		  .getUsers()
		  .stream()
		  .map(SimpUser::getName)
		  .filter(name -> !user.equals(name))
		  .toList();

	  LOG.debug("broadcastToOthers({}, {}, {}). Sending to {}", destination, payload, user, subscribers);
	  subscribers.forEach(sub -> _simpMessagingTemplate.convertAndSendToUser(sub, destination, payload));
	}
	else
	{
	  LOG
	  .warn(
		"broadCastToOthers: No user name was found in the principal {}. Sending to all subscribers of {}",
		principal,
		destination);
	  _simpMessagingTemplate.convertAndSend(destination, payload);
	}
  }
}

