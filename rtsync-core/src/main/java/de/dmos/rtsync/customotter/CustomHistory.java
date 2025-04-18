package de.dmos.rtsync.customotter;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.CloseableIterator;
import se.l4.otter.engine.InMemoryOperationHistory;
import se.l4.otter.engine.OperationHistory;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.OTType;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

/**
 * A {@link OperationHistory} which works similarly to the {@link InMemoryOperationHistory} but also allows stored
 * operations to change and stores {@link TaggedUserOperation}s.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 * @param <T>
 */
public class CustomHistory<T extends Operation<CombinedHandler>> implements TaggedUserOperationHistory<T>
{
  protected final OTType<T>          _type;
  protected final SortedMap<Long, TaggedUserOperation> _operations;
  protected final SortedSet<Long>                      _wholeStateOperationIds;
  protected final boolean                              _taggedOperationStoreIncreasesHistoryIds;

  /**
   * @param taggedOperationStoreIncreasesHistoryIds If true (default), then {@link #store(TaggedOperation)} and
   *          {@link #store(TaggedUserOperation)} increase the historyId. Otherwise the {@link TaggedOperation}s
   *          historyId is used or reused. Servers should use true and their clients should use false.
   */
  public CustomHistory(OTType<T> type, T initial, boolean taggedOperationStoreIncreasesHistoryIds)
  {
	this(type, new TaggedUserOperation(1, "", initial, null), taggedOperationStoreIncreasesHistoryIds);
  }

  /**
   * @param taggedOperationStoreIncreasesHistoryIds If true (default), then {@link #store(TaggedOperation)} and
   *          {@link #store(TaggedUserOperation)} increase the historyId. Otherwise the {@link TaggedOperation}s
   *          historyId is used or reused. Servers should use true and their clients should use false.
   */
  public CustomHistory(OTType<T> type, TaggedUserOperation initial, boolean taggedOperationStoreIncreasesHistoryIds)
  {
	_type = type;
	_taggedOperationStoreIncreasesHistoryIds = taggedOperationStoreIncreasesHistoryIds;
	_operations = new ConcurrentSkipListMap<>();
	_wholeStateOperationIds = new ConcurrentSkipListSet<>();
	putOperation(1l, initial, true);
  }

  @Override
  public void resetOperationsTo(TaggedUserOperation taggedBaseOperation)
  {
	long id = taggedBaseOperation.getHistoryId();
	synchronized (this)
	{
	  _operations.tailMap(id + 1).sequencedKeySet().forEach(_operations::remove);
	  putOperation(id, taggedBaseOperation, true);
	}
  }

  @Override
  public long getLatestWholeStateVersion()
  {
	return _wholeStateOperationIds.getLast();
  }

  private void putOperation(long id, TaggedUserOperation userOp, boolean wholeState)
  {
	_operations.put(id, userOp);
	if ( wholeState )
	{
	  _wholeStateOperationIds.add(id);
	}
  }

  @Override
  public OTType<T> getType()
  {
	return _type;
  }

  @Override
  public long store(T op)
  {
	synchronized (this)
	{
	  return store(new TaggedUserOperation(_operations.lastKey() + 1, "", op, null), false).getHistoryId();
	}
  }

  @Override
  public TaggedUserOperation store(TaggedUserOperation userOp, boolean wholeState)
  {
	synchronized (this)
	{
	  if ( _taggedOperationStoreIncreasesHistoryIds )
	  {
		long id = _operations.lastKey() + 1;
		TaggedUserOperation storedOp =
			new TaggedUserOperation(id, userOp.getToken(), userOp.getOperation(), userOp.getUser());
		_operations.put(id, storedOp);
		return storedOp;
	  }
	  else
	  {
		long id = userOp.getHistoryId();
		_operations.put(id, userOp);
		return userOp;
	  }
	}
  }

  @Override
  public long getLatest()
  {
	return _operations.lastKey();
  }

  @Override
  public TaggedUserOperation getLatestUserOperation()
  {
	return _operations.lastEntry().getValue();
  }

  @SuppressWarnings({"unchecked"})
  private T getCastedOperation(TaggedUserOperation taggedUserOperation)
  {
	return (T) taggedUserOperation.getOperation();
  }

  private CloseableIterator<T> mapToIterator(SortedMap<Long, TaggedUserOperation> sortedMap)
  {
	return new IteratorImpl<>(sortedMap.values().stream().map(this::getCastedOperation).iterator());
  }

  @Override
  public CloseableIterator<T> between(long start, long end)
  {
	return mapToIterator(_operations.subMap(start, end));
  }

  @Override
  public CloseableIterator<T> from(long historyId)
  {
	return mapToIterator(_operations.tailMap(historyId));
  }

  @Override
  public CloseableIterator<T> until(long historyId)
  {
	return mapToIterator(_operations.headMap(historyId));
  }

  private static class IteratorImpl<T> implements CloseableIterator<T>
  {
	private final Iterator<T> it;

	public IteratorImpl(Iterator<T> it)
	{
	  this.it = it;
	}

	@Override
	public void close()
	{
	  // Nothing to do here.
	}

	@Override
	public boolean hasNext()
	{
	  return it.hasNext();
	}

	@Override
	public T next()
	{
	  return it.next();
	}
  }
}
