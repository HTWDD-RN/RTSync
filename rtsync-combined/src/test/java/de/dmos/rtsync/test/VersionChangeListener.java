package de.dmos.rtsync.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.l4.otter.model.Model;
import se.l4.otter.model.SharedMap;
import se.l4.otter.model.SharedString;

public class VersionChangeListener implements SharedMap.Listener
{
  private static final Logger LOG				= LoggerFactory.getLogger(VersionChangeListener.class);
  private String              _replacedOldValue = null;

  @Override
  public void valueRemoved(String key, Object oldValue)
  {
	if ( oldValue instanceof SharedString oldSharedString)
	{
	  LOG.debug("valueRemoved {}: {}", key, oldSharedString.get());
	}
  }

  @Override
  public void valueChanged(String key, Object oldValue, Object newValue)
  {
	if ( newValue instanceof SharedString newSharedString && oldValue instanceof SharedString oldSharedString )
	{
	  LOG
	  .debug(
		"valueChanged {}: {} -> {}",
		key,
		oldSharedString.get(),
		newSharedString.get());
	  _replacedOldValue = oldSharedString.get();
	}
  }

  public String getReplacedOldValue()
  {
	return _replacedOldValue;
  }

  public static VersionChangeListener addAlternativeVersionListener(Model model)
  {
	VersionChangeListener versionListener = new VersionChangeListener();
	model.addChangeListener(versionListener);
	return versionListener;
  }
}