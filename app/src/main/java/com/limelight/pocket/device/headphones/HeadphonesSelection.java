package com.limelight.pocket.device.headphones;

import java.util.List;
import com.limelight.pocket.device.HorizonBudsPolicy;

/** Auto-selection is limited to one named paired candidate with an available audio endpoint.
 * A name is a hint; the transport always verifies model ID on the selected socket. */
public final class HeadphonesSelection {
    private HeadphonesSelection() {}
    static int automatic(List<String> names, List<Boolean> audioAvailable) {
        if (names == null || audioAvailable == null || names.size() != audioAvailable.size()) return -1;
        int match = -1;
        for (int i = 0; i < names.size(); i++) {
            if (HorizonBudsPolicy.matches7i(names.get(i)) && Boolean.TRUE.equals(audioAvailable.get(i))) {
                if (match >= 0) return -1;
                match = i;
            }
        }
        return match;
    }
}
