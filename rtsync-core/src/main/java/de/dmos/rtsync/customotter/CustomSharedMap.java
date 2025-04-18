package de.dmos.rtsync.customotter;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import se.l4.otter.EventHelper;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.SharedMap;
import se.l4.otter.model.internal.SharedMapImpl;
import se.l4.otter.model.spi.DataValues;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.OperationException;
import se.l4.otter.operations.map.MapDelta;
import se.l4.otter.operations.map.MapHandler;

/**
 * A copy of {@link SharedMapImpl} that also exposes the key set. The copy was needed because of the private map values.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class CustomSharedMap extends SharedObjectContainer<Operation<MapHandler>> implements SharedMap
{
  private final SortedMap<String, Object> _values;
  private final MapHandler            _handler;
  private final EventHelper<Listener> _mapChangeListeners;

  public CustomSharedMap(CustomSharedObjectEditor<Operation<MapHandler>> editor)
  {
	super(editor);

	_values = new TreeMap<>();
	_mapChangeListeners = new EventHelper<>();
	_handler = createHandler();

	editor.getCurrent().apply(_handler);
	editor.setOperationHandler(this);
  }

  private MapHandler createHandler()
  {
	return new MapHandler() {
	  @Override
	  public void remove(String key, Object oldValue)
	  {
		Object old = _values.remove(key);
		editor.queueEvent(() -> _mapChangeListeners.trigger(l -> l.valueRemoved(key, old)));
	  }

	  @Override
	  public void put(String key, Object oldValue, Object newValue)
	  {
		Object value = DataValues.fromData(editor, newValue);
		Object old = _values.put(key, value);
		editor.queueEvent(() -> _mapChangeListeners.trigger(l -> l.valueChanged(key, old, value)));
	  }
	};
  }

  public SequencedSet<String> sequencedKeySet()
  {
	return _values.sequencedKeySet();
  }

  @Override
  public String getObjectId()
  {
	return editor.getId();
  }

  @Override
  public String getObjectType()
  {
	return editor.getType();
  }

  @Override
  public boolean containsKey(String key)
  {
	return _values.containsKey(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(String key)
  {
	return (T) _values.get(key);
  }

  @Override
  public void remove(String key)
  {
	try (CloseableLock lock = editor.lock())
	{
	  applyRemoveToEditor(key);
	}
  }

  @Override
  public boolean removeObject(Object obj)
  {
	try (CloseableLock lock = editor.lock())
	{
	  Optional<Entry<String, Object>> entry = _values.entrySet().stream().filter(e -> e.getValue() == obj).findAny();
	  if (entry.isEmpty()) {
		return false;
	  }
	  applyRemoveToEditor(entry.get().getKey());
	}
	return true;
  }

  private Operation<MapHandler> applyRemoveToEditor(String key)
  {
	Operation<MapHandler> mapOp;
	Object value = _values.get(key);
	mapOp = MapDelta.builder().set(key, DataValues.toData(value), null).done();
	editor.apply(mapOp);
	return mapOp;
  }

  @Override
  public void set(String key, Object value)
  {
	if ( value == null )
	{
	  throw new IllegalArgumentException("null values are currently not supported");
	}

	Operation<MapHandler> mapOp;
	try (CloseableLock lock = editor.lock())
	{
	  Object old = _values.get(key);
	  mapOp = MapDelta.builder().set(key, DataValues.toData(old), DataValues.toData(value)).done();
	  editor.apply(mapOp);
	}
  }

  @Override
  public void addChangeListener(Listener listener)
  {
	_mapChangeListeners.add(listener);
  }

  @Override
  public void removeChangeListener(Listener listener)
  {
	_mapChangeListeners.remove(listener);
  }

  /**
   * Creates a shallow copy of the underlying map.
   */
  public SortedMap<String, Object> toMap()
  {
	return new TreeMap<>(_values);
  }

  @Override
  public void apply(Operation<MapHandler> op, boolean local)
  {
	op.apply(_handler);
  }

  private class MapResetHandler implements MapHandler
  {
	Set<String> _updatedKeys = new HashSet<>();

	Set<String> getUpdatedKeys()
	{
	  return _updatedKeys;
	}

	@Override
	public void remove(String key, Object oldValue)
	{
	  throw new OperationException("Latest value invalid, must only contain inserts (puts).");
	}

	@Override
	public void put(String key, Object oldValue, Object newValue)
	{
	  _updatedKeys.add(key);
	  Object value = DataValues.fromData(editor, newValue);
	  Object old = _values.put(key, value);
	  //	  setContainerIfPossible(value);
	  if ( !Objects.equals(old, value) )
	  {
		editor.queueEvent(() -> _mapChangeListeners.trigger(l -> l.valueChanged(key, old, value)));
	  }
	}
  }

  @Override
  public void onReset()
  {
	try (CloseableLock lock = editor.lock())
	{
	  MapResetHandler resetHandler = new MapResetHandler();
	  editor.getCurrent().apply(resetHandler);
	  Set<String> updatedKeys = resetHandler.getUpdatedKeys();
	  _values
	  .entrySet()
	  .stream()
	  .filter(e -> !updatedKeys.contains(e.getKey()))
	  .forEach(e -> _handler.remove(e.getKey(), e.getValue()));
	}
  }

  @Override
  public Object getObjectAtPathPart(String pathPart) throws IllegalArgumentException
  {
	Object obj = get(pathPart);
	if ( obj == null )
	{
	  throw new IllegalArgumentException("No object at " + pathPart);
	}
	return obj;
  }

  @Override
  public void insertObjectAtPathPart(Object obj, String pathPart)
	  throws IndexOutOfBoundsException, ClassCastException
  {
	set(pathPart, obj);
  }

  @Override
  public Stream<Object> getContents()
  {
	return _values.values().stream();
  }
}
