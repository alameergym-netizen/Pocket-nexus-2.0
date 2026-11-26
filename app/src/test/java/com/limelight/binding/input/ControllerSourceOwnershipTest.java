package com.limelight.binding.input;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.util.SparseArray;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.shadows.ShadowMoonBridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 33, application = Application.class, shadows = ShadowMoonBridge.class)
public class ControllerSourceOwnershipTest {
    private ActivityController<Activity> activity;
    private ControllerHandler handler;
    private NvConnection connection;
    private SparseArray<ControllerHandler.InputDeviceContext> physical;
    private SparseArray<ControllerHandler.UsbDeviceContext> usb;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup();
        connection = mock(NvConnection.class);
        handler = new ControllerHandler(activity.get(), connection, null, new PreferenceConfiguration());
        physical = ReflectionHelpers.getField(handler, "inputDeviceContexts");
        usb = ReflectionHelpers.getField(handler, "usbDeviceContexts");
    }

    @After public void tearDown() {
        if (handler != null) handler.destroy();
        activity.pause().stop().destroy();
    }

    private ControllerHandler.InputDeviceContext addPad(int id, int player) {
        ControllerHandler.InputDeviceContext pad = handler.new InputDeviceContext();
        pad.id = id;
        pad.controllerNumber = (short) player;
        pad.assignedControllerNumber = true;
        physical.put(id, pad);
        return pad;
    }

    @Test public void mappedReleaseBelongsToOriginalPlayerAndPreservesTheirPhysicalButton() {
        int flag = ControllerPacket.SPECIAL_BUTTON_FLAG;
        ControllerHandler.InputDeviceContext first = addPad(11, 0);
        ControllerHandler.InputDeviceContext second = addPad(22, 1);
        first.inputMap = second.inputMap = flag;
        ReflectionHelpers.setField(handler, "lastActiveContext", first);
        handler.setEmulatedFlag(flag, true);
        ReflectionHelpers.setField(handler, "lastActiveContext", second);
        clearInvocations(connection);
        handler.setEmulatedFlag(flag, false);
        shadowOf(Looper.getMainLooper()).idleFor(110, TimeUnit.MILLISECONDS);
        assertEquals(flag, first.inputMap);
        assertEquals(flag, second.inputMap);
        verify(connection).sendControllerInput((short) 0, (short) 1, flag, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0);
        verifyNoMoreInteractions(connection);
    }

    @Test public void onscreenReportsCannotOverwriteHeldMappedButtons() {
        handler.setEmulatedFlag(ControllerPacket.PLAY_FLAG, true);
        clearInvocations(connection);
        handler.reportOscState(ControllerPacket.A_FLAG, (short) 0, (short) 0, (short) 0, (short) 0, (byte) 0, (byte) 0);
        verify(connection).sendControllerInput((short) 0, (short) 1, ControllerPacket.A_FLAG | ControllerPacket.PLAY_FLAG,
                (byte) 0, (byte) 0, (short) 0, (short) 0, (short) 0, (short) 0);
    }

    @Test public void splitAxesChooseRealSignedStickAndUnsignedTriggerValuesAndReleaseTogether() {
        ControllerHandler.InputDeviceContext pad = addPad(11, 0);
        pad.leftStickX = -3000;
        pad.rightStickY = 0x1111;
        pad.leftTrigger = (byte) 180;
        ControllerHandler.UsbDeviceContext accessory = handler.new UsbDeviceContext();
        accessory.assignedControllerNumber = true;
        accessory.inputMap = ControllerPacket.B_FLAG;
        accessory.rightTrigger = (byte) 233;
        usb.put(33, accessory);
        handler.reportOscState(ControllerPacket.A_FLAG, (short) 6000, (short) 12, (short) 1024, (short) 0x2222,
                (byte) 110, (byte) 127);
        verify(connection).sendControllerInput((short) 0, (short) 1, ControllerPacket.A_FLAG | ControllerPacket.B_FLAG,
                (byte) 180, (byte) 233, (short) 6000, (short) 12, (short) 1024, (short) 0x2222);
        clearInvocations(connection);
        handler.releaseAllInputs();
        verify(connection, times(3)).sendControllerInput((short) 0, (short) 1, 0, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0);
        verifyNoMoreInteractions(connection);
    }
}
