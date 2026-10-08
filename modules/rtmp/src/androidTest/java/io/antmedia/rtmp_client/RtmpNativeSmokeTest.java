package io.antmedia.rtmp_client;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/** Exercises JNI loading and native calls without depending on a remote stream. */
@RunWith(AndroidJUnit4.class)
public class RtmpNativeSmokeTest {
    @Test public void unopenedClientCanQueryAndCloseRepeatedly() {
        RtmpClient client = new RtmpClient();
        assertFalse(client.isConnected());
        client.close();
        client.close();
        assertFalse(client.isConnected());
    }

    @Test public void readBeforeOpenReportsJavaException() throws Exception {
        RtmpClient client = new RtmpClient();
        try {
            client.read(new byte[16], 0, 16);
            fail("An unopened native client must reject reads");
        } catch (IllegalStateException expected) {
            // Also exercises native FindClass/ThrowNew with the original JNI names.
        } finally {
            client.close();
        }
    }
}
