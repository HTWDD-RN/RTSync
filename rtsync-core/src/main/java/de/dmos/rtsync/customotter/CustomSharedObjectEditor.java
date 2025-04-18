package de.dmos.rtsync.customotter;

import java.util.function.Consumer;

import de.dmos.rtsync.customotter.CustomModel.ModelObjectData;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.model.SharedObject;
import se.l4.otter.model.internal.SharedObjectEditorImpl;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.OperationException;

/**
 * An extension of {@link SharedObjectEditorImpl} that enables resets.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class CustomSharedObjectEditor<T extends Operation<?>> extends SharedObjectEditorImpl<T>
{
  private final CustomModel				_customModel;
  /**
   * A copy of super.handler which is required because of the limited visibility.
   */
  private AbstractCustomSharedObject<T>			_handler;


  public CustomSharedObjectEditor(
	CustomModel customModel,
	String id,
	String type,
	ModelObjectData<T> modelObjectData,
	Consumer<Runnable> eventQueuer)
  {
	super(null, id, type, modelObjectData, modelObjectData, eventQueuer);
	_customModel = customModel;
  }

  @Override
  public CloseableLock lock()
  {
	return _customModel.lock();
  }

  @Override
  public SharedObject getObject(String id, String type)
  {
	return _customModel.getObject(id, type);
  }

  @Override
  public void operationApplied(T op, boolean local) throws OperationException, IndexOutOfBoundsException
  {
	operationApplied(op, local, null);
  }

  public void operationApplied(T op, boolean local, String user)
	  throws OperationException, IndexOutOfBoundsException
  {
	if ( _handler == null )
	{
	  return;
	}
	if ( !_customModel.isResetOnOperationException() )
	{
	  _handler.newOperation(op, local, user);
	  return;
	}

	try
	{
	  _handler.newOperation(op, local, user);
	}
	catch (OperationException | IndexOutOfBoundsException ex)
	{
	  _handler.reset(user);
	}
  }

  @Override
  public void setOperationHandler(OperationHandler<T> handler)
  {
	_handler = (AbstractCustomSharedObject<T>) handler;
	super.setOperationHandler(handler);
  }
}
