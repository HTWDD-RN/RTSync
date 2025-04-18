package de.dmos.rtsync.test;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;

import se.l4.otter.model.SharedMap;

public class SharedMapUpdateListener implements SharedMap.Listener
{
  List<Triple<String, Object, Object>> _updates = new ArrayList<>();

  public List<Triple<String, Object, Object>> getUpdates()
  {
    return _updates;
  }

  @Override
  public void valueRemoved(String key, Object oldValue)
  {
    _updates.add(Triple.of(key, oldValue, null));
  }

  @Override
  public void valueChanged(String key, Object oldValue, Object newValue)
  {
    _updates.add(Triple.of(key, oldValue, newValue));
  }
}