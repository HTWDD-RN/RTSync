package de.dmos.rtsync.client.internalinterfaces;

import org.springframework.messaging.simp.stomp.StompSession.Receiptable;

public interface ReceiptHandler<T extends Receiptable>
{
  void receiptReceived(T receipt);
}
