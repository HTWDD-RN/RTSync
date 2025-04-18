package de.dmos.rtsync.client;

import java.awt.Color;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.messaging.simp.stomp.StompSessionHandler;

import de.dmos.rtsync.client.ProjectAvailability.ProjectAvailabilityState;
import de.dmos.rtsync.client.internalinterfaces.ClientOperationSync;
import de.dmos.rtsync.client.internalinterfaces.OutgoingClientMessage;
import de.dmos.rtsync.client.internalinterfaces.ProjectStompSessionSync;
import de.dmos.rtsync.listeners.LocalProjectListener;
import de.dmos.rtsync.listeners.ProjectListListener;
import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.project.AbstractProjectOperationSync;
import de.dmos.rtsync.project.RTProjectData;
import de.dmos.rtsync.serializers.ProjectMessageSerialization;
import de.dmos.rtsync.util.WeakLinkedList;
import jakarta.annotation.PreDestroy;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public class RTProjectClientOperationSync extends AbstractProjectOperationSync<ClientRTProjectData>
implements
ClientOperationSync,
ProjectStompSessionSync,
LocalProjectListener<ClientRTProjectData>
{
  private static final Logger						   LOG					 =
	  LoggerFactory.getLogger(RTProjectClientOperationSync.class);
  private static final List<String>					   INITIAL_SUBSCRIPTIONS = List
	  .of(
		EndpointPaths.USER_QUEUE_EXCEPTION,
		EndpointPaths.TOPIC_SUBSCRIBERS,
		EndpointPaths.TOPIC_PROJECTS,
		EndpointPaths.APP_INIT_CLIENT);
  private static final String						   APP_PATH				 =
	  EndpointPaths.APP + EndpointPaths.DELIMITER;

  protected final BlockingQueue<OutgoingClientMessage> _messageQueue;
  protected final StompSessionHandler				   _stompSessionHandler;
  private final ClientConnectionHandler				   _connectionHandler;
  private final WeakLinkedList<ProjectListListener>	   _projectsListeners;

  private List<String>								   _projectList;
  private ClientSynchronizingThread					   _connectionThread;
  private ExecutorService							   _executorService		 = null;

  public RTProjectClientOperationSync(String preferredName, Color preferredColor)
  {
	super();
	_connectionHandler = new ClientConnectionHandler(this, preferredName, preferredColor);
	_messageQueue = new LinkedBlockingQueue<>();
	_stompSessionHandler = new ProjectStompSessionHandler(this);
	_projectsListeners = new WeakLinkedList<>();
	Runtime.getRuntime().addShutdownHook(new Thread(this::stopSynchronizingWithServer));
	addWeakLocalProjectListener(this);
  }

  protected ClientSynchronizingThread createSynchronizingThread(URI uri)
  {
	return new ClientSynchronizingThread(
	  uri,
	  _messageQueue,
	  _connectionHandler,
	  _stompSessionHandler,
	  ProjectMessageSerialization.getCombinedMessageConverter(),
	  INITIAL_SUBSCRIPTIONS);
  }

  @Override
  protected ClientRTProjectData createRTProjectData(String project)
  {
	return new ClientRTProjectData(project, this, position -> updateProjectCursor(project, position));
  }

  private void updateProjectCursor(String project, CursorPosition position)
  {
	_messageQueue.add(new BasicMessage(APP_PATH + project + EndpointPaths.PATH_UPDATE_OWN_CURSORS, position));
  }

  @Override
  public ClientSynchronizingThread getSyncThread()
  {
	return _connectionThread;
  }

  @Override
  public boolean isConnected()
  {
	return _connectionThread != null && _connectionThread.isConnected();
  }

  public void setExecutorService(ExecutorService executorService)
  {
	_executorService = executorService;
  }

  @Override
  public void close()
  {
	_messageQueue.clear();
	stopSynchronizingWithServer();
	super.close();
  }

  @Override
  public void startSynchronizingWithServer(URI uri)
  {
	terminateConnectionThreadIfPossible();

	_connectionThread = createSynchronizingThread(uri);
	try
	{
	  if ( _executorService != null )
	  {
		_executorService.submit(_connectionThread);
	  }
	  else
	  {
		_connectionThread.start();
	  }
	}
	catch (IllegalThreadStateException itse)
	{
	  itse.printStackTrace();
	}
	catch (Exception ex)
	{
	  ex.printStackTrace();
	}
  }

  @Override
  @PreDestroy
  public void stopSynchronizingWithServer()
  {
	terminateConnectionThreadIfPossible();
  }

  private void terminateConnectionThreadIfPossible()
  {
	if ( _connectionThread != null )
	{
	  _connectionThread.terminate();
	  _connectionThread = null;
	  if ( !ConnectionState.NOT_CONNECTED.equals(_connectionHandler.getConnectionState()) )
	  {
		_connectionHandler.onConnectionStateChanged(ConnectionState.NOT_CONNECTED, null);
	  }
	}
  }

  @Override
  public void onServerMessageException(StompHeaders headers, String exceptionMessage)
  {
	ClientRTProjectData data = getProjectData(headers);
	if ( data != null )
	{
	  onSynchronizationProblem(data, true);
	}
  }

  void onSynchronizationProblem(ClientRTProjectData clientRTProjectData, boolean queryLatest)
  {
	removeSendOperationsFromMessageQueue(clientRTProjectData.getProject());
	if ( queryLatest )
	{
	  queryLatestProjectOperation(clientRTProjectData.getProject());
	}
  }

  private void queryLatestProjectOperation(String project)
  {
	_messageQueue.add(new SubscribeMessage(APP_PATH + project + EndpointPaths.PATH_GET_LATEST_OPERATION));
  }

  private void removeSendOperationsFromMessageQueue(String project)
  {
	_messageQueue
	.removeIf(
	  m -> m instanceof BasicMessage basicMessage
	  && basicMessage.destination().endsWith(project + EndpointPaths.PATH_SEND_OPERATION));
  }

  private String getProjectFromHeaders(StompHeaders headers)
  {
	String destination = headers.getDestination();
	if ( destination == null )
	{
	  return null;
	}
	String[] headerParts = destination.split(EndpointPaths.DELIMITER);
	if ( headerParts.length < 2 )
	{
	  LOG.error("Could not get project from destination in headers. {}", headers);
	  return null;
	}
	return headerParts[headerParts.length - 2];
  }

  private ClientRTProjectData getProjectData(StompHeaders headers)
  {
	return getProjectData(getProjectFromHeaders(headers));
  }

  @Override
  public void onSubscribersReceived(StompHeaders headers, List<Subscriber> subscribers)
  {
	if ( !EndpointPaths.TOPIC_SUBSCRIBERS.equals(headers.getDestination()) )
	{
	  ClientRTProjectData data = getProjectData(headers);
	  if ( data != null )
	  {
		data.getSubscriptionContainer().onSubscribersReceived(subscribers);
	  }
	  return;
	}

	// At this point, subscribers refers to the complete list of subscribers. It was probably sent because a client disconnected
	// or changed their name or color. Old subscribers must be removed or updated but no new subscribers must be inserted.
	for ( Entry<String, ClientRTProjectData> entry : _projectData.entrySet() )
	{
	  ClientSubscriptionContainer subscriptionContainer = entry.getValue().getSubscriptionContainer();
	  List<Subscriber> projectSubscribers = subscriptionContainer.getSubscribers();
	  if ( projectSubscribers == null )
	  {
		continue;
	  }
	  List<Subscriber> targetProjectSubscribers =
		subscribers.stream().filter(s -> s.getProjects().contains(entry.getKey())).toList();
	  if ( !projectSubscribers.containsAll(targetProjectSubscribers) )
	  {
		subscriptionContainer.onSubscribersReceived(projectSubscribers);
	  }
	}
  }

  @Override
  public void onCursorsMessageReceived(StompHeaders headers, UserCursors cursorsMessage)
  {
	ClientRTProjectData data = getProjectData(headers);
	if ( data != null )
	{
	  data.getSubscriptionContainer().onUserCursorsReceived(cursorsMessage);
	}
  }

  @Override
  public void onProjectStateMessageReceived(StompHeaders headers, RTState projectStateMessage)
  {
	String project = getProjectFromHeaders(headers);
	ClientRTProjectData data = getOrCreateRTProjectData(project);
	if ( data == null )
	{
	  return;
	}

	data.onTaggedOperationReceived(projectStateMessage.taggedOperation(), true);
	ClientSubscriptionContainer subContainer = data.getSubscriptionContainer();
	if ( projectStateMessage.userCursors() != null )
	{
	  projectStateMessage.userCursors().forEach(subContainer::onUserCursorsReceived);
	}
  }

  @Override
  public void onTaggedOperationReceived(StompHeaders headers, TaggedUserOperation userOp, boolean isWholeState)
  {
	ClientRTProjectData data = getOrCreateRTProjectData(getProjectFromHeaders(headers));
	if ( data != null )
	{
	  data.onTaggedOperationReceived(userOp, isWholeState);
	}
  }

  @Override
  public void onSelfSubscriberReceived(StompHeaders headers, Subscriber selfSubscriber)
  {
	_connectionHandler.onOwnNameOrColorChanged(selfSubscriber);
	_connectionHandler.onConnectionStateChanged(ConnectionState.CONNECTED, null);
	if ( _projectsListeners.hasNoStrongContents() )
	{
	  queryProjects();
	}
  }

  @Override
  public ClientConnectionHandler getConnectionHandler()
  {
	return _connectionHandler;
  }

  @Override
  protected boolean isServer()
  {
	return false;
  }

  @Override
  public void send(RTProjectData data, TaggedOperation<Operation<CombinedHandler>> op)
  {
	_messageQueue.add(new BasicMessage(APP_PATH + data.getProject() + EndpointPaths.PATH_SEND_OPERATION, op));
  }

  @Override
  public void onException(Throwable throwable)
  {
	_connectionHandler.onException(throwable);
  }

  @Override
  public void onException(StompHeaders headers, Throwable exception)
  {
	String project = getProjectFromHeaders(headers);
	queryLatestProjectOperation(project);
  }

  /**
   * Gets the list of projects names which was last received from the server. This may be empty or null.
   */
  public List<String> getServerProjectNameList()
  {
	return _projectList;
  }

  @Override
  public void onProjectListUpdated(List<String> projects)
  {
	_projectList = projects;
	_projectsListeners.toStrongStream().forEach(l -> l.onProjectListReceived(projects));
  }

  public void addWeakProjectListListener(ProjectListListener listener)
  {
	_projectsListeners.addAsWeakReference(listener);
  }

  public boolean removeProjectListListener(ProjectListListener listener)
  {
	return _projectsListeners.removeReferencedObject(listener);
  }

  public List<ProjectAvailability> getProjectAvailabilities()
  {
	Set<String> localProjects = _projectData.keySet();
	if ( _projectList == null )
	{
	  return localProjects.stream().map(p -> new ProjectAvailability(p, ProjectAvailabilityState.LOCAL)).toList();
	}

	List<ProjectAvailability> list = new ArrayList<>();
	list.addAll(localProjects.stream().map(this::toLocalProjectAvailability).toList());
	list
	.addAll(
	  _projectList
	  .stream()
	  .filter(p -> !localProjects.contains(p))
	  .map(p -> new ProjectAvailability(p, ProjectAvailabilityState.REMOTE))
	  .toList());

	return list;
  }

  private ProjectAvailability toLocalProjectAvailability(String project)
  {
	return new ProjectAvailability(
	  project,
	  _projectList.contains(project) ? ProjectAvailabilityState.LOCAL_AND_REMOTE : ProjectAvailabilityState.LOCAL);
  }

  @Override
  public void localProjectCreated(ClientRTProjectData data)
  {
	_messageQueue
	.add(
	  new SubscribeMessage(
		APP_PATH + data.getProject() + EndpointPaths.PATH_ALL_TOPICS,
		data.getSubscriptionContainer()));
  }

  @Override
  public void localProjectClosed(ClientRTProjectData project)
  {
	Subscription subscription = project.getSubscriptionContainer().getSubscription();
	if ( subscription != null )
	{
	  _messageQueue.add(new UnsubscribeMessage(subscription));
	}
  }

  /**
   * Queries a list of projects from the server. Note, that this is only done automatically, if there is at least one
   * {@link ProjectListListener}. However, updates to the server's project list are automatically received by the
   * client.
   */
  public void queryProjects()
  {
	_messageQueue.add(new SubscribeMessage(EndpointPaths.APP_QUERY_PROJECTS));
  }
}
