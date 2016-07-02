package net.drakefamily.esscanner;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

/**
 * A Simple Activity to contain the fragment which does all of
 * the work. All we do here is load it in.
 */
public class ESScannerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sdscanner);

        // Look for the fragment
        FragmentManager fm = getSupportFragmentManager();
        Fragment f = fm.findFragmentById(R.id.sdcanner_activity_fragment_container);

        if (f == null) {
            f = new ESScannerFragment();
            fm.beginTransaction()
                    .add(R.id.sdcanner_activity_fragment_container, f)
                    .commit();
        }
    }
}
