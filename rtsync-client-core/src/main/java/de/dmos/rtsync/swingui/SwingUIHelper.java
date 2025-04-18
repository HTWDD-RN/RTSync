package de.dmos.rtsync.swingui;

import javax.swing.JComponent;

public class SwingUIHelper
{
  private SwingUIHelper()
  {
  }

  public static void revalidateTopComponent(JComponent component)
  {
	JComponent root = component.getRootPane();
	JComponent componentToValidate = root != null ? root : component;
	componentToValidate.revalidate();
  }
}
