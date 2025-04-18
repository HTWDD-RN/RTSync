package de.dmos.rtsync.client;

import java.awt.Dimension;
import java.awt.GridBagConstraints;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.listeners.IncompatibleModelListener;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.swingui.AbstractExampleClientGUI;
import de.dmos.rtsync.swingui.RTJTextPane;
import de.dmos.rtsync.swingui.RTUserOverviewBox;
import se.l4.otter.model.SharedMap.Listener;
import se.l4.otter.model.SharedString;

public class ExampleSimpleClientGUI extends AbstractExampleClientGUI implements IncompatibleModelListener, Listener
{
  private static final String                  SHARED_STRING_ID = "info";

  private final ClientSubscriptionContainer					 _subscriptionContainer;
  private SharedString						_info;
  private JComponent						_rtTextContainer;
  private final CustomModel					_model;

  public ExampleSimpleClientGUI(
	ClientConnectionHandler connectionHandler,
	ClientSubscriptionContainer subscriptionContainer,
	CustomModel model)
  {
	super(connectionHandler, "RT Sync Simple Demo", new Dimension(800, 260));
	_subscriptionContainer = subscriptionContainer;
	_info = (SharedString) model.get(SHARED_STRING_ID, () -> model.getObject(SHARED_STRING_ID, "string"));
	subscriptionContainer.addWeakIncompatibleModelListener(this);
	_model = model;
	model.addChangeListener(this);
  }

  @Override
  protected void buildGUI()
  {
	super.buildGUI();

	_rtTextContainer = Box.createVerticalBox();
	createRTTxt();
	addLabelledComponent(1, 3, _rtTextContainer, "shared text:", _connectionPanel, c -> {
	  c.fill = GridBagConstraints.HORIZONTAL;
	  return c;
	});
	addLabelledComponent(4, 3, _receivedMessageArea, "last exception:", _connectionPanel, c -> {
	  c.gridwidth = 2;
	  c.gridheight = 2;
	  c.weightx = 1;
	  return c;
	});

	Box userOverViewBox = new RTUserOverviewBox(_connectionHandler, _subscriptionContainer);
	addLabelledComponent(1, 4, userOverViewBox, "users:", _connectionPanel);
	_frame.add(_connectionPanel);
  }

  private void createRTTxt()
  {
	JTextComponent rtTxt = new RTJTextPane(_info, _subscriptionContainer);
	rtTxt.setMinimumSize(new Dimension(200, 22));
	_rtTextContainer.removeAll();
	_rtTextContainer.add(rtTxt);
  }

  @Override
  public IncompatibleModelResolution onIncompatibleModelReceived(TaggedUserOperation taggedUserOperation)
  {
	return showResolveModelDialog("A model which is incompatible with the current local state has been received. Overwrite the local model? No means overwrite the remote model.");
  }

  @Override
  public void valueChanged(String key, Object oldValue, Object newValue)
  {
	if ( key.equals(SHARED_STRING_ID) )
	{
	  _info = (SharedString) _model.get(SHARED_STRING_ID, () -> _model.getObject(SHARED_STRING_ID, "string"));
	  SwingUtilities.invokeLater(() -> {
		createRTTxt();
		_connectionPanel.revalidate();
	  });
	}
  }

  @Override
  public void valueRemoved(String key, Object oldValue)
  {
	if ( key.equals(SHARED_STRING_ID) )
	{
	  _receivedMessageArea.setText("The shared string was removed!");
	}
  }
}
