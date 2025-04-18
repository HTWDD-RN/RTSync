package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sksamuel.diffpatch.DiffMatchPatch;

import de.dmos.rtsync.client.SubscriptionSupplier;
import de.dmos.rtsync.customotter.ChangeListener;
import de.dmos.rtsync.customotter.CustomSharedList;
import de.dmos.rtsync.customotter.CustomSharedMap;
import de.dmos.rtsync.customotter.CustomSharedString;
import de.dmos.rtsync.customotter.SharedObjectContainer;
import de.dmos.rtsync.listeners.SelfSubscriberListener;
import de.dmos.rtsync.listeners.SelfSubscriptionSupplier;
import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.listeners.UserIdAndColorListener;
import de.dmos.rtsync.message.Subscriber;
import jakarta.annotation.PreDestroy;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.list.ListHandler;
import se.l4.otter.operations.map.MapHandler;

/**
 * A class that encapsulates required functionality to draw colored text for {@link SharedObjectContainer}s depending on
 * their modifying user's colors in order to display changes to it in real time.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class RTSharedObjectContainerAdapter implements SelfSubscriberListener, SubscriberListener
{
  static final Color										DEFAULT_COLOR	   =
	  RTSharedTextComponentAdapter.DEFAULT_COLOR;

  private final UserIdAndColorListener						_userIdAndColorListener;
  private final Map<SharedObjectContainer<?>, SharedObjectContainerListener<?>>	_listeners	  = new HashMap<>();
  private final SharedObjectContainerUpdater				_containerUpdater;

  private SubscriptionSupplier									 _subscriptionSupplier;

  RTSharedObjectContainerAdapter(
	SharedObjectContainerUpdater containerUpdater,
	SelfSubscriptionSupplier selfSubscriptionSupplier)
  {
	this(containerUpdater, selfSubscriptionSupplier, null);
  }

  RTSharedObjectContainerAdapter(
	SharedObjectContainerUpdater containerUpdater,
	SelfSubscriptionSupplier selfSubscriptionSupplier,
	SubscriptionSupplier subscriptionSupplier)
  {
	_userIdAndColorListener = new UserIdAndColorListener(selfSubscriptionSupplier, subscriptionSupplier);
	_containerUpdater = containerUpdater;
	selfSubscriptionSupplier.addSelfSubscriberListener(this);
	setSubscriptionSupplier(subscriptionSupplier);
  }

  public void setSubscriptionSupplier(SubscriptionSupplier subscriptionSupplier)
  {
	_listeners.forEach((c, s) -> s.stopListening());
	_listeners.clear();
	_userIdAndColorListener.setSubscriptionSupplier(subscriptionSupplier);

	if ( _subscriptionSupplier != null )
	{
	  _subscriptionSupplier.removeWeakSubscriberListener(this);
	}
	_subscriptionSupplier = subscriptionSupplier;
	if ( subscriptionSupplier != null )
	{
	  subscriptionSupplier.addWeakSubscriberListener(this);
	}
  }

  @Override
  public void onOwnNameOrColorChanged(Subscriber subscriber)
  {
	Subscriber selfSubscriber = _userIdAndColorListener.getSelfSubscriber();
	boolean removed = false;
	if ( selfSubscriber != null )
	{
	  for ( SharedObjectContainerListener<?> listener : _listeners.values() )
	  {
		removed = listener.getInsertions().removeIf(ins -> userMatchesSubscriber(ins, selfSubscriber)) || removed;
	  }
	}
	if ( removed )
	{
	  _containerUpdater.updateAllColors();
	}
  }

  @Override
  public void onSubscribersReceived(List<Subscriber> subscribers)
  {
	if ( subscribers == null )
	{
	  return;
	}

	boolean changed = false;
	for ( SharedObjectContainerListener<?> listener : _listeners.values() )
	{
	  changed = listener.updateInsertionColors() || changed;
	}
	if ( changed )
	{
	  _containerUpdater.updateAllColors();
	}
  }

  public boolean userMatchesSubscriber(Selection sel, Subscriber subscriber)
  {
	return subscriber.getName().equals(sel.getUser());
  }

  public void listenToChanges(SharedObjectContainer<?> container, boolean recursive)
  {
	if ( container instanceof CustomSharedMap map )
	{
	  listenToChanges(map, recursive);
	}
	else if ( container instanceof CustomSharedList<?> list )
	{
	  listenToChanges(list, recursive);
	}
  }

  public void listenToChanges(CustomSharedMap map, boolean recursive)
  {
	_listeners.computeIfAbsent(map, m -> new MapChangeListener(map));
	listenToChildrenIfRecursive(map, recursive);
  }

  public void listenToChanges(CustomSharedList<?> list, boolean recursive)
  {
	_listeners.computeIfAbsent(list, l -> new ListChangeListener(list));
	listenToChildrenIfRecursive(list, recursive);
  }

  private void listenToChildrenIfRecursive(SharedObjectContainer<?> container, boolean recursive)
  {
	if ( recursive )
	{
	  container.getContents().forEach(c -> {
		if (c instanceof SharedObjectContainer<?> childContainer) {
		  listenToChanges(childContainer, recursive);
		}
	  });
	}
  }

  public List<Insertion> getInsertions(SharedObjectContainer<?> container)
  {
	SharedObjectContainerListener<?> listener = _listeners.get(container);
	return listener != null ? listener.getInsertions() : List.of();
  }

  public static interface SharedObjectContainerUpdater
  {
	Selection getSelectionInContainer(SharedObjectContainer<?> container);

	void updateContainer(SharedObjectContainer<?> container, Selection selection, Set<SharedObjectContainer<?>> removedDescendantContainers);

	void updateAllColors();
  }

  private abstract class SharedObjectContainerListener<T extends Operation<?>> implements ChangeListener<T>
  {
	private final SharedObjectContainer<T> _container;
	private List<Insertion>				   _insertions;
	private boolean						   _listening;

	SharedObjectContainerListener(SharedObjectContainer<T> container)
	{
	  _container = container;
	  _insertions = new ArrayList<>();
	  container.addAsWeakChangeListener(this);
	  _listening = true;
	}

	public List<Insertion> getInsertions()
	{
	  return _insertions;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	protected void updateInsertionsAndSelection(
	  SharedObjectContainer container,
	  Operation op,
	  AbstractSharedObjectHandler handler)
	{
	  Selection selection = _containerUpdater.getSelectionInContainer(container);
	  handler.setCurrentSelection(selection);
	  if ( op == null )
	  {
		handler.reset();
	  }
	  else
	  {
		op.apply(handler);
	  }

	  selection = handler.getCurrentSelection();
	  updateInsertionColors();

	  _containerUpdater.updateContainer(container, selection, handler.getRemovedDescendantContainers());
	}

	private boolean updateInsertionColors()
	{
	  boolean changed = false;
	  for ( Insertion ins : _insertions )
	  {
		changed = _userIdAndColorListener.updateSelectionColor(ins, DEFAULT_COLOR) || changed;
	  }
	  return changed;
	}

	@PreDestroy
	private void stopListening()
	{
	  if ( _listening )
	  {
		_listening = false;
		_container.removeWeakChangeListener(this);
	  }
	}

	abstract class AbstractSharedObjectHandler
	{
	  protected final String	_user;
	  private Selection			_currentSelection;
	  protected int				_index;
	  private final Set<SharedObjectContainer<?>> _removedDescendantContainers;

	  protected AbstractSharedObjectHandler(String user, int index)
	  {
		_user = user;
		_index = index;
		_removedDescendantContainers = new HashSet<>();
	  }

	  public void setCurrentSelection(Selection currentSelection)
	  {
		_currentSelection = currentSelection;
	  }

	  public Selection getCurrentSelection()
	  {
		return _currentSelection;
	  }

	  protected void addInsertion()
	  {
		addInsertionAt(_index);
		_index++;
	  }

	  protected void addInsertionAt(int index)
	  {
		moveCursorsIfAppropriate(index, 1);
		if ( _user == null )
		{
		  return;
		}
		_insertions.add(new Insertion(index, 1, _user));
	  }

	  public Set<SharedObjectContainer<?>> getRemovedDescendantContainers()
	  {
		return _removedDescendantContainers;
	  }

	  /**
	   * Applies differential operations from the previous state to the new one.
	   *
	   * Maybe it would be good to extract Diff functionality in a similar way that {@link DiffMatchPatch} is used in
	   * {@link CustomSharedString}.
	   */
	  abstract void reset();

	  protected void removeIfContainer(Object obj)
	  {
		if ( obj instanceof SharedObjectContainer<?> container )
		{
		  SharedObjectContainerListener<?> listener = _listeners.remove(container);
		  if ( listener != null )
		  {
			listener.stopListening();
			_removedDescendantContainers.add(container);
			container.getContents().forEach(this::removeIfContainer);
		  }
		}
	  }

	  @SuppressWarnings("unchecked")
	  protected void moveCursorsIfAppropriate(int minIndex, int offset)
	  {
		if ( offset == 0 )
		{
		  return;
		}
		List<Insertion> movedInsertions = (List<Insertion>) _insertions
			.stream()
			.flatMap(i -> i.move(minIndex, offset).stream())
			.toList();
		_insertions = new ArrayList<>(movedInsertions); // This makes the list mutable.
		if ( _currentSelection != null )
		{
		  _currentSelection = _currentSelection.move(minIndex, offset).getFirst();
		}
	  }
	}
  }

  /**
   * A helper class that serves the purpose to store a {@link CustomSharedMap}s key indices in order to remove the
   * correct insertions when a map entry is deleted.
   *
   * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
   * @version $Rev$
   *
   */
  private class MapChangeListener extends SharedObjectContainerListener<Operation<MapHandler>>
  {
	private final CustomSharedMap _sharedMap;
	private Map<String, Object>	  _map;
	private List<String>		  _oldKeys;
	private List<String>		  _newKeys;

	MapChangeListener(CustomSharedMap sharedMap)
	{
	  super(sharedMap);
	  _sharedMap = sharedMap;
	  updateMap();
	}

	private void updateMap()
	{
	  _map = _sharedMap.toMap();
	  _oldKeys = new ArrayList<>(_sharedMap.sequencedKeySet());
	}

	@Override
	public void valueChanged(Operation<MapHandler> op, boolean local, String user)
	{
	  _newKeys = new ArrayList<>(_sharedMap.sequencedKeySet());
	  updateInsertionsAndSelection(_sharedMap, op, new MapChangeHandler(user));
	  updateMap();
	}

	private class MapChangeHandler extends AbstractSharedObjectHandler implements MapHandler
	{
	  MapChangeHandler(String user)
	  {
		super(user, _map.size());
	  }

	  @Override
	  public void put(String key, Object oldValue, Object newValue)
	  {
		remove(key, oldValue);
		addInsertionAt(_newKeys.indexOf(key));
	  }

	  @Override
	  public void remove(String key, Object oldValue)
	  {
		int index = _oldKeys.indexOf(key);
		if ( index > -1 )
		{
		  moveCursorsIfAppropriate(index, -1);
		  removeIfContainer(_map.get(key));
		}
	  }

	  @Override
	  void reset()
	  {
		List<String> deletedKeys = _oldKeys.stream().filter(k -> !_newKeys.contains(k)).toList();
		List<String> keptKeys = _oldKeys.stream().filter(k -> !deletedKeys.contains(k)).toList();

		deletedKeys.forEach(k -> remove(k, null));
		_index = keptKeys.size();
		_newKeys.stream().filter(k -> !keptKeys.contains(k)).forEach(k -> addInsertion());
	  }
	}
  }

  private class ListChangeListener extends SharedObjectContainerListener<Operation<ListHandler>>
  {
	private final CustomSharedList<?> _list;
	private List<?>					  _values;
	private int						  _insertedCount;

	ListChangeListener(CustomSharedList<?> list)
	{
	  super(list);
	  _list = list;
	  updateValueList();
	}

	private void updateValueList()
	{
	  _values = _list.toList();
	  _insertedCount = 0;
	}

	@Override
	public void valueChanged(Operation<ListHandler> op, boolean local, String user)
	{
	  user = _userIdAndColorListener.isSelfSubscriber(user) ? null : user;
	  updateInsertionsAndSelection(_list, op, new ListChangeHandler(user));
	  updateValueList();
	}

	private class ListChangeHandler extends AbstractSharedObjectHandler implements ListHandler
	{
	  ListChangeHandler(String user)
	  {
		super(user, 0);
	  }

	  @Override
	  public void retain(int length)
	  {
		_index += length;
	  }

	  @Override
	  public void insert(Object item)
	  {
		addInsertion();
		_insertedCount++;
	  }

	  @Override
	  public void delete(Object item)
	  {
		moveCursorsIfAppropriate(_index, -1);
		removeIfContainer(_values.get(_index - _insertedCount));
	  }

	  @Override
	  void reset()
	  {
		List<?> newValues = _list.toList();

		List<Object> matchingObjects = new LinkedList<>();
		for ( Object oldValue : _values )
		{
		  int indexInNewValues = newValues.indexOf(oldValue);
		  if (indexInNewValues < 0) {
			delete(oldValue);
		  } else {
			matchingObjects.add(oldValue);
			retain(1);
		  }
		}

		// List entries are checked only in the previous order.
		// All old entries that appear out of the new entries' order are considered to be replaced.
		_index = 0;
		for (Object obj: newValues) {
		  if ( obj == matchingObjects.getFirst() )
		  {
			matchingObjects.removeFirst();
			retain(1);
		  }
		  else
		  {
			insert(obj);
		  }
		}
		delete(matchingObjects.size());
	  }
	}
  }
}
