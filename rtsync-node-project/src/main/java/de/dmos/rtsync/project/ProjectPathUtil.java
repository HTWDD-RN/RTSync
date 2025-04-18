package de.dmos.rtsync.project;

import de.dmos.rtsync.network.EndpointPaths;

public class ProjectPathUtil
{
  private ProjectPathUtil()
  {
  }

  public static String getProjectFromDestination(String destination)
  {
	if ( destination == null )
	{
	  return null;
	}
	String[] headerParts = destination.split(EndpointPaths.DELIMITER);
	if ( headerParts.length < 2 )
	{
	  return null;
	}
	return headerParts[headerParts.length - 2];
  }
}
