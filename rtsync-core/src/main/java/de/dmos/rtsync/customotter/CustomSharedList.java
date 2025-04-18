package de.dmos.rtsync.customotter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.SharedList;
import se.l4.otter.model.spi.DataValues;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.OperationException;
import se.l4.otter.operations.list.ListDelta;
import se.l4.otter.operations.list.ListHandler;

public class CustomSharedList<T> extends SharedObjectContainer<Operation<ListHandler>> implements SharedList<T>
{
  private final ListHandler _resetHandler = new ResetHandler();
  private List<T>           _values;

  public CustomSharedList(CustomSharedObjectEditor<Operation<ListHandler>> editor)
  {
	super(editor);

	onReset();
	editor.setOperationHandler(this);
  }

  public List<T> toList()
  {
	return List.copyOf(_values);
  }

  //  private void setContainerIfPossible(Object obj)
  //  {
  //	if ( obj instanceof AbstractCustomSharedObject<?> sharedObject )
  //	{
  //	  sharedObject.setContainer(this);
  //	}
  //  }

  private class ResetHandler implements ListHandler
  {
	@Override
	public void retain(int length)
	{
	  throw new OperationException("Latest value invalid, must only contain inserts.");
	}

	@Override
	@SuppressWarnings("unchecked")
	public void insert(Object item)
	{
	  T value = (T) DataValues.fromData(editor, item);
	  _values.add(value);
	  //	  setContainerIfPossible(value);
	}

	@Override
	public void delete(Object item)
	{
	  throw new OperationException("Latest value invalid, must only contain inserts.");
	}
  }

  private class ListOperationHandler implements ListHandler
  {
	int index = 0;

	@Override
	public void retain(int length)
	{
	  index += length;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void insert(Object item)
	{
	  T value = (T) DataValues.fromData(editor, item);
	  _values.add(index, value);
	  index += 1;
	  //	  setContainerIfPossible(value);
	}

	@Override
	public void delete(Object item)
	{
	  _values.remove(index);
	}
  }

  @Override
  public void onReset()
  {
	_values = new ArrayList<>();
	editor.getCurrent().apply(_resetHandler);
  }

  @Override
  public void apply(Operation<ListHandler> op, boolean local)
  {
	op.apply(new ListOperationHandler());
  }

  @Override
  public int length()
  {
	return _values.size();
  }

  @Override
  public T get(int index)
  {
	if(index >= _values.size())
	{
	  throw new IndexOutOfBoundsException("Index must be less than length. Was " + index + " but length is " + _values.size());
	}

	return _values.get(index);
  }

  @Override
  public boolean contains(T value)
  {
	return _values.contains(value);
  }

  @Override
  public void clear()
  {
	Operation<ListHandler> listOp;
	try(CloseableLock lock = editor.lock())
	{
	  ListDelta<Operation<ListHandler>> delta = ListDelta.builder();
	  for(T item : _values)
	  {
		delta.delete(DataValues.toData(item));
	  }
	  listOp = delta.done();
	  editor.apply(listOp);
	}
  }

  @Override
  public void add(T item)
  {
	Operation<ListHandler> listOp;
	try(CloseableLock lock = editor.lock())
	{
	  listOp = ListDelta.builder().retain(_values.size()).insert(DataValues.toData(item)).done();
	  editor.apply(listOp);
	}
  }

  @Override
  public void addAll(Collection<? extends T> items)
  {
	Operation<ListHandler> listOp;
	try(CloseableLock lock = editor.lock())
	{
	  ListDelta<Operation<ListHandler>> delta = ListDelta.builder()
		  .retain(_values.size());

	  for(T item : items)
	  {
		delta.insert(DataValues.toData(item));
	  }
	  listOp = delta.done();
	  editor.apply(listOp);
	}
  }

  @Override
  public void insert(int index, T item)
  {
	Operation<ListHandler> listOp;
	try(CloseableLock lock = editor.lock())
	{
	  int length = length();
	  listOp = ListDelta.builder().retain(index).insert(DataValues.toData(item)).retain(length - index).done();
	  editor.apply(listOp);
	}
  }

  @Override
  public void insertAll(int index, Collection<? extends T> items)
  {
	Operation<ListHandler> listOp;
	try(CloseableLock lock = editor.lock())
	{
	  int length = length();
	  ListDelta<Operation<ListHandler>> delta = ListDelta.builder()
		  .retain(index);

	  for(T item : items)
	  {
		delta.insert(DataValues.toData(item));
	  }

	  delta.retain(length - index);
	  listOp = delta.done();
	  editor.apply(listOp);
	}
  }

  @Override
  public void remove(int index)
  {
	try (CloseableLock lock = editor.lock())
	{
	  applyRemoveToEditor(index);
	}
  }

  @Override
  public boolean removeObject(Object obj)
  {
	try(CloseableLock lock = editor.lock())
	{
	  int index = _values.indexOf(obj);
	  if ( index < 0 )
	  {
		return false;
	  }
	  applyRemoveToEditor(index);
	}
	return true;
  }

  private Operation<ListHandler> applyRemoveToEditor(int index)
  {
	Operation<ListHandler> listOp;
	int length = length();
	listOp = ListDelta
		.builder()
		.retain(index)
		.delete(DataValues.toData(_values.get(index)))
		.retain(length - index - 1)
		.done();
	editor.apply(listOp);
	return listOp;
  }

  @Override
  public void removeRange(int fromIndex, int toIndex)
  {
	Operation<ListHandler> listOp;
	try(CloseableLock lock = editor.lock())
	{
	  int length = length();
	  ListDelta<Operation<ListHandler>> delta = ListDelta.builder()
		  .retain(fromIndex);

	  for(int i=fromIndex; i<toIndex; i++)
	  {
		delta.delete(DataValues.toData(_values.get(i)));
	  }

	  delta.retain(length - toIndex);
	  listOp = delta.done();
	  editor.apply(listOp);
	}
  }

  @Override
  public void set(int index, T value)
  {
	Operation<ListHandler> listOp;
	try(CloseableLock lock = editor.lock())
	{
	  int length = length();
	  listOp = ListDelta
		  .builder()
		  .retain(index)
		  .insert(DataValues.toData(value))
		  .delete(DataValues.toData(_values.get(index)))
		  .retain(length - index - 1)
		  .done();
	  editor.apply(listOp);
	}
  }

  @Override
  public Object getObjectAtPathPart(String pathPart) throws NumberFormatException, IndexOutOfBoundsException
  {
	return get(Integer.parseInt(pathPart));
  }

  @Override
  @SuppressWarnings("unchecked")
  public void insertObjectAtPathPart(Object obj, String pathPart)
	  throws IndexOutOfBoundsException, ClassCastException
  {
	insert(Integer.parseInt(pathPart), (T) obj);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Stream<Object> getContents()
  {
	return (Stream<Object>) _values.stream();
  }
}
