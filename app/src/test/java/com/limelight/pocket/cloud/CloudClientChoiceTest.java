package com.limelight.pocket.cloud;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CloudClientChoiceTest {
    private final CloudClients.Client app = new CloudClients.Client(CloudClients.APP,
            CloudClients.BETTER_XCLOUD_ANDROID, "Better xCloud for Android");
    private final CloudClients.Client builtIn = new CloudClients.Client(CloudClients.BUILT_IN, null, "Built-in player");

    @Test public void installingOrRemovingTheAndroidWrapperKeepsAnAvailableFallback() {
        assertSame(builtIn, CloudClients.choose(Arrays.asList(app, builtIn), null, false));
        assertSame(builtIn, CloudClients.choose(Arrays.asList(builtIn), app.id(), false));
        assertSame(app, CloudClients.choose(Arrays.asList(app, builtIn), app.id(), true));
    }

    @Test public void theExistingDefaultAndBuiltInEnhancementKeepTheirPriorities() {
        CloudClients.Client browser = new CloudClients.Client(CloudClients.BROWSER, "browser", "Browser");
        assertSame(browser, CloudClients.choose(Arrays.asList(browser, app, builtIn), null, false));
        assertSame(builtIn, CloudClients.choose(Arrays.asList(browser, app, builtIn), null, true));
        assertSame(browser, CloudClients.choose(Arrays.asList(browser, app, builtIn), browser.id(), true));
    }

    @Test public void openingAnAppAloneCannotRecordASpecificTitle() {
        HorizonCloudDispatch.Result opened = HorizonCloudDispatch.Result.EXTERNAL_APP_DISPATCHED;
        assertTrue(opened.dispatched());
        assertTrue(opened.dispatchedRequestedTarget(false));
        assertFalse(opened.dispatchedRequestedTarget(true));
        assertFalse(opened.gameplayConfirmed());
        assertTrue(HorizonCloudDispatch.Result.EXTERNAL_LINK_DISPATCHED.dispatchedRequestedTarget(true));
        assertFalse(HorizonCloudDispatch.Result.REJECTED.dispatchedRequestedTarget(false));
    }
}
