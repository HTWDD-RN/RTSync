package de.dmos.rtsync.network;

/**
 * A place for all constants that are relevant for client, server and their communication and which have no better place
 * to fit in.
 */
public class CommunicationConstants
{
  private CommunicationConstants()
  {
  }

  public static final String                       SERVER_EXCEPTION_MESSAGE_START = "server exception: ";
  public static final String                       MESSAGE_START_OLD_HISTORY_ID   = "old historyId ";

  public static final String                     PREFERRED_NAME_HEADER    = "preferredName";
  public static final String                     PREFERRED_COLOR_HEADER   = "preferredColor";

  public static final boolean USE_SOCK_JS = false;
}
