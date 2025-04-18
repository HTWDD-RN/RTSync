package de.dmos.rtsync.swingui;

import java.lang.reflect.InvocationTargetException;

import javax.swing.text.JTextComponent;

import org.junit.jupiter.api.Test;

import se.l4.otter.model.SharedString;

class RTJTextPaneTest extends RTJTextComponentTest
{
  @Override
  JTextComponent createRTJTextComponent(SharedString sharedString)
  {
	return new RTJTextPane(sharedString);
  }

  @Test
  void updateSharedStringFromTextPaneChangeTest() throws InvocationTargetException, InterruptedException
  {
	updateSharedStringFromTextComponentChange();
  }

  @Test
  void updateTextPaneFromSharedStringChangeTest() throws InvocationTargetException, InterruptedException
  {
	updateTextComponentFromSharedStringChange();
  }

  @Test
  void changeTwoStringSharingTextPanesTest() throws InvocationTargetException, InterruptedException
  {
	changeTwoStringSharingTextComponentsFields();
  }

  @Test
  void remoteChangeTextPaneTest() throws InvocationTargetException, InterruptedException
  {
	remoteChange();
  }
}
