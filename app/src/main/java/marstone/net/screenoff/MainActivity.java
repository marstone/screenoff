package marstone.net.screenoff;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    final String TAG = getClass().getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.activity_main);

        // boolean rooted = RootUtil.isDeviceRooted();
        // Log.i(TAG, "rooted:" + rooted);

        new Thread() {
            public void run() {
                screenOff();
            }
        }.start();
        // done
        finish();
    }


    private void screenOff() {

        RootHelper helper = new RootHelper();
        if (!helper.isRooted()) {
            // startActivity(new Intent(getBaseContext(), RootErrorActivity.class));
            Toast.makeText(this, "failed to get root.", Toast.LENGTH_SHORT).show();
        }
        try {
            helper.sudoAndExit("input keyevent 26", 100);
        } catch (Exception localException) {
            Log.e(this.TAG, localException.getLocalizedMessage());
        }
        helper.tryExitAndClean();
    }

}
