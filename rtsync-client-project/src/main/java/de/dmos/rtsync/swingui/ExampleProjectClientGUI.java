package de.dmos.rtsync.swingui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import de.dmos.rtsync.client.ClientRTProjectData;
import de.dmos.rtsync.client.ClientSubscriptionContainer;
import de.dmos.rtsync.client.ProjectAvailability;
import de.dmos.rtsync.client.RTProjectClientOperationSync;

public class ExampleProjectClientGUI extends AbstractExampleClientGUI
{
  private final RTProjectClientOperationSync _projectSync;
  private JButton							 _createProjectButton;
  private JTextField						 _newProjectTxt;
  private JButton							 _subscribeButton;
  private RTProjectList						 _projectList;
  private RTProjectViewPanel				 _projectView;
  private RTUserOverviewBox				 _userOverview;
  private ClientSubscriptionContainer		 _lastSelectionSubscriptionContainer;

  public ExampleProjectClientGUI(RTProjectClientOperationSync projectClientOperationSync)
  {
	super(projectClientOperationSync.getConnectionHandler(), "RT Sync Project Demo", new Dimension(800, 360));
	_projectSync = projectClientOperationSync;
  }

  @Override
  protected void buildGUI()
  {
	super.buildGUI();
	int txtWidth = 200;

	addLabelledComponent(1, 3, _receivedMessageArea, "last exception:", _connectionPanel);

	_newProjectTxt = new JTextField("Project A", 30);
	_newProjectTxt.setMinimumSize(new Dimension(txtWidth, 22));
	_newProjectTxt.getDocument().addDocumentListener((DocumentChangeListener) c -> updateCreateProjectButton());
	addLabelledComponent(4, 3, _newProjectTxt, "new project name:", _connectionPanel);
	_createProjectButton = new JButton("create project");
	_createProjectButton.addActionListener(actionEvent -> createProject());
	addComponent(_connectionPanel, _createProjectButton, 6, 3);

	_subscribeButton = new JButton("subscribe");
	_subscribeButton.addActionListener(a -> {
	  _subscribeButton.setEnabled(false);
	  ProjectAvailability selected = _projectList.getSelectedValue();
	  if ( selected.availabilityState().hasLocalRTProjectData() )
	  {
		_projectSync.closeProject(selected.project());
	  }
	  else
	  {
		_projectSync.getOrCreateRTProjectData(selected.project());
	  }
	});
	Box projectListHeaderBox = Box.createHorizontalBox();
	projectListHeaderBox.add(new JLabel("Projects"));
	projectListHeaderBox.add(Box.createHorizontalStrut(6));
	projectListHeaderBox.add(Box.createHorizontalGlue());
	projectListHeaderBox.add(_subscribeButton);
	projectListHeaderBox
	.setMaximumSize(new Dimension(Integer.MAX_VALUE, projectListHeaderBox.getPreferredSize().height));

	_projectList = new RTProjectList(_projectSync);
	_projectList.addListSelectionListener(selectionEvent -> {
	  if ( !selectionEvent.getValueIsAdjusting() )
	  {
		updateSelectedProject();
	  }
	});

	Box projectBox = Box.createHorizontalBox();
	projectBox
	.add(alignVerticallyWithHorizontalFilling(projectListHeaderBox, Box.createVerticalStrut(8), _projectList));
	projectBox.add(Box.createHorizontalGlue());
	_projectView = new RTProjectViewPanel(_connectionHandler);
	projectBox.add(Box.createHorizontalStrut(10));
	projectBox.add(_projectView);
	projectBox.add(Box.createHorizontalStrut(10));
	projectBox.setBorder(new EmptyBorder(INSETS));

	_userOverview = new RTUserOverviewBox(_connectionHandler);
	projectBox.add(alignVerticallyWithHorizontalFilling(_userOverview, Box.createVerticalGlue()));

	updateCreateProjectButton();
	updateSelectedProject();

	_frame.add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, _connectionPanel, projectBox));
  }

  private void createProject()
  {
	_createProjectButton.setEnabled(false);
	String newProject = _newProjectTxt.getText();
	_projectSync.getOrCreateRTProjectData(newProject);
	updateCreateProjectButton();

	SwingUtilities.invokeLater(() -> _projectList.setSelectedProject(newProject, true));
  }

  private void updateCreateProjectButton()
  {
	String newProject = _newProjectTxt.getText();
	_createProjectButton
	.setEnabled(newProject != null && !newProject.isBlank() && !_projectSync.containsProject(newProject));
  }

  private void updateSelectedProject()
  {
	ProjectAvailability selected = _projectList.getSelectedValue();

	_subscribeButton
	.setText(
	  selected != null && selected.availabilityState().hasLocalRTProjectData() ? "unsubscribe" : "subscribe");
	_subscribeButton.setEnabled(selected != null);

	ClientRTProjectData data = selected != null ? _projectSync.getProjectData(selected.project()) : null;
	ClientSubscriptionContainer subscriptionContainer = data != null ? data.getSubscriptionContainer() : null;

	if ( subscriptionContainer != _lastSelectionSubscriptionContainer )
	{
	  _lastSelectionSubscriptionContainer = subscriptionContainer;
	  _projectView.setSubscriptionContainer(subscriptionContainer);
	  _userOverview.setSubscriptionSupplier(subscriptionContainer);
	}

	updateCreateProjectButton();
  }

  private JPanel alignVerticallyWithHorizontalFilling(Component... components)
  {
	JPanel panel = new JPanel();
	panel.setLayout(new GridBagLayout());

	int lastIndex = components.length - 1;
	for ( int i = 0; i < lastIndex; i++ )
	{
	  GridBagConstraints constraints = new GridBagConstraints();
	  //	  constraints.anchor = GridBagConstraints.NORTHWEST;
	  constraints.gridx = 0;
	  constraints.gridy = i;
	  constraints.weightx = 1;
	  constraints.fill = GridBagConstraints.HORIZONTAL;
	  constraints.insets = INSETS;
	  panel.add(components[i], constraints);
	}
	GridBagConstraints constraints = new GridBagConstraints();
	//	constraints.anchor = GridBagConstraints.NORTHWEST;
	constraints.gridx = 0;
	constraints.gridy = lastIndex;
	constraints.fill = GridBagConstraints.BOTH;
	constraints.weightx = 1;
	constraints.weighty = 1;
	panel.add(components[lastIndex], constraints);

	return panel;
  }
}
