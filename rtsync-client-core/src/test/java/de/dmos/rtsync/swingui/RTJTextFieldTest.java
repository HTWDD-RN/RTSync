//package de.dmos.rtsync.swingui;
//
//import java.lang.reflect.InvocationTargetException;
//
//import javax.swing.text.JTextComponent;
//
//import org.junit.jupiter.api.Test;
//
//import se.l4.otter.model.SharedString;
//
//class RTJTextFieldTest extends RTJTextComponentTest
//{
//  @Override
//  JTextComponent createRTJTextComponent(SharedString sharedString)
//  {
//	return new RTJTextField(sharedString);
//  }
//
//  @Test
//  void updateSharedStringFromRTJTextFieldChangeTest() throws InvocationTargetException, InterruptedException
//  {
//	updateSharedStringFromTextComponentChange();
//  }
//
//  @Test
//  void updateRTJTextFieldFromSharedStringChangeTest() throws InvocationTargetException, InterruptedException
//  {
//	updateTextComponentFromSharedStringChange();
//  }
//
//  @Test
//  void changeTwoStringSharingTextFieldsTest() throws InvocationTargetException, InterruptedException
//  {
//	changeTwoStringSharingTextComponentsFields();
//  }
//
//  @Test
//  void remoteTextFieldChangeTest() throws InvocationTargetException, InterruptedException
//  {
//	remoteChange();
//  }
//}
