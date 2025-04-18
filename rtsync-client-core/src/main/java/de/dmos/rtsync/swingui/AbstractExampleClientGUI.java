package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import de.dmos.rtsync.client.ClientConnectionHandler;
import de.dmos.rtsync.client.ConnectionState;
import de.dmos.rtsync.listeners.ConnectionListener;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import jakarta.annotation.PreDestroy;

public abstract class AbstractExampleClientGUI implements ConnectionListener
{
  protected static final Insets INSETS = new Insets(4, 4, 4, 4);
  protected final ClientConnectionHandler _connectionHandler;
  private final String					  _title;
  private final Dimension				  _dimension;

  protected JFrame						_frame;
  private JButton											 _connectButton;
  protected JTextArea					_receivedMessageArea;
  private JTextField										 _serverTxt;
  private JLabel											 _connectionStatusLabel;
  protected ConnectionState				  _currentConnectionState;
  private JButton											 _colorButton;
  protected JPanel						_connectionPanel;

  protected AbstractExampleClientGUI(ClientConnectionHandler connectionHandler, String title, Dimension dimension)
  {
	_connectionHandler = connectionHandler;
	_title = title;
	_dimension = dimension;
	_currentConnectionState = ConnectionState.NOT_CONNECTED;
	connectionHandler.addConnectionListener(this);
  }

  public void start()
  {
	SwingUtilities.invokeLater(this::buildGUI);
  }

  public void connect()
  {
	URI uri = null;
	try
	{
	  uri = new URI(_serverTxt.getText());
	}
	catch (URISyntaxException uriEx)
	{
	  uriEx.printStackTrace();
	  _receivedMessageArea.setText(uriEx.getMessage());
	}

	if ( uri != null )
	{
	  _connectionHandler.startSynchronizingWithServer(uri);
	}
  }

  protected void changeName(String newName)
  {
	_connectionHandler.setPreferredName(newName == null || newName.isEmpty() ? null : newName);
  }

  protected void disconnect()
  {
	if ( _currentConnectionState != ConnectionState.NOT_CONNECTED )
	{
	  _connectionHandler.stopSynchronizingWithServer();
	}
  }

  protected void buildGUI()
  {
	int textFieldColumns = 50;
	int txtWidth = 200;
	_frame = new JFrame(_title);
	_frame.setSize(_dimension);
	_frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	_frame.setLocationByPlatform(true);

	GridBagLayout layout = new GridBagLayout();
	_connectionPanel = new JPanel(layout);
	_connectionPanel.setAlignmentY(0);

	for ( int i = 1; i <= 3; i++ )
	{
	  addComponent(_connectionPanel, new JLabel("     "), 3, i);
	}

	_serverTxt = new JTextField("ws://localhost:8080/socket", textFieldColumns);
	_serverTxt.setMinimumSize(new Dimension(txtWidth, 26));
	addLabelledComponent(1, 1, _serverTxt, "Server:", _connectionPanel);

	JTextField nameTxt = new JTextField(_connectionHandler.getPreferredName(), textFieldColumns);
	addLabelledComponent(4, 1, nameTxt, "preferred name (optional):", _connectionPanel, setWeightX);
	JButton setNameButton = new JButton("Change Name");
	setNameButton.addActionListener(a -> changeName(nameTxt.getText()));
	addComponent(_connectionPanel, setNameButton, 6, 1);

	_connectButton = new JButton("connect");
	_connectionStatusLabel = addLabelledComponent(1, 2, _connectButton, null, _connectionPanel);

	_colorButton = new JButton();
	_colorButton.setBackground(_connectionHandler.getPreferredColor());
	addLabelledComponent(4, 2, _colorButton, "preferred color (optional):", _connectionPanel);
	_colorButton.addActionListener(a -> displayColorChooser(_frame));

	_receivedMessageArea = new JTextArea("");
	_receivedMessageArea.setLineWrap(true);
	_receivedMessageArea.setWrapStyleWord(true);
	_receivedMessageArea.setEditable(false);

	_connectButton.addActionListener(a -> {
	  if ( _currentConnectionState == ConnectionState.NOT_CONNECTED )
	  {
		connect();
	  }
	  else
	  {
		disconnect();
	  }
	});

	//	_frame.pack();
	onConnectionUpdated();
	_frame.setVisible(true);
  }

  private final ConstraintChanger setWeightX = c -> {
	c.weightx = 1;
	return c;
  };

  protected static interface ConstraintChanger
  {
	GridBagConstraints changeConstraints(GridBagConstraints constraints);
  }

  protected void addComponent(JPanel panel, JComponent component, int column, int row)
  {
	panel.add(component, getConstraints(column, row));
  }

  protected JLabel addLabelledComponent(int column, int row, JComponent component, String label, JPanel panel)
  {
	return addLabelledComponent(column, row, component, label, panel, null);
  }

  protected JLabel addLabelledComponent(
	int column,
	int row,
	JComponent component,
	String label,
	JPanel panel,
	ConstraintChanger cChanger)
  {
	GridBagConstraints c = getConstraints(column, row);
	JLabel jLabel = new JLabel(label);
	jLabel.setLabelFor(component);
	panel.add(jLabel, c);
	c.gridx += 1;
	c.gridwidth = 1;
	if ( cChanger != null )
	{
	  c = cChanger.changeConstraints(c);
	}
	panel.add(component, c);
	return jLabel;
  }

  protected GridBagConstraints getConstraints(int column, int row)
  {
	GridBagConstraints c = new GridBagConstraints();
	c.gridx = column - 1;
	c.gridy = row - 1;
	c.fill = GridBagConstraints.BOTH;
	c.insets = INSETS;
	return c;
  }

  protected void onConnectionUpdated()
  {
	_connectionStatusLabel.setText(_currentConnectionState.toString());
	_serverTxt.setEnabled(_currentConnectionState == ConnectionState.NOT_CONNECTED);
	if ( _currentConnectionState != ConnectionState.NOT_CONNECTED )
	{
	  _connectButton.setText("disconnect");
	}
	else
	{
	  _connectButton.setText("connect");
	}
  }

  @Override
  public void onConnectionStateChanged(ConnectionState currentState, Throwable throwable)
  {
	_currentConnectionState = currentState;
	_receivedMessageArea.setText(throwable != null ? throwable.getLocalizedMessage() : "");
	onConnectionUpdated();
  }

  protected void displayColorChooser(Component parent)
  {
	Color color =
		JColorChooser.showDialog(parent, "Choose your preferred color", _connectionHandler.getPreferredColor());
	_connectionHandler.setPreferredColor(color);
	_colorButton.setBackground(color);
  }

  @Override
  public void onException(Throwable throwable)
  {
	_receivedMessageArea.setText(throwable.getLocalizedMessage());
  }

  public IncompatibleModelResolution showResolveModelDialog(String text)
  {
	int dialogResult =
		JOptionPane.showConfirmDialog(_frame, text, "Incompatible Model State", JOptionPane.YES_NO_CANCEL_OPTION);
	switch (dialogResult)
	{
	  case JOptionPane.YES_OPTION:
		return IncompatibleModelResolution.OVERWRITE_LOCAL_CHANGES;
	  case JOptionPane.NO_OPTION:
		return IncompatibleModelResolution.OVERWRITE_REMOTE_CHANGES;
	  default:
		return IncompatibleModelResolution.NONE;
	}
  }

  @PreDestroy
  void close()
  {
	_connectionHandler.stopSynchronizingWithServer();
  }
}
