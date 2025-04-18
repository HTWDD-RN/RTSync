package de.dmos.rtsync.customotter;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.dmos.rtsync.internalinterfaces.WholeStateStore;
import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.CloseableIterator;
import se.l4.otter.engine.DefaultEditorControl;
import se.l4.otter.engine.EditorControl;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.Composer;
import se.l4.otter.operations.OTType;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.OperationPair;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.ylem.ids.LongIdGenerator;
import se.l4.ylem.ids.SimpleLongIdGenerator;

/**
 * A copy of {@link DefaultEditorControl} which stores {@link TaggedUserOperation}s and can reset the history.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class CustomEditorControl<T extends Operation<CombinedHandler>> implements EditorControl<T>, WholeStateStore
{
  protected final TaggedUserOperationHistory<T> _history;
  protected final Lock             _lock;
  protected final LongIdGenerator  _idGenerator;
  protected final CloseableLock    _closeableLock;

  public CustomEditorControl(TaggedUserOperationHistory<T> history)
  {
	this(history, new ReentrantLock());
  }

  public CustomEditorControl(TaggedUserOperationHistory<T> history, Lock lock)
  {
	this(history, lock, new SimpleLongIdGenerator());
  }

  public CustomEditorControl(
	TaggedUserOperationHistory<T> history,
	Lock lock,
	LongIdGenerator idGenerator)
  {
	_lock = lock;
	_idGenerator = idGenerator;
	_closeableLock = lock::unlock;
	_history = history;
  }

  public void setBaseOperation(TaggedUserOperation baseOperation)
  {
	_history.resetOperationsTo(baseOperation);
  }

  @Override
  public OTType<T> getType()
  {
	return _history.getType();
  }

  @Override
  public CloseableLock lock()
  {
	_lock.lock();
	return _closeableLock;
  }

  @SuppressWarnings("unchecked")
  @Override
  public TaggedOperation<T> getLatest()
  {
	return (TaggedOperation<T>) getLatestUserOperation();
  }

  @Override
  public TaggedUserOperation getLatestUserOperation()
  {
	try (CloseableLock lock = lock()) {
	  TaggedUserOperation latestUserOp = _history.getLatestUserOperation();
	  long id = latestUserOp.getHistoryId();
	  long wholeStateId = _history.getLatestWholeStateVersion();
	  try (CloseableIterator<T> it = _history.between(wholeStateId, id + 1))
	  {
		Composer<T> composer = _history.getType().newComposer();
		while (it.hasNext())
		{
		  T op = it.next();
		  composer.add(op);
		}
		Operation<CombinedHandler> composed = composer.done();
		long sessionId = _idGenerator.next();

		// This could also be put as whole state operation into the _history if it is needed often.
		return new TaggedUserOperation(id, toString(sessionId), composed, latestUserOp.getUser());
	  }
	}
  }

  @Override
  public long getLatestVersion()
  {
	return _history.getLatest();
  }

  @SuppressWarnings("unchecked")
  @Override
  public TaggedOperation<T> store(TaggedOperation<T> operation)
  {
	return (TaggedOperation<T>) store(TaggedUserOperation.toTaggedUserOperation(operation), false);
  }

  @Override
  public TaggedUserOperation store(TaggedUserOperation userOp, boolean wholeState)
  {
	_lock.lock();
	try
	{
	  TaggedUserOperation toStore;
	  if ( !wholeState )
	  {
		@SuppressWarnings("unchecked")
		OperationPair<T> pair = composeAndTransform(userOp.getHistoryId(), (T) userOp.getOperation());
		T transformed = pair.getRight();
		toStore = new TaggedUserOperation(userOp.getHistoryId(), userOp.getToken(), transformed, userOp.getUser());
	  }
	  else
	  {
		toStore = userOp;
	  }
	  return _history.store(toStore, wholeState);
	}
	finally
	{
	  _lock.unlock();
	}
  }

  @Override
  public TaggedOperation<T> store(long historyBase, String token, T operation)
  {
	_lock.lock();
	try
	{
	  OperationPair<T> pair = composeAndTransform(historyBase, operation);
	  long latest = _history.store(pair.getRight());
	  return new TaggedOperation<>(latest, token, pair.getRight());
	}
	finally
	{
	  _lock.unlock();
	}
  }

  private OperationPair<T> composeAndTransform(long historyId, T operation)
  {
	// Get all of the operations that have occurred after our history
	T composed;
	try (CloseableIterator<T> it = _history.from(historyId + 1))
	{
	  Composer<T> composer = _history.getType().newComposer();

	  while (it.hasNext())
	  {
		composer.add(it.next());
	  }

	  composed = composer.done();
	}

	// Transform the new operation on top of the composed operation
	if ( composed == null )
	{
	  return new OperationPair<>(null, operation);
	}
	else
	{
	  return _history.getType().transform(composed, operation);
	}
  }

  private static final char[] DIGITS = {
	'0',
	'1',
	'2',
	'3',
	'4',
	'5',
	'6',
	'7',
	'8',
	'9',
	'a',
	'b',
	'c',
	'd',
	'e',
	'f',
	'g',
	'h',
	'i',
	'j',
	'k',
	'l',
	'm',
	'n',
	'o',
	'p',
	'q',
	'r',
	's',
	't',
	'u',
	'v',
	'w',
	'x',
	'y',
	'z',
	'A',
	'B',
	'C',
	'D',
	'E',
	'F',
	'G',
	'H',
	'I',
	'J',
	'K',
	'L',
	'M',
	'N',
	'O',
	'P',
	'Q',
	'R',
	'S',
	'T',
	'U',
	'V',
	'W',
	'X',
	'Y',
  'Z'};

  private static final int    MAX    = DIGITS.length;

  public static String toString(long i)
  {
	char[] buf = new char[11];
	int charPos = 10;

	int radix = MAX;
	i = -i;
	while (i <= -radix)
	{
	  buf[charPos--] = DIGITS[(int) (-(i % radix))];
	  i = i / radix;
	}
	buf[charPos] = DIGITS[(int) (-i)];

	return new String(buf, charPos, (11 - charPos));
  }
}
