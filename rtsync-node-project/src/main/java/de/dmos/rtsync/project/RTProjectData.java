package de.dmos.rtsync.project;

import de.dmos.rtsync.customotter.AbstractOperationSync;
import de.dmos.rtsync.customotter.CustomEditor;
import de.dmos.rtsync.customotter.CustomEditorControl;
import de.dmos.rtsync.customotter.CustomHistory;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.customotter.CustomModelBuilder;
import de.dmos.rtsync.serializers.MessageSerialization;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.internal.combined.DefaultCombinedDelta;

/**
 * Groups a project's model, editor, control and history.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class RTProjectData extends AbstractOperationSync
{
  private final CustomModel									  _model;
  private final CustomEditor<Operation<CombinedHandler>> _editor;
  private final String						   _project;
  protected final AbstractProjectOperationSync<?> _projectSync;

  public RTProjectData(String project, AbstractProjectOperationSync<?> projectSync)
  {
	super(createEditorControl(projectSync.isServer()));
	_project = project;
	_projectSync = projectSync;
	_editor = new CustomEditor<>(this);
	_model = new CustomModelBuilder(_editor).setResetOnOperationException(!projectSync.isServer()).build();
  }

  private static CustomEditorControl<Operation<CombinedHandler>> createEditorControl(boolean isServer)
  {
	CustomHistory<Operation<CombinedHandler>> history = new CustomHistory<>(
		MessageSerialization.COMBINED_TYPE,
		new DefaultCombinedDelta<>(o -> o).done(),
		isServer);
	return new CustomEditorControl<>(history);
  }

  public CustomModel getModel()
  {
	return _model;
  }

  public String getProject()
  {
	return _project;
  }

  public CustomEditor<Operation<CombinedHandler>> getEditor()
  {
	return _editor;
  }

  @Override
  public void send(TaggedOperation<Operation<CombinedHandler>> op)
  {
	_projectSync.send(this, op);
  }

  @Override
  public void close()
  {
	_projectSync.closeProject(_project);
  }
}
