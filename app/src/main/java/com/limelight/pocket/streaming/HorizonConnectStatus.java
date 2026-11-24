package com.limelight.pocket.streaming;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

/** Discovery status only. READY means streaming host online and paired, not an active session. */
public final class HorizonConnectStatus {
    public enum State { READY, PAIRING_REQUIRED, OFFLINE, UNKNOWN }
    private HorizonConnectStatus() {}
    public static State classify(ComputerDetails details) {
        if (details == null || details.state == null) return State.UNKNOWN;
        if (details.state == ComputerDetails.State.OFFLINE) return State.OFFLINE;
        if (details.state != ComputerDetails.State.ONLINE) return State.UNKNOWN;
        if (details.pairState == PairingManager.PairState.PAIRED) return State.READY;
        if (details.pairState == null) return State.UNKNOWN;
        return State.PAIRING_REQUIRED;
    }
}
