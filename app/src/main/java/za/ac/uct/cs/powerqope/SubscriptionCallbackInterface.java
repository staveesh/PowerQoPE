package za.ac.uct.cs.powerqope;

import ua.naiksoftware.stomp.dto.StompMessage;

public interface SubscriptionCallbackInterface {
    void onSubscriptionResult(StompMessage result);
}
