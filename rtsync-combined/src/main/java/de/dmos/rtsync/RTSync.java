package de.dmos.rtsync;


import java.util.Arrays;

import de.dmos.rtsync.client.RTSyncProjectClient;
import de.dmos.rtsync.client.RTSyncSimpleClient;
import de.dmos.rtsync.server.project.RTSyncProjectServer;

public class RTSync
{
  public static void main(String[] args)
  {
	if (args.length < 1) {
	  System.out
	  .println(
		  "At least 1 parameter is required!. Provide either c, s, pc or ps as parameter.\n c  = Simple Client\n s  = Simple Server\n pc = Project Client\n ps = Project Server");
	  return;
	}
	String[] otherArgs = Arrays.copyOfRange(args, 1, args.length);
	switch (args[0]) {
	  case "c":
		RTSyncSimpleClient.main(otherArgs);
		break;
	  case "s":
		RTSyncProjectServer.main(otherArgs);
		break;
	  case "pc":
		RTSyncProjectClient.main(otherArgs);
		break;
	  case "ps":
		RTSyncProjectServer.main(otherArgs);
		break;
	  default:
		System.out
		.println("The first parameter must be either c, s, pc or ps");
	}
  }
}
