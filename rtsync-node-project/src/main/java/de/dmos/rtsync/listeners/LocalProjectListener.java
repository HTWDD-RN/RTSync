package de.dmos.rtsync.listeners;

import de.dmos.rtsync.project.RTProjectData;

public interface LocalProjectListener<T extends RTProjectData>
{
  /**
   * Called when a local project is created. Note, that its RTProjectData has no TaggedUserOperation when this is
   * called. But this event can be used to update a list of locally available projects.
   */
  void localProjectCreated(T project);

  default void localProjectClosed(T project)
  {
  }
}
