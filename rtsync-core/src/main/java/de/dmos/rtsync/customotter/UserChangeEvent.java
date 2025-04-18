package de.dmos.rtsync.customotter;

import se.l4.otter.engine.events.ChangeEvent;

public class UserChangeEvent<T> extends ChangeEvent<T>
{
  private final String _user;

  public UserChangeEvent(T operation, boolean local, String user)
  {
	super(operation, local);
	_user = user;
  }

  public String getUser()
  {
	return _user;
  }
}
