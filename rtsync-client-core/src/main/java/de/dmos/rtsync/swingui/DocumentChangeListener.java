package de.dmos.rtsync.swingui;

import java.util.function.Consumer;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A simplified DocumentListener which groups all kinds of document udpate into one.
 *
 * Found on https://stackoverflow.com/questions/3953208/value-change-listener-to-jtextfield
 */
public interface DocumentChangeListener extends Consumer<DocumentEvent>, DocumentListener
{
  @Override
  default void insertUpdate(DocumentEvent e)
  {
	accept(e);
  }

  @Override
  default void removeUpdate(DocumentEvent e)
  {
	accept(e);
  }

  @Override
  default void changedUpdate(DocumentEvent e)
  {
	accept(e);
  }
}