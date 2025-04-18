package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import de.dmos.rtsync.client.ClientRTProjectData;
import de.dmos.rtsync.client.ProjectAvailability;
import de.dmos.rtsync.client.RTProjectClientOperationSync;
import de.dmos.rtsync.listeners.LocalProjectListener;
import de.dmos.rtsync.listeners.ProjectListListener;

/**
 * A {@link JPanel} which shows an automatically updating list of project names.
 */
public class RTProjectList extends JList<ProjectAvailability>
implements
ProjectListListener,
LocalProjectListener<ClientRTProjectData>
{
  private final RTProjectClientOperationSync _projectSync;
  private boolean							 _modelUpdateInitiated = false;
  private List<ProjectAvailability>			 _availabilities;

  /**
   * docme: serialVersionUID
   */
  private static final long serialVersionUID = -3877875421111346792L;

  public RTProjectList(RTProjectClientOperationSync projectSync)
  {
	super();
	setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	setCellRenderer(new ProjectListRenderer());
	_projectSync = projectSync;
	projectSync.addWeakProjectListListener(this);
	projectSync.addWeakLocalProjectListener(this);
	updateModel();
	if ( projectSync.getServerProjectNameList() == null && projectSync.isConnected() )
	{
	  projectSync.queryProjects();
	}
  }

  @Override
  public void onProjectListReceived(List<String> projects)
  {
	triggerModelUpdateIfNeeded();
  }

  public void triggerModelUpdateIfNeeded()
  {
	if ( _modelUpdateInitiated )
	{
	  return;
	}
	_modelUpdateInitiated = true;
	SwingUtilities.invokeLater(this::updateModel);
  }

  private void updateModel()
  {
	_modelUpdateInitiated = false;

	List<ProjectAvailability> availabilities = _projectSync.getProjectAvailabilities();
	if ( availabilities.equals(_availabilities) )
	{
	  return;
	}

	setValueIsAdjusting(true);
	List<ProjectAvailability> selection = getSelectedValuesList();
	setListData(availabilities.toArray(new ProjectAvailability[availabilities.size()]));

	Object[] newIndices = selection
		.stream()
		.flatMap(s -> availabilities.stream().filter(a -> a.projectEquals(s.project())).findAny().stream())
		.map(a -> Integer.valueOf(availabilities.indexOf(a)))
		.toArray();
	int[] selectedIndices = new int[newIndices.length];
	for ( int i = 0; i < newIndices.length; i++ )
	{
	  selectedIndices[i] = (int) newIndices[i];
	}
	setSelectedIndices(selectedIndices);
	setValueIsAdjusting(false);

	SwingUIHelper.revalidateTopComponent(this);
  }

  public boolean setSelectedProject(String project, boolean shouldScroll)
  {
	ListModel<ProjectAvailability> model = getModel();
	for ( int i = 0; i < model.getSize(); i++ )
	{
	  if ( model.getElementAt(i).projectEquals(project) )
	  {
		setSelectedValue(model.getElementAt(i), shouldScroll);
		return true;
	  }
	}
	return false;
  }

  @Override
  public void localProjectCreated(ClientRTProjectData data)
  {
	triggerModelUpdateIfNeeded();
  }

  @Override
  public void localProjectClosed(ClientRTProjectData data)
  {
	triggerModelUpdateIfNeeded();
  }

  static class ProjectListRenderer implements ListCellRenderer<ProjectAvailability>
  {
	private final ListCellRenderer<Object> _defaultRenderer	= new DefaultListCellRenderer();

	@Override
	public Component getListCellRendererComponent(
	  JList<? extends ProjectAvailability> list,
	  ProjectAvailability value,
	  int index,
	  boolean isSelected,
	  boolean cellHasFocus)
	{
	  Component defaultComponent =
		  _defaultRenderer
		  .getListCellRendererComponent(
			list,
			value.project(),
			index,
			isSelected,
			cellHasFocus);
	  switch (value.availabilityState())
	  {
		case LOCAL_AND_REMOTE:
		  if ( defaultComponent instanceof JLabel label )
		  {
			label.setText("* " + value.project());
			return label;
		  }
		  else
		  {
			Box box = Box.createHorizontalBox();
			box.add(new JLabel(" * "));
			box.add(defaultComponent);
			box.add(Box.createHorizontalGlue());
			return box;
		  }
		case LOCAL:
		  Color superBackground = defaultComponent.getBackground();
		  defaultComponent.setBackground(redder(superBackground));
		  return defaultComponent;
		default:
		  return defaultComponent;
	  }
	}

	/**
	 * Makes the given color redder by increasing its redness or decreasing its blueness or greenness.
	 */
	public Color redder(Color color)
	{
	  return redder(color, 0.22);
	}

	/**
	 * Makes the given color redder by the given factor by increasing its redness or decreasing its blueness or
	 * greenness.
	 */
	public Color redder(Color color, double factor)
	{
	  int red = (int) (factor * 255) + color.getRed();
	  int darkness = red > 255 ? red - 255 : 0;
	  return new Color(
		(red - darkness),
		Math.max(color.getGreen() - darkness, 0),
		Math.max(color.getBlue() - darkness, 0),
		color.getAlpha());
	}
  }
}
