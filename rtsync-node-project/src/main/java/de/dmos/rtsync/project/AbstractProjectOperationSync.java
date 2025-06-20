package de.dmos.rtsync.project;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.dmos.rtsync.internalinterfaces.TaggedUserOperationListener;
import de.dmos.rtsync.listeners.LocalProjectListener;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.util.WeakLinkedList;
import se.l4.otter.engine.OperationSync;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.TransformException;
import se.l4.otter.operations.combined.CombinedHandler;

/**
 * Base class for project based equivalents of {@link OperationSync}s
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public abstract class AbstractProjectOperationSync<T extends RTProjectData> implements AutoCloseable
{
  protected Map<String, T> _projectData = new ConcurrentHashMap<>();
  protected final WeakLinkedList<LocalProjectListener<T>> _localProjectListeners = new WeakLinkedList<>();

  private boolean										  _newProjectCreated		= false;

  public TaggedUserOperation connectUserOperationListener(String project, TaggedUserOperationListener listener)
  {
	RTProjectData data = getOrCreateRTProjectData(project);
	data.connectUserOperationListener(listener);
	return data.getLatestUserOperation();
  }

  public TaggedUserOperation getLatestUserOperation(String project)
  {
	return getOrCreateRTProjectData(project).getLatestUserOperation();
  }

  public T getProjectData(String project)
  {
	return _projectData.get(project);
  }

  public void addWeakLocalProjectListener(LocalProjectListener<T> listener)
  {
	_localProjectListeners.addAsWeakReference(listener);
  }

  public boolean removeWeakLocalProjectListener(LocalProjectListener<T> listener)
  {
	return _localProjectListeners.removeReferencedObject(listener);
  }

  public boolean containsProject(String project)
  {
	return _projectData.containsKey(project);
  }

  /**
   * This is the way, local projects are supposed to be opened.
   */
  public synchronized T getOrCreateRTProjectData(String project)
  {
	T data = _projectData.computeIfAbsent(project, p -> {
	  _newProjectCreated = true;
	  return createRTProjectData(p);
	});
	if ( _newProjectCreated )
	{
	  _newProjectCreated = false;
	  _localProjectListeners.toStrongStream().forEach(l -> l.localProjectCreated(data));
	}
	return data;
  }

  protected abstract T createRTProjectData(String project);

  protected abstract boolean isServer();

  public abstract void send(RTProjectData data, TaggedOperation<Operation<CombinedHandler>> op);

  /**
   * Stores the received operation and notifies local listeners of the store result. This should be called when a
   * network node receives a {@link TaggedOperation} via the network.
   */
  public void onTaggedOperationReceived(String project, TaggedUserOperation userOp, boolean wholeState)
	  throws TransformException
  {
	T data = getOrCreateRTProjectData(project);
	data.onTaggedOperationReceived(userOp, wholeState);
  }

  @Override
  public void close()
  {
	Set<String> oldProjects = _projectData.keySet();
	_projectData = new ConcurrentHashMap<>();
	oldProjects.forEach(this::closeProject);
  }

  public T closeProject(String project)
  {
	T closed = _projectData.remove(project);
	if ( closed != null )
	{
	  _localProjectListeners.toStrongStream().forEach(l -> l.localProjectClosed(closed));
	}
	return closed;
  }

  public Set<String> getLocalProjectNames()
  {
	return _projectData.keySet();
  }
}
