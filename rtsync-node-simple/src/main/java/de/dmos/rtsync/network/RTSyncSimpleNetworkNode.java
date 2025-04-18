package de.dmos.rtsync.network;

import de.dmos.rtsync.customotter.AbstractOperationSync;
import de.dmos.rtsync.customotter.CustomEditor;
import de.dmos.rtsync.customotter.CustomEditorControl;
import de.dmos.rtsync.customotter.CustomHistory;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.customotter.CustomModelBuilder;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.serializers.MessageSerialization;
import se.l4.otter.engine.OperationHistory;
import se.l4.otter.engine.events.ChangeEvent;
import se.l4.otter.operations.CompoundOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.internal.combined.DefaultCombinedDelta;
import se.l4.otter.operations.internal.combined.Update;

/**
 * A class to bundle otter-java objects which are required for Real-Time communication by client or server.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public abstract class RTSyncSimpleNetworkNode
{
  protected final AbstractOperationSync							  _sync;
  protected final CustomModel  _model;
  protected final CustomEditor<Operation<CombinedHandler>>		  _editor;
  protected final CustomEditorControl<Operation<CombinedHandler>> _control;
  protected final OperationHistory<Operation<CombinedHandler>>	  _history;

  protected RTSyncSimpleNetworkNode(OperationSyncCreator syncCreator)
  {
	CustomHistory<Operation<CombinedHandler>> history = new CustomHistory<>(
		MessageSerialization.COMBINED_TYPE,
		new DefaultCombinedDelta<>(o -> o).done(),
		isServer());
	_history = history;
	_control = new CustomEditorControl<>(history);
	_sync = syncCreator.createOperationSync(_control);
	_editor = new CustomEditor<>(_sync);
	_model = new CustomModelBuilder(_editor)
		.setResetOnOperationException(!isServer())
		.build();
  }

  protected abstract boolean isServer();

  protected void changeModel(TaggedUserOperation baseOperation)
  {
	_control.setBaseOperation(baseOperation);
  }

  public CustomModel getModel()
  {
	return _model;
  }

  public AbstractOperationSync getSync()
  {
	return _sync;
  }

  public CustomEditor<Operation<CombinedHandler>> getEditor()
  {
	return _editor;
  }

  public CustomEditorControl<Operation<CombinedHandler>> getControl()
  {
	return _control;
  }

  public String changeEventToString(@SuppressWarnings("rawtypes") ChangeEvent changeEvent)
  {
	return (changeEvent.isRemote() ? "remote " : "local ") + operationToString(changeEvent.getOperation());
  }

  public String operationToString(Object obj)
  {
	if ( obj instanceof Update update )
	{
	  return update.getId() + ": " + operationToString(update.getOperation());
	}
	if ( !(obj instanceof CompoundOperation compoundOp) )
	{
	  return obj.toString();
	}

	StringBuilder builder = new StringBuilder();
	builder.append("[");
	boolean first = true;
	for ( Object subOp : compoundOp.getOperations() )
	{
	  if ( first )
	  {
		first = false;
	  }
	  else
	  {
		builder.append(", ");
	  }
	  builder.append(subOp.toString());
	}
	builder.append("]");
	return builder.toString();
  }

  /**
   * This is only called once by the constructor to create the required OperationSync of this RTSyncNetworkNode
   */

  public static interface OperationSyncCreator
  {
	AbstractOperationSync createOperationSync(
	  CustomEditorControl<Operation<CombinedHandler>> control);
  }

}
