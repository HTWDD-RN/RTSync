package de.dmos.rtsync.server.project;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import de.dmos.rtsync.listeners.LocalProjectListener;
import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.network.CommunicationConstants;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.project.ProjectPathUtil;
import de.dmos.rtsync.project.RTProjectData;
import de.dmos.rtsync.server.AbstractRTSyncServerController;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

@Controller
public class RTSyncProjectServerController extends AbstractRTSyncServerController
implements
ProjectServerNetworkHandler,
LocalProjectListener<RTProjectData>
{
  private static final Logger				 LOG							 =
	  LoggerFactory.getLogger(RTSyncProjectServerController.class);

  private static final String				 PROJECT_VARIABLE				 = "{project}";
  private static final String				 PROJECT_PATH_VARIABLE			 =
	  EndpointPaths.DELIMITER + PROJECT_VARIABLE;
  private static final String				 TOPIC_PATH						 =
	  EndpointPaths.TOPIC + EndpointPaths.DELIMITER;
  private static final String				 TOPIC_PROJECT_OPERATIONS_FORMAT =
	  TOPIC_PATH + "%s" + EndpointPaths.PATH_OPERATIONS;
  private static final String				 QUEUE_PROJECT_EXCEPTION_FORMAT	 =
	  EndpointPaths.QUEUE + EndpointPaths.DELIMITER + "%s" + EndpointPaths.PATH_EXCEPTION;

  private final RTProjectServerOperationSync _serverSync;

  private boolean							 _autoCloseProjects				 = true;

  @Autowired
  public RTSyncProjectServerController(
	SimpUserRegistry simpUserRegistry,
	SimpMessagingTemplate simpMessagingTemplate)
  {
	super(simpUserRegistry, simpMessagingTemplate);
	_serverSync = new RTProjectServerOperationSync(this);
	_serverSync.addWeakLocalProjectListener(this);
  }

  @EventListener
  public void onUnsubscribeEvent(SessionUnsubscribeEvent unsubscribeEvent)
  {
	Subscriber unsubscriber = getSubscriber(unsubscribeEvent.getUser());
	String simpSessionId = (String) unsubscribeEvent.getMessage().getHeaders().get("simpSessionId");

	List<String> usersRemainingProjectSubsciptions = _simpUserRegistry
		.findSubscriptions(s -> s.getSession().getId().equals(simpSessionId))
		.stream()
		.filter(s -> s.getDestination().endsWith(EndpointPaths.PATH_ALL_TOPICS))
		.map(s -> ProjectPathUtil.getProjectFromDestination(s.getDestination()))
		.toList();
	Set<String> removedProjectSubscriptions = new HashSet<>(
		unsubscriber.getProjects().stream().filter(p -> !usersRemainingProjectSubsciptions.contains(p)).toList());
	removedProjectSubscriptions.forEach(unsubscriber::removeProject);
	SwingUtilities.invokeLater(() -> {
	  removedProjectSubscriptions.forEach(this::broadCastProjectSubscribers);
	  autoCloseProjectsIfSuitable(removedProjectSubscriptions);
	});
  }

  @Override
  public void onDisconnectEvent(SessionDisconnectEvent event)
  {
	super.onDisconnectEvent(event);
	Subscriber subscriber = getSubscriber(event.getUser());
	SwingUtilities.invokeLater(() -> autoCloseProjectsIfSuitable(subscriber.getProjects()));
  }

  @Override
  public void handleException(Throwable throwable, String project, String sender)
  {
	String message = logExceptionAndGetMessage(throwable);
	_simpMessagingTemplate.convertAndSendToUser(sender, getProjectExceptionEndpoint(project), message);
  }

  @MessageMapping(PROJECT_PATH_VARIABLE + EndpointPaths.PATH_UPDATE_OWN_CURSORS)
  @SendTo(EndpointPaths.TOPIC + PROJECT_PATH_VARIABLE + EndpointPaths.PATH_CURSORS)
  public UserCursors setCursors(
	@DestinationVariable("project") String project,
	@Payload CursorPosition cursorPosition,
	Principal principal)
  {
	// TODO: Store this information somewhere and attach it to StateMessages.
	return new UserCursors(getSubscriber(principal).getId(), List.of(cursorPosition));
	// It would be more professionell to broadcast it only to thers like this:
	//	broadcastToOthers(EndpointPaths.TOPIC_CURSORS, toBroadcast, principal);
  }

  @MessageMapping(PROJECT_PATH_VARIABLE + EndpointPaths.PATH_SEND_OPERATION)
  public void sendOperation(
	@DestinationVariable("project") String project,
	@Payload TaggedOperation<Operation<CombinedHandler>> taggedOp,
	Principal principal)
  {
	RTProjectData data = _serverSync.getOrCreateRTProjectData(project);
	long currentHistoryId = data.getLatestVersion();
	if ( _rejectOldHistoryIds && taggedOp.getHistoryId() < currentHistoryId )
	{
	  LOG
	  .info(
		"{} called {}/sendOperation({}) with an older historyId than {}",
		principal.getName(),
		project,
		taggedOp,
		currentHistoryId);
	  String oldIdMessage = getOldHistoryIdMessage(taggedOp, currentHistoryId);
	  _simpMessagingTemplate
	  .convertAndSendToUser(
		principal.getName(),
		QUEUE_PROJECT_EXCEPTION_FORMAT.formatted(project),
		oldIdMessage);
	  return;
	}
	// Maybe we should send the user's id instead of the their principal's name.
	TaggedOperation<Operation<CombinedHandler>> userOp = new TaggedUserOperation(taggedOp, principal.getName());
	LOG.debug("sendOperation({})", userOp);
	_serverSync.send(data, userOp);
  }

  private String getProjectExceptionEndpoint(String project)
  {
	return EndpointPaths.QUEUE + EndpointPaths.DELIMITER + project + EndpointPaths.QUEUE_EXCEPTION;
  }

  @SubscribeMapping(PROJECT_PATH_VARIABLE + EndpointPaths.PATH_GET_LATEST_OPERATION)
  public TaggedUserOperation getLatestOperation(@DestinationVariable("project") String project)
  {
	LOG.debug("{}/getLatestOperation called", project);
	return _serverSync.getLatestUserOperation(project);
  }

  @SubscribeMapping(EndpointPaths.PATH_INIT_CLIENT)
  public Subscriber initClient(Principal principal)
  {
	LOG.debug("{} called initClient", principal.getName());
	return getSubscriber(principal);
  }

  @Override
  public void brodcastTaggedOperation(String project, TaggedUserOperation taggedOperation)
  {
	LOG.debug("broadcastTaggedOperation({})", taggedOperation);
	_simpMessagingTemplate.convertAndSend(TOPIC_PROJECT_OPERATIONS_FORMAT.formatted(project), taggedOperation);
  }

  public void broadCastProjectSubscribers(String project)
  {
	_simpMessagingTemplate
	.convertAndSend(
	  TOPIC_PATH + project + EndpointPaths.PATH_SUBSCRIBERS,
	  getProjectsSubscribers(project));
  }

  private Subscriber[] getProjectsSubscribers(String project)
  {
	return getSubscriberStream().filter(s -> s.getProjects().contains(project)).toArray(Subscriber[]::new);
  }

  public String[] getProjectNames()
  {
	Set<String> projects = _serverSync.getLocalProjectNames();
	return projects.toArray(new String[projects.size()]);
  }

  public void broadCastProjectList()
  {
	_simpMessagingTemplate.convertAndSend(EndpointPaths.TOPIC_PROJECTS, getProjectNames());
  }

  @SubscribeMapping(EndpointPaths.PROJECTS)
  public String[] queryProjects()
  {
	return getProjectNames();
  }

  @SubscribeMapping(PROJECT_VARIABLE + EndpointPaths.PATH_ALL_TOPICS)
  public RTState subscribeToProject(
	@DestinationVariable("project") String project,
	Principal principal,
	MessageHeaders headers)
  {
	LOG.debug("{} is subscribing to project {}", principal.getName(), project);

	String sessionId = (String) headers.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
	String subscriptionId = (String) headers.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
	if ( sessionId == null || subscriptionId == null )
	{
	  LOG
	  .warn(
		"{} sent {}:{} and {}:{}. Both must not be null for subscriptions, so no subscription is registered!",
		principal.getName(),
		SimpMessageHeaderAccessor.SESSION_ID_HEADER,
		sessionId,
		SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER,
		subscriptionId);

	  _simpMessagingTemplate
	  .convertAndSendToUser(
		principal.getName(),
		getProjectExceptionEndpoint(project),
		CommunicationConstants.SERVER_EXCEPTION_MESSAGE_START
		+ "simpSessionId and simpSubscriptionId must not be null");
	}
	else
	{
	  Subscriber subscriber = getSubscriber(principal);
	  subscriber.addProject(project);
	  // TODO: check if replaceable by _simpMessagingTemplate.send(thisMessage)
	  subscribe(sessionId, subscriptionId, TOPIC_PATH + project + EndpointPaths.PATH_ALL_TOPICS);

	  broadCastProjectSubscribers(project);
	}

	return new RTState(null, getLatestOperation(project));
  }

  @Override
  public void localProjectCreated(RTProjectData project)
  {
	broadCastProjectList();
  }

  @Override
  public void localProjectClosed(RTProjectData project)
  {
	broadCastProjectList();
  }

  public void setAutoCloseProjects(boolean autoCloseProjects)
  {
	_autoCloseProjects = autoCloseProjects;
	autoCloseProjectsIfSuitable(null);
  }

  public boolean isAutoCloseProjects()
  {
	return _autoCloseProjects;
  }

  /**
   * Closes projects which no client has subscribed to if _autoCloseProjects is set.
   *
   * @param projectsToCheck An optional set of projects to potentially close. If null, then all opened projects are
   *          checked.
   */
  private void autoCloseProjectsIfSuitable(Set<String> projectsToCheck)
  {
	if ( !_autoCloseProjects )
	{
	  return;
	}
	Set<String> neededProjects = new HashSet<>(
		_simpUserRegistry
		.getUsers()
		.parallelStream()
		.map(u -> getSubscriber(u.getPrincipal()))
		.flatMap(s -> s.getProjects().stream())
		.distinct()
		.toList());
	if ( projectsToCheck == null )
	{
	  projectsToCheck = _serverSync.getLocalProjectNames();
	}
	LOG.trace("projects to check: {}\nneeded projects: {}", projectsToCheck, neededProjects);
	projectsToCheck.stream().filter(p -> !neededProjects.contains(p)).forEach(_serverSync::closeProject);
  }
}
