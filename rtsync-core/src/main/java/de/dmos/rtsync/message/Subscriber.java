package de.dmos.rtsync.message;

import java.awt.Color;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class Subscriber implements Serializable
{
  /**
   * docme: serialVersionUID
   */
  private static final long	 serialVersionUID = 1L;

  private final long   _id;
  private String			 _name;
  private Color				 _color;
  private final Set<String>	_projects;

  public Subscriber(long id, String name, Color color)
  {
	this(id, name, color, null);
  }

  public Subscriber(long id, String name, Color color, Set<String> projects)
  {
	_id = id;
	_name = name;
	_color = color;
	_projects = projects != null ? projects : new HashSet<>();
  }

  public String getName()
  {
	return _name;
  }

  public void setName(String newName)
  {
	_name = newName;
  }

  public void setColor(Color color)
  {
	_color = color;
  }

  public Color getColor()
  {
	return _color;
  }

  public Set<String> getProjects()
  {
	return _projects;
  }

  public long getId()
  {
	return _id;
  }

  public void addProject(String project)
  {
	_projects.add(project);
  }

  public boolean removeProject(String project)
  {
	return _projects.remove(project);
  }

  @Override
  public boolean equals(Object obj)
  {
	if ( obj == null || obj.getClass() != getClass() )
	{
	  return false;
	}
	Subscriber sub = (Subscriber) obj;
	if ( _id != sub.getId() || !_name.equals(sub.getName()) || !_color.equals(sub.getColor()) )
	{
	  return false;
	}
	return _projects.equals(sub.getProjects());
  }
}
