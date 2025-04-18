package de.dmos.rtsync.customotter;

import de.dmos.rtsync.util.WeakLinkedList;
import se.l4.otter.model.spi.AbstractSharedObject;
import se.l4.otter.model.spi.SharedObjectEditor.OperationHandler;
import se.l4.otter.operations.Operation;

public abstract class AbstractCustomSharedObject<T extends Operation<?>> extends AbstractSharedObject<T>
implements
OperationHandler<T>,
ChangeSupplier<T>
{
  private final WeakLinkedList<ChangeListener<T>> _changeListeners = new WeakLinkedList<>();

  protected AbstractCustomSharedObject(CustomSharedObjectEditor<T> editor)
  {
	super(editor);
  }

  public T getCurrentWholeOperation()
  {
	return editor.getCurrent();
  }

  abstract void onReset();

  abstract void apply(T operation, boolean local);

  @Override
  public void newOperation(T op, boolean local)
  {
	newOperation(op, local, null);
  }

  public void newOperation(T op, boolean local, String user)
  {
	apply(op, local);
	notifyListeners(op, local, user);
  }

  public void reset(String user)
  {
	onReset();
	notifyListeners(null, false, user);
  }

  @Override
  public void addAsWeakChangeListener(ChangeListener<T> changeListener)
  {
	_changeListeners.addAsWeakReference(changeListener);
  }

  @Override
  public boolean removeWeakChangeListener(Object changeListener)
  {
	return _changeListeners.removeReferencedObject(changeListener);
  }

  private void notifyListeners(T op, boolean local, String user)
  {
	_changeListeners.toStrongStream().forEach(l -> l.valueChanged(op, local, user));
  }
}
