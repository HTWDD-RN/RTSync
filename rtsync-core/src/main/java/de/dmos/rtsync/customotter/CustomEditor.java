package de.dmos.rtsync.customotter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.dmos.rtsync.internalinterfaces.TaggedUserOperationListener;
import de.dmos.rtsync.internalinterfaces.UserOperationSync;
import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.DefaultEditor;
import se.l4.otter.engine.Editor;
import se.l4.otter.engine.EditorListener;
import se.l4.otter.engine.OperationSync;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.engine.events.ChangeEvent;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.Composer;
import se.l4.otter.operations.OTType;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.OperationPair;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.internal.DefaultComposer;

/**
 * A copy of {@link DefaultEditor} which allows a reset of the internal state.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class CustomEditor<T extends Operation<CombinedHandler>> implements Editor<T>, TaggedUserOperationListener
{
  enum State
  {
	SYNCHRONIZED,
	AWAITING_CONFIRM,
	AWAITING_CONFIRM_WITH_BUFFER
  }

  protected final String                               _id;
  protected final OTType<T>                            _type;
  protected final OperationSync<T>                     _sync;
  protected final Lock                                 _lock;

  protected final List<CustomEditorListener<T>>    _listeners;
  protected final Map<String, CompletableFuture<Void>> _futures;

  protected State                                      _state;

  protected long                                       _parentHistoryId;
  protected int                                        _lastId;

  protected TaggedOperation<T>                         _lastSent;
  protected TaggedOperation<T>                         _buffer;

  protected T                                          _current;

  protected Composer<T>                                _composer;
  protected int                                        _lockDepth;

  public CustomEditor(OperationSync<T> sync)
  {
	_sync = sync;
	_type = sync.getType();
	_lock = new ReentrantLock();

	_listeners = new ArrayList<>();
	_futures = new HashMap<>();

	_lock.lock();
	try
	{
	  @SuppressWarnings("unchecked")
	  TaggedOperation<T> initial = sync instanceof UserOperationSync userSync
	  ? (TaggedOperation<T>) userSync.connectUserOperationListener(this)
		  : sync.connect(this::receive);
	  _id = initial.getToken();
	  reset(initial);
	}
	finally
	{
	  _lock.unlock();
	}
  }

  // TODO: Check if it wouldn't be better to change receive to allow a whole state.
  @Override
  public void onTaggedUserOperationReceived(TaggedUserOperation userOp, boolean wholeState)
  {
	if ( wholeState )
	{
	  resetToOperation(userOp);
	}
	else
	{
	  receive(userOp);
	}
  }

  private void reset(TaggedOperation<T> taggedOp)
  {
	_state = State.SYNCHRONIZED;
	_parentHistoryId = taggedOp.getHistoryId();
	_current = taggedOp.getOperation();
	_futures.clear();
  }

  @SuppressWarnings("unchecked")
  public void resetToOperation(TaggedUserOperation taggedOp)
  {
	_lock.lock();
	reset((TaggedOperation<T>) taggedOp);
	_listeners.forEach(l -> l.onReset((T) taggedOp.getOperation(), taggedOp.getUser()));
	_lock.unlock();
  }

  @Override
  public void close()
  {
	_lock.lock();
	try
	{
	  _sync.close();
	}
	finally
	{
	  _lock.unlock();
	}
  }

  @Override
  public String getId()
  {
	return _id;
  }

  @Override
  public OTType<T> getType()
  {
	return _type;
  }

  @Override
  public T getCurrent()
  {
	return _current;
  }

  /**
   * {@inheritDoc}
   *
   * @see #addCustomListener(CustomEditorListener)
   */
  @SuppressWarnings("unchecked")
  @Override
  public void addListener(EditorListener<T> listener)
  {
	_listeners
	.add(listener instanceof CustomEditorListener customListener ? customListener : listener::editorChanged);
  }

  /**
   * Adds a {@link CustomEditorListener} which will also be notified about resets.
   */
  public void addCustomListener(CustomEditorListener<T> listener)
  {
	_listeners.add(listener);
  }

  @Override
  public void removeListener(EditorListener<T> listener)
  {
	_listeners.remove(listener);
  }

  @SuppressWarnings("unchecked")
  private void receive(TaggedUserOperation userOp)
  {
	receive((TaggedOperation<T>) userOp, userOp.getUser());
  }

  private void receive(TaggedOperation<T> op)
  {
	receive(op, null);
  }

  // TODO: Check mergedIds and don't apply buffered operations if matching.
  private void receive(TaggedOperation<T> op, String user)
  {
	_lock.lock();
	try
	{
	  switch(_state)
	  {
		case SYNCHRONIZED:
		  /*
		   * No local changes, simply send operation to listeners.
		   */
		  _parentHistoryId = op.getHistoryId();
		  composeAndTriggerListeners(op.getOperation(), user);
		  break;
		case AWAITING_CONFIRM:
		  if(_lastSent.getToken().equals(op.getToken()))
		  {
			/*
			 * This is the operation we previously sent, we have
			 * already applied this locally so we can safely switch
			 * to synchronized state.
			 */
			_parentHistoryId = op.getHistoryId();
			this._state = State.SYNCHRONIZED;

			// Trigger the future for the operation
			CompletableFuture<Void> future = _futures.get(op.getToken());
			if(future != null)
			{
			  _futures.remove(op.getToken());
			  future.complete(null);
			}
		  }
		  else
		  {
			/*
			 * Someone else has edited the document before our own
			 * operation was applied. Transform the incoming operation
			 * over our sent operation.
			 */
			OperationPair<T> transformed = _type
				.transform(
				  op.getOperation(),
				  _lastSent.getOperation()
					);

			/*
			 * We stay in our current state but replace lastSent
			 * with the transformed operation so any other edits
			 * can be safely applied.
			 */
			_lastSent = new TaggedOperation<>(
				op.getHistoryId(),
				_lastSent.getToken(),
				transformed.getRight()
				);

			_parentHistoryId = op.getHistoryId();
			composeAndTriggerListeners(transformed.getLeft(), user);
		  }
		  break;
		case AWAITING_CONFIRM_WITH_BUFFER:
		  if(_lastSent.getToken().equals(op.getToken()))
		  {
			/*
			 * This is the operation we previously sent, so request
			 * that we send our buffer and switch to awaiting
			 * confirm.
			 */
			_parentHistoryId = op.getHistoryId();
			_state = State.AWAITING_CONFIRM;

			_buffer = new TaggedOperation<>(
				op.getHistoryId(),
				_buffer.getToken(),
				_buffer.getOperation()
				);
			_lastSent = _buffer;

			_sync.send(_buffer);
		  }
		  else
		  {
			/*
			 * Someone else has edited the document, rewrite
			 * both the incoming and our buffered operation.
			 */
			OperationPair<T> transformed = _type
				.transform(
				  op.getOperation(),
				  _lastSent.getOperation()
					);

			/*
			 * As for awaiting confirm, we replace lastSent with
			 * a transformed operation.
			 */
			_lastSent = new TaggedOperation<>(
				op.getHistoryId(),
				_lastSent.getToken(),
				transformed.getRight()
				);

			/*
			 * Transform the already transformed remote operation
			 * over our buffer.
			 */
			transformed = _type.transform(
			  _buffer.getOperation(),
			  transformed.getLeft()
				);

			_buffer = new TaggedOperation<>(
				op.getHistoryId(),
				_buffer.getToken(),
				transformed.getLeft()
				);

			_parentHistoryId = op.getHistoryId();
			composeAndTriggerListeners(transformed.getRight(), user);
		  }
		  break;
		default:
		  throw new AssertionError("Unknown state: " + _state);
	  }
	}
	finally
	{
	  _lock.unlock();
	}
  }

  private CompletableFuture<Void> future(String token)
  {
	return _futures.computeIfAbsent(token, k -> new CompletableFuture<>());
  }

  @Override
  public CompletableFuture<Void> apply(T op)
  {
	_lock.lock();
	try
	{
	  if(_lockDepth > 0)
	  {
		// If we are currently locked, compose together with previous op
		_composer.add(op);

		String nextToken = _id + "-" + (_lastId + 1);
		return future(nextToken);
	  }

	  // Compose together with the current operation
	  _current = _type.compose(_current, op);

	  CompletableFuture<Void> future;
	  switch(_state)
	  {
		case SYNCHRONIZED:
		{
		  /*
		   * Create a tagged version with a unique token and
		   * start tracking when it is applied.
		   */
		  String token = _id + "-" + (_lastId++);
		  TaggedOperation<T> tagged = new TaggedOperation<>(
			  _parentHistoryId,
			  token,
			  op
			  );

		  future = future(token);
		  _state = State.AWAITING_CONFIRM;
		  _lastSent = tagged;
		  _sync.send(tagged);

		  break;
		}
		case AWAITING_CONFIRM:
		{
		  /*
		   * We are already waiting for another operation to be applied,
		   * buffer this one.
		   */
		  String token = _id + "-" + (_lastId++);
		  TaggedOperation<T> tagged = new TaggedOperation<>(
			  _parentHistoryId,
			  token,
			  op
			  );

		  future = future(token);
		  _buffer = tagged;
		  _state = State.AWAITING_CONFIRM_WITH_BUFFER;
		  break;
		}
		case AWAITING_CONFIRM_WITH_BUFFER:
		{
		  /*
		   * We have something buffered, compose the buffer together
		   * with this edit.
		   */
		  _buffer = new TaggedOperation<>(
			  _buffer.getHistoryId(),
			  _buffer.getToken(),
			  _type.compose(_buffer.getOperation(), op)
			  );

		  future = future(_buffer.getToken());
		  break;
		}
		default:
		  throw new AssertionError("Unknown state: " + _state);
	  }

	  ChangeEvent<T> event = new UserChangeEvent<>(op, true, null);
	  _listeners.forEach(l -> l.editorChanged(event));

	  return future;
	}
	finally
	{
	  _lock.unlock();
	}
  }

  private void composeAndTriggerListeners(T op, String user)
  {
	_current = _type.compose(_current, op);

	ChangeEvent<T> event = new UserChangeEvent<>(op, false, user);
	_listeners.forEach(l -> l.editorChanged(event));
  }

  @Override
  public synchronized CloseableLock lock()
  {
	if(_lockDepth++ == 0)
	{
	  _lock.lock();
	  _composer = new DefaultComposer<>(_type);
	}

	return new CloseableLockImpl();
  }

  private class CloseableLockImpl
  implements CloseableLock
  {
	private boolean closed;

	@Override
	public void close()
	{
	  if(closed)
	  {
		return;
	  }

	  closed = true;
	  if(--_lockDepth == 0)
	  {
		T composed = _composer.done();
		if(composed != null)
		{
		  apply(composed);
		}
		_composer = null;
		_lock.unlock();
	  }
	}
  }
}
