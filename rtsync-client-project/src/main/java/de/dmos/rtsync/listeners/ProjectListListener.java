package de.dmos.rtsync.listeners;

import java.util.List;

public interface ProjectListListener
{
  void onProjectListReceived(List<String> projects);
}
