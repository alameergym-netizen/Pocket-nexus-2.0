package com.limelight.pocket.streaming;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonConnectStatusTest {
    @Test public void onlinePairedIsReady() {
        ComputerDetails pc = new ComputerDetails();
        pc.state = ComputerDetails.State.ONLINE;
        pc.pairState = PairingManager.PairState.PAIRED;
        assertEquals(HorizonConnectStatus.State.READY, HorizonConnectStatus.classify(pc));
    }
    @Test public void offlineOverridesPairing() {
        ComputerDetails pc = new ComputerDetails();
        pc.state = ComputerDetails.State.OFFLINE;
        pc.pairState = PairingManager.PairState.PAIRED;
        assertEquals(HorizonConnectStatus.State.OFFLINE, HorizonConnectStatus.classify(pc));
    }
    @Test public void unresolvedDiscoveryDoesNotClaimReadiness() {
        ComputerDetails pc = new ComputerDetails();
        assertEquals(HorizonConnectStatus.State.UNKNOWN, HorizonConnectStatus.classify(pc));
        pc.state = ComputerDetails.State.ONLINE;
        assertEquals(HorizonConnectStatus.State.UNKNOWN, HorizonConnectStatus.classify(pc));
        assertEquals(HorizonConnectStatus.State.UNKNOWN, HorizonConnectStatus.classify(null));
    }
    @Test public void nonPairedStatesRequirePairing() {
        ComputerDetails pc = new ComputerDetails();
        pc.state = ComputerDetails.State.ONLINE;
        for (PairingManager.PairState state : PairingManager.PairState.values()) {
            if (state == PairingManager.PairState.PAIRED) continue;
            pc.pairState = state;
            assertEquals(HorizonConnectStatus.State.PAIRING_REQUIRED, HorizonConnectStatus.classify(pc));
        }
    }
}
