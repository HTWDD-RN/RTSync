package de.dmos.rtsync.customotter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.l4.otter.engine.events.ChangeEvent;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.DefaultModel;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedObject;
import se.l4.otter.model.spi.SharedObjectEditor;
import se.l4.otter.model.spi.SharedObjectFactory;
import se.l4.otter.operations.CompoundOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.OperationException;
import se.l4.otter.operations.combined.CombinedDelta;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.combined.CombinedType;

public class CustomModel implements Model, CustomEditorListener<Operation<CombinedHandler>>
{
  private static final String							 ROOT = "root";

  private static final Logger							 LOG = LoggerFactory.getLogger(CustomModel.class);

  private final CustomEditor<Operation<CombinedHandler>> _editor;
  private final CombinedType							 _otType;
  private final Map<String, SharedObjectFactory<?, ?>>	 _types;
  private final boolean									 _resetOnOperationException;
  private final CombinedHandler							 _handler;

  protected Map<String, ModelObjectData<?>>				 _modelDataMap;

  private int											 _latestId;
  private final CustomSharedMap							 _root;
  private final List<Runnable>							 _queuedEvents;
  private int											 _lockDepth;
  private CloseableLock									 _lock;
  private String										 _currentOperationsUser;

  /**
   * Create a new model over the given editor.
   */
  public CustomModel(
	CustomEditor<Operation<CombinedHandler>> editor,
	Map<String, SharedObjectFactory<?, ?>> types,
	boolean resetOnOperationException)
  {
	_editor = editor;
	_types = types;
	_otType = (CombinedType) editor.getType();
	_queuedEvents = new ArrayList<>();
	_modelDataMap = new ConcurrentHashMap<>();
	_resetOnOperationException = resetOnOperationException;
	_handler = createHandler();

	try (CloseableLock lock = editor.lock())
	{
	  Operation<CombinedHandler> current = editor.getCurrent();
	  if ( current != null )
	  {
		current.apply(_handler);
	  }

	  editor.addListener(this);
	}

	_root = (CustomSharedMap) getObject(ROOT, "map");
  }

  @Override
  public void editorChanged(ChangeEvent<Operation<CombinedHandler>> event)
  {
	if ( event.isRemote() )
	{
	  try (CloseableLock lock = lock())
	  {
		_currentOperationsUser = event instanceof UserChangeEvent userEvent ? userEvent.getUser() : null;
		/*
		 * Perform operation in a lock to ensure that events
		 * are triggered after the apply has finished.
		 */
		event.getOperation().apply(_handler);
	  }
	}
  }

  @Override
  public void onReset(Operation<CombinedHandler> op, String user)
  {
	try (CloseableLock lock = lock())
	{
	  _currentOperationsUser = user;
	  ModelResetHandler resetHandler = new ModelResetHandler();
	  op.apply(resetHandler);

	  removeOtherModelData(resetHandler.getUpdatedKeys());
	}
  }

  public CustomSharedMap getRoot()
  {
	return _root;
  }

  /**
   * Removes the model object data by the given keys. The removal of old entries synchronizes this model with the
   * server's one. If the Removed model objects must then be reinserted if wanted and the server will then be notified
   * about the insertion. If old model objects are supposed to be kept, then this behaviour must be changed, so that the
   * server gets a new notification about the object insertion as soon as the old model object is modified in any other
   * way than its removal.
   */
  private void removeOtherModelData(Set<String> keysToKeep)
  {
	keysToKeep.add(ROOT);
	Map<String, Operation<?>> toRemove = _modelDataMap
		.entrySet()
		.stream()
		.filter(e -> !keysToKeep.contains(e.getKey()))
		.collect(Collectors.toMap(Entry::getKey, e -> e.getValue().get()));
	LOG.debug("Model reset - Removing {} entries:", toRemove.size());
	toRemove.forEach((key, operation) -> {
	  _modelDataMap.remove(key);
	  LOG.debug("  {}: {}", key, operation);
	});
  }

  @Override
  public String getObjectId()
  {
	return ROOT;
  }

  @Override
  public String getObjectType()
  {
	return "map";
  }

  public boolean isResetOnOperationException()
  {
	return _resetOnOperationException;
  }

  private ModelObjectData<?> getOrCreateModelData(String id, String type, Operation<?> op)
  {
	return _modelDataMap.computeIfAbsent(id, i -> new ModelObjectData<>(op, id, type, this));
  }

  private class ModelResetHandler implements CombinedHandler
  {
	Set<String> _updatedKeys = new HashSet<>();

	public Set<String> getUpdatedKeys()
	{
	  return _updatedKeys;
	}

	@Override
	public void update(String id, String type, Operation<?> change)
	{
	  _updatedKeys.add(id);
	  ModelObjectData<?> data = getOrCreateModelData(id, type, change);
	  if ( data.get() == change )
	  {
		return;
	  }
	  data.setValue(change);
	  if ( data.getObject() instanceof AbstractCustomSharedObject<?> resetable )
	  {
		resetable.reset(_currentOperationsUser);
	  }
	}
  }

  @SuppressWarnings("unchecked")
  protected CombinedHandler createHandler()
  {
	return (id, type, change) -> {
	  ModelObjectData<?> data = getOrCreateModelData(id, type, change);
	  LOG.debug("Updating {}, {}, {}.", id, type, change);
	  if ( data.get() == change )
	  {
		return;
	  }
	  data.setValue(_otType.compose(type, data.get(), change));
	  data.getEditor().operationApplied(change, false, _currentOperationsUser);
	};
  }

  @Override
  public CloseableLock lock()
  {
	if ( _lockDepth++ == 0 )
	{
	  _lock = _editor.lock();
	}
	return new CloseableLockImpl();
  }

  @Override
  public void close()
  {
	_editor.close();
  }

  public SharedObject getObject(String id, String type)
  {
	return getOrCreateModelData(id, type, CompoundOperation.empty()).getObject();
  }

  @SuppressWarnings("unchecked")
  private <T extends SharedObject> T initObject(String type, String id)
  {
	return (T) getObject(id, type);
  }

  private <T extends SharedObject> T initObject(String type)
  {
	return initObject(type, nextId());
  }

  private String nextId()
  {
	return _editor.getId() + "-" + _latestId++;
  }

  @Override
  public CustomSharedMap newMap()
  {
	return initObject("map");
  }

  @Override
  public CustomSharedString newString()
  {
	return initObject("string");
  }

  @Override
  public <T> CustomSharedList<T> newList()
  {
	return initObject("list");
  }

  @Override
  public <T extends SharedObject> T newObject(String type, Class<T> objectType)
  {
	return initObject(type);
  }

  @Override
  public boolean containsKey(String key)
  {
	return _root.containsKey(key);
  }

  @Override
  public <T> T get(String key)
  {
	return _root.get(key);
  }

  @Override
  public void remove(String key)
  {
	_root.remove(key);
  }

  public boolean removeObject(Object obj)
  {
	return _root.removeObject(obj);
  }

  @Override
  public void set(String key, Object value)
  {
	_root.set(key, value);
  }

  @Override
  public void addChangeListener(Listener listener)
  {
	_root.addChangeListener(listener);
  }

  @Override
  public void removeChangeListener(Listener listener)
  {
	_root.removeChangeListener(listener);
  }

  private class CloseableLockImpl implements CloseableLock
  {
	private boolean closed;

	@Override
	public void close()
	{
	  if ( closed )
	  {
		return;
	  }

	  closed = true;
	  if ( --_lockDepth == 0 )
	  {
		try
		{
		  try
		  {
			for ( Runnable r : _queuedEvents )
			{
			  r.run();
			}
		  }
		  finally
		  {
			_queuedEvents.clear();
		  }
		}
		finally
		{
		  _lock.close();
		}
	  }
	}
  }

  /**
   * Groups SharedObject, value and {@link CustomSharedObjectEditor} together in order to reduce the number of Map.get
   * operations.
   *
   * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
   * @version $Rev$
   *
   */
  class ModelObjectData<T extends Operation<?>> implements Supplier<T>, Consumer<T>
  {
	private final String					  _id;
	private final String					  _type;
	private final CustomSharedObjectEditor<T> _sharedObjectEditor;
	private final SharedObject				  _object;
	private T								  _value;

	@SuppressWarnings({"rawtypes", "unchecked"})
	ModelObjectData(T value, String id, String type, CustomModel model)
	{
	  _value = value;
	  _id = id;
	  _type = type;

	  SharedObjectFactory<?, ?> factory = _types.get(_type);
	  if ( factory == null )
	  {
		throw new OperationException("Unknown type: " + _type);
	  }
	  LOG.debug("Creating {} {}: {}", _type, _id, _value);
	  CustomSharedObjectEditor editor = new CustomSharedObjectEditor<>(model, _id, _type, this, _queuedEvents::add);
	  _sharedObjectEditor = editor;
	  _object = factory.create(editor);
	}

	@SuppressWarnings("unchecked")
	public void setValue(Operation<?> operation)
	{
	  _value = (T) operation;
	}

	@SuppressWarnings("rawtypes")
	public CustomSharedObjectEditor getEditor()
	{
	  return _sharedObjectEditor;
	}

	public SharedObject getObject()
	{
	  return _object;
	}

	@Override
	public T get()
	{
	  return _value;
	}

	/**
	 * Applies the given operation received from the {@link SharedObjectEditor}. This corresponds to
	 * {@link DefaultModel#apply()}. DefaultModel.apply uses a lock, but it seems unnecessary because apply is only
	 * called when a SharedObjectEditor sets its value and all those setters use the lock already.
	 */
	@Override
	public void accept(T op)
	{
	  LOG.debug("Applying {}, {}. Currently: {}", _id, op, _value);
	  @SuppressWarnings("unchecked")
	  T composed = (T) _otType.compose(_type, _value, op);
	  setValue(composed);

	  Operation<CombinedHandler> combinedOp = CombinedDelta.builder().update(_id, _type, op).done();
	  _editor.apply(combinedOp);
	  _sharedObjectEditor.operationApplied(op, true, null);
	}
  }
}
