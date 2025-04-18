package de.dmos.rtsync.client;

public record ProjectAvailability(String project, ProjectAvailabilityState availabilityState)
{
  public enum ProjectAvailabilityState implements Comparable<ProjectAvailabilityState>
  {
	LOCAL_AND_REMOTE, LOCAL, REMOTE;

	public boolean hasLocalRTProjectData()
	{
	  return !equals(REMOTE);
	}
  }

  public boolean projectEquals(String otherProject)
  {
	return project().equals(otherProject);
  }
}
