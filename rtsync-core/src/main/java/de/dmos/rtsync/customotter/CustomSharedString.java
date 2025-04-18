package de.dmos.rtsync.customotter;

import java.util.LinkedList;

import com.sksamuel.diffpatch.DiffMatchPatch;
import com.sksamuel.diffpatch.DiffMatchPatch.Diff;

import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.SharedString;
import se.l4.otter.model.internal.SharedStringImpl;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.OperationException;
import se.l4.otter.operations.string.AnnotationChange;
import se.l4.otter.operations.string.StringDelta;
import se.l4.otter.operations.string.StringHandler;

/**
 * A copy of {@link SharedStringImpl} which checks, whether delete and retain operations make sense and resets it if
 * case they don't. The SharedStringTest doesn't test this case.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class CustomSharedString extends AbstractCustomSharedObject<Operation<StringHandler>>
implements
SharedString
{
  private static final DiffMatchPatch DIFF          = new DiffMatchPatch();

  public static Operation<StringHandler> diffToOperation(String from, String newValue)
  {
	Operation<StringHandler> stringOp;
	LinkedList<Diff> diffs = DIFF.diff_main(from, newValue);
	if ( diffs.size() > 2 )
	{
	  DIFF.diff_cleanupSemantic(diffs);
	  DIFF.diff_cleanupEfficiency(diffs);
	}

	StringDelta<Operation<StringHandler>> builder = StringDelta.builder();
	for ( Diff d : diffs )
	{
	  switch (d.operation)
	  {
		case EQUAL:
		  builder.retain(d.text.length());
		  break;
		case DELETE:
		  builder.delete(d.text);
		  break;
		case INSERT:
		  builder.insert(d.text);
		  break;
	  }
	}
	stringOp = builder.done();
	return stringOp;
  }

  private final ResetHandler          _resetHandler = new ResetHandler();
  private StringBuilder               _value;

  public CustomSharedString(CustomSharedObjectEditor<Operation<StringHandler>> editor)
  {
	super(editor);
	_value = new StringBuilder();
	editor.getCurrent().apply(_resetHandler);
	editor.setOperationHandler(this);
  }

  @Override
  public void onReset()
  {
	try (CloseableLock lock = editor.lock())
	{
	  _value = new StringBuilder();
	  editor.getCurrent().apply(_resetHandler);
	}
  }

  private class ResetHandler implements StringHandler
  {
	@Override
	public void retain(int count)
	{
	  throw new OperationException("Latest value invalid, must only contain inserts.");
	}

	@Override
	public void insert(String s)
	{
	  _value.append(s);
	}

	@Override
	public void delete(String s)
	{
	  throw new OperationException("Latest value invalid, must only contain inserts.");
	}

	@Override
	public void annotationUpdate(AnnotationChange change)
	{
	  // Annotations are not currently handled
	}
  }

  private class StringOperationHandler implements StringHandler
  {
	int _index = 0;

	@Override
	public void retain(int count)
	{
	  _index += count;
	}

	@Override
	public void insert(String s)
	{
	  _value.insert(_index, s);
	  _index += s.length();
	}

	@Override
	public void delete(String s)
	{
	  if ( !s.equals(_value.substring(_index, _index + s.length())) )
	  {
		throw new OperationException(
		  String
		  .format(
			"The string part in %s at index %d doesn't match the one to delete '%s'",
			_value.toString(),
			_index,
			s));
	  }
	  _value.delete(_index, _index + s.length());
	}

	@Override
	public void annotationUpdate(AnnotationChange change)
	{
	  // Annotations are not currently handled
	}
  }

  @Override
  void apply(Operation<StringHandler> operation, boolean local)
  {
	operation.apply(new StringOperationHandler());
  }

  @Override
  public String get()
  {
	return _value.toString();
  }

  @Override
  public void set(String newValue)
  {
	Operation<StringHandler> stringOp;
	try (CloseableLock lock = editor.lock())
	{
	  stringOp = diffToOperation(_value.toString(), newValue);
	  editor.apply(stringOp);
	}
  }

  @Override
  public void append(String value)
  {
	Operation<StringHandler> stringOp;
	try (CloseableLock lock = editor.lock())
	{
	  int length = this._value.length();

	  stringOp = StringDelta.builder().retain(length).insert(value).done();
	  editor.apply(stringOp);
	}
  }

  @Override
  public void insert(int idx, String value)
  {
	Operation<StringHandler> stringOp;
	try (CloseableLock lock = editor.lock())
	{
	  int length = this._value.length();
	  // The following line was commented out to fix a bug. Found on https://github.com/LevelFourAB/otter-java/commit/924f12e2083a0e089bc5d26cddc723ff13163cb9
	  // this.value.insert(idx, value);

	  stringOp = StringDelta.builder().retain(idx).insert(value).retain(length - idx).done();
	  editor.apply(stringOp);
	}
  }

  @Override
  public void remove(int fromIndex, int toIndex)
  {
	Operation<StringHandler> stringOp;
	try (CloseableLock lock = editor.lock())
	{
	  int length = this._value.length();
	  String deleted = this._value.substring(fromIndex, toIndex);
	  stringOp = StringDelta.builder().retain(fromIndex).delete(deleted).retain(length - toIndex).done();
	  editor.apply(stringOp);
	}
  }
}
