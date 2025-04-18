package de.dmos.rtsync.swingui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dmos.rtsync.client.ClientSubscriptionContainer;
import de.dmos.rtsync.customotter.AbstractCustomSharedObject;
import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.customotter.CustomSharedList;
import de.dmos.rtsync.customotter.CustomSharedMap;
import de.dmos.rtsync.customotter.SharedObjectContainer;
import de.dmos.rtsync.util.WeakLinkedList;
import se.l4.otter.model.SharedString;

public class RTModelActionPanel extends JPanel
{
  private static final long							  serialVersionUID = -4218004384081947069L;
  private static final Logger LOG = LoggerFactory.getLogger(RTModelActionPanel.class);
  private static final Dimension			BUTTON_SIZE	= new Dimension(70, 30);

  private final JButton						_removeBtn;
  private final JTextField					_newKeyTxt;
  private final JSpinner					_newIndexSpinner;
  private final JComboBox<SharedObjectType>	_soTypeComboBox;
  private final JButton						_addBtn;
  private final Box									  _rtTextBox;
  private final CardLayout					_insertPathCard;
  private final JPanel						_insertPathPanel;
  private final WeakLinkedList<RTModelActionListener> _modelActionListeners;

  private transient CustomModel					_model;
  private transient ClientSubscriptionContainer	_subscriptionContainer;
  private transient Object						_selectedObject;
  private transient SharedObjectContainer<?>	_selectedObjectContainer;
  private String							_sharedStringId;

  private enum SharedObjectType
  {
	LIST, MAP, STRING;

	AbstractCustomSharedObject<?> create(CustomModel model)
	{
	  switch (this)
	  {
		case LIST:
		  return model.newList();
		case MAP:
		  return model.newMap();
		case STRING:
		  return model.newString();
		default:
		  return null;
	  }
	}
  }

  public RTModelActionPanel()
  {
	super();
	_modelActionListeners = new WeakLinkedList<>();
	setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

	_removeBtn = new JButton("remove");
	_removeBtn.setSize(BUTTON_SIZE);
	_removeBtn.setForeground(Color.black);
	_removeBtn.setBackground(new Color(255, 150, 150));
	_removeBtn.addActionListener(e -> removeSharedObject());

	add(_removeBtn);
	add(Box.createVerticalStrut(2));

	int textColumns = 12;
	_newKeyTxt = new JTextField(textColumns);
	_newKeyTxt.getDocument().addDocumentListener((DocumentChangeListener) e -> updateCreateButton());
	_newIndexSpinner = new JSpinner(new SpinnerNumberModel(0, 0, null, 1));
	_newIndexSpinner.addChangeListener(e -> updateCreateButton());
	_soTypeComboBox = new JComboBox<>(SharedObjectType.values());

	Box newKeyBox = Box.createHorizontalBox();
	addWithLabelToBox(newKeyBox, _newKeyTxt, "new key: ");
	Box newIndexBox = Box.createHorizontalBox();
	addWithLabelToBox(newIndexBox, _newIndexSpinner, "insert at: ");

	_insertPathPanel = new JPanel();
	_insertPathCard = new CardLayout();
	_insertPathPanel.setLayout(_insertPathCard);
	_insertPathPanel.add(newKeyBox, "key");
	_insertPathPanel.add(newIndexBox, "index");
	_insertPathCard.show(_insertPathPanel, "key");
	add(_insertPathPanel);

	_addBtn = new JButton("create");
	_addBtn.setSize(BUTTON_SIZE);
	_addBtn.setForeground(Color.black);
	_addBtn.setBackground(new Color(150, 255, 150));
	_addBtn.addActionListener(e -> createSharedObject());

	Box creationBox = Box.createHorizontalBox();
	creationBox.add(new JLabel("new object type:"));
	creationBox.add(Box.createHorizontalStrut(2));
	creationBox.add(_soTypeComboBox);
	creationBox.add(Box.createHorizontalStrut(8));
	creationBox.add(_addBtn);
	add(creationBox);

	add(Box.createVerticalStrut(6));
	_rtTextBox = Box.createHorizontalBox();
	_rtTextBox.add(new JLabel("text:"));
	_rtTextBox.add(new JTextPane());
	add(_rtTextBox);
	Dimension actionPanelSize = getPreferredSize();
	Dimension creationPanelSize = creationBox.getPreferredSize();
	actionPanelSize.width = creationPanelSize.width;
	setMinimumSize(actionPanelSize);
	setMaximumSize(actionPanelSize);
  }

  private void addWithLabelToBox(Box box, JComponent component, String labelText)
  {
	JLabel label = new JLabel(labelText);
	label.setLabelFor(component);
	box.add(label);
	box.add(component);
  }

  /**
   * Sets the object and {@link SharedObjectContainer} for this panel to display actions for.
   *
   * @param selectedObject The selected object. This is required for remove action and insert action (only if it is a
   *          {@link SharedObjectContainer}) as well as an eventual {@link RTJTextPane}.
   * @param container The {@link SharedObjectContainer} which contains the selected object. This is used for the remove
   *          action and insert action (only if it is not a {@link SharedObjectContainer}).
   */
  public void setActionObjects(Object selectedObject, SharedObjectContainer<?> container)
  {
	_selectedObject = selectedObject;
	_selectedObjectContainer = container;
	updateGUI();
  }

  public void updateGUI()
  {
	_removeBtn
	.setEnabled(
	  _selectedObjectContainer != null
	  && _selectedObject != null
	  && _selectedObject != _model
	  && _selectedObject != _model.getRoot());

	SharedObjectContainer<?> selectedContainer = getSelectedContainer();
	if ( selectedContainer instanceof CustomSharedList<?> )
	{
	  _insertPathCard.show(_insertPathPanel, "index");
	}
	else if ( selectedContainer instanceof CustomSharedMap )
	{
	  _insertPathCard.show(_insertPathPanel, "key");
	}
	updateCreateButton();

	if ( _selectedObject instanceof SharedString ss )
	{
	  if ( !ss.getObjectId().equals(_sharedStringId) )
	  {
		removeRTTextPanelIfAvailable();
		_sharedStringId = ss.getObjectId();
		RTJTextPane sharedStringTextPane =
			_subscriptionContainer != null ? new RTJTextPane(ss, _subscriptionContainer) : new RTJTextPane(ss);
		_rtTextBox.add(sharedStringTextPane);
		_rtTextBox.revalidate();
	  }
	  _rtTextBox.setVisible(true);
	}
	else
	{
	  removeRTTextPanelIfAvailable();
	  _sharedStringId = null;
	  _rtTextBox.setVisible(false);
	}
  }

  private void removeRTTextPanelIfAvailable()
  {
	if ( _rtTextBox.getComponents().length > 1 )
	{
	  _rtTextBox.remove(1);
	}
  }

  private void createSharedObject()
  {
	_addBtn.setEnabled(false);

	SharedObjectContainer<?> container = getSelectedContainer();

	String pathPart =
		container instanceof CustomSharedList<?> ? _newIndexSpinner.getValue().toString() : _newKeyTxt.getText();
	SharedObjectType type = (SharedObjectType) _soTypeComboBox.getSelectedItem();

	AbstractCustomSharedObject<?> newObject = type.create(_model);
	try
	{
	  container.insertObjectAtPathPart(newObject, pathPart);
	}
	catch (Exception ex)
	{
	  LOG.error("Could not insert newly created object at " + pathPart, ex);
	  updateCreateButton();
	  return;
	}

	_modelActionListeners.toStrongStream().forEach(l -> l.objectCreated(newObject, container));
	updateCreateButton();
  }

  private void updateCreateButton()
  {
	SharedObjectContainer<?> container = getSelectedContainer();
	if ( container == null )
	{
	  _addBtn.setEnabled(false);
	  return;
	}

	switch (container)
	{
	  case CustomSharedList<?> selectedSharedList -> {
		int index = (int) _newIndexSpinner.getValue();
		_addBtn.setEnabled(selectedSharedList.length() >= index);
	  }
	  case CustomSharedMap customMap -> {
		String newKey = _newKeyTxt.getText();
		_addBtn.setEnabled(!newKey.isBlank() && !customMap.containsKey(newKey));
	  }
	  default -> _addBtn.setEnabled(false);
	}
  }

  private void removeSharedObject()
  {
	_removeBtn.setEnabled(false);
	if ( _selectedObjectContainer.removeObject(_selectedObject) )
	{
	  _modelActionListeners.toStrongStream().forEach(l -> l.objectRemoved(_selectedObject, _selectedObjectContainer));
	}
	updateGUI();
  }

  private SharedObjectContainer<?> getSelectedContainer()
  {
	return _selectedObject instanceof SharedObjectContainer<?> c ? c : _selectedObjectContainer;
  }

  public void setSubscriptionContainer(ClientSubscriptionContainer subscriptionContainer)
  {
	_subscriptionContainer = subscriptionContainer;
	_model = subscriptionContainer != null ? subscriptionContainer.getModel() : null;
	_selectedObjectContainer = _model != null ? _model.getRoot() : null;
	SwingUtilities.invokeLater(this::updateGUI);
  }

  public void addAsWeakModelActionListener(RTModelActionListener listener)
  {
	_modelActionListeners.addAsWeakReference(listener);
  }

  public void removeWeakModelActionListener(Object listener)
  {
	_modelActionListeners.removeReferencedObject(listener);
  }

  public static interface RTModelActionListener {
	/**
	 * Called when a new object is created by the {@link RTModelActionPanel}.
	 *
	 * @param newObject The newly created object
	 * @param container The container of the new object.
	 */
	void objectCreated(AbstractCustomSharedObject<?> newObject, SharedObjectContainer<?> container);

	/**
	 * Called when a object is removed by the {@link RTModelActionPanel}.
	 *
	 * @param removeObject The removed object
	 * @param container The container of the removed object.
	 */
	void objectRemoved(Object removeObject, SharedObjectContainer<?> container);
  }
}
