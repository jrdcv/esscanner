package net.drakefamily.esscanner;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jrd on 6/29/16.
 *
 * Encapsulate logic to allow the user to scan external storage
 * and retrieve information about files there. Using a fragment
 * allows us to retain the instance state and thus simplify handling
 * changes in device rotation, etc.
 *
 * The user can initiate a scan of external storage. The scan is
 * handled by an AsyncTask which lets us update the UI on the main
 * thread. The scan can be stopped by user action or by the "back"
 * button. In the former case the partial scan results will be
 * shown.
 */
public class ESScannerFragment extends Fragment {

    // This works since we are not a production app
    private static String TAG = ESScannerFragment.class.getSimpleName();

    // ID for use with the NotificationManager
    private static final int NOTIFICATION_ID = 5305;

    // Some convenient constants
    private int MAX_FILESIZE_COUNT = 10;
    private int MAX_SUFFIX_COUNT = 5;

    // Keep an handle to our task to scan the storage area. This
    // lets us avoid multiple, redundant AsyncTask instances.
    private Scannertask mScannertask;

    // Show the progress as we go.
    private TextView mScanProgressText;
    private ProgressBar mScanProgressBar;
    private int mScanPercentComplete = 0;

    // Keep a count of how many files we find
    private long mScannerFilesFound = 0;

    // Controls to start & stop scanning
    private Button mStartButton;
    private Button mStopButton;

    // Widgets to show scan results
    private TextView mSizeScanEmptyTextView;
    private TextView mSuffixScanEmptyTextView;
    private TextView[] mSizeScanResultTexts = new TextView[10];
    private TextView[] mSuffixScanResultTexts = new TextView[5];
    private TextView mAverageSizeEmptyTextView;
    private TextView mAverageSizeResultTextView;

    // Arrays to save scan results across config changes
    private String[] mSizeScanResults = null;
    private String[] mSuffixScanResults = null;
    private long mAverageSizeResults = 0L;

    // Button to share results
    private Button mShareButton;

    private boolean isAttached = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_sdscanner_main, container, false);

        mScanProgressText = (TextView)v.findViewById(R.id.fragment_sdcanner_progress_label);
        mScanProgressText.setText(String.format(getResources().getString(R.string.scan_status_label),
                mScanPercentComplete));

        mStartButton = (Button)v.findViewById(R.id.fragment_sdscanner_button_start);
        mStopButton = (Button)v.findViewById(R.id.fragment_sdscanner_button_stop);

        // Set listeners to do appropriate UI changes
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Make sure we can do the scan in the first place
                int permission = ActivityCompat.checkSelfPermission(getActivity(),
                        Manifest.permission.READ_EXTERNAL_STORAGE);
                if (permission != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "no permission to read");
                    AlertDialog ad = new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.error_dialog_title)
                            .setMessage(R.string.error_dialog_text)
                            .setPositiveButton(android.R.string.ok, null)
                            .create();
                    ad.show();
                } else {
                    // Hey, ho! Let's go.
                    mScanPercentComplete = 0;
                    mSizeScanResults = mSuffixScanResults = null;
                    mAverageSizeResults = 0L;
                    mScannertask = new Scannertask();
                    mScannertask.execute();
                    updateUI();
                    createNotification();
                }
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mScannertask != null) {
                    // Cancelling the task will remove the notification
                    mScannertask.cancel(false);
                    mScannertask = null;
                }
                updateUI();
            }
        });

        mScanProgressBar = (ProgressBar)v.findViewById(R.id.fragment_sdscanner_progress_bar);
        mScanProgressBar.setMax(100);

        // Text items for showing results. I opted not to use ListViews for the data,
        // because there is no user interaction and the amount of data we are going
        // to show is known. This seemed reasonable but leads to some redundancy
        // in the code, and meant that the fragment itself had to be wrapped in a
        // ScrollView to allow for device orientation changes and the size of the
        // output.
        mSizeScanEmptyTextView = (TextView)v.findViewById(R.id.empty_largest_files_item);
        mSizeScanResultTexts[0] = (TextView)v.findViewById(R.id.largest_files_item0);
        mSizeScanResultTexts[1] = (TextView)v.findViewById(R.id.largest_files_item1);
        mSizeScanResultTexts[2] = (TextView)v.findViewById(R.id.largest_files_item2);
        mSizeScanResultTexts[3] = (TextView)v.findViewById(R.id.largest_files_item3);
        mSizeScanResultTexts[4] = (TextView)v.findViewById(R.id.largest_files_item4);
        mSizeScanResultTexts[5] = (TextView)v.findViewById(R.id.largest_files_item5);
        mSizeScanResultTexts[6] = (TextView)v.findViewById(R.id.largest_files_item6);
        mSizeScanResultTexts[7] = (TextView)v.findViewById(R.id.largest_files_item7);
        mSizeScanResultTexts[8] = (TextView)v.findViewById(R.id.largest_files_item8);
        mSizeScanResultTexts[9] = (TextView)v.findViewById(R.id.largest_files_item9);

        mSuffixScanEmptyTextView = (TextView)v.findViewById(R.id.empty_common_extensions_item);
        mSuffixScanResultTexts[0] = (TextView)v.findViewById(R.id.common_extensions_item0);
        mSuffixScanResultTexts[1] = (TextView)v.findViewById(R.id.common_extensions_item1);
        mSuffixScanResultTexts[2] = (TextView)v.findViewById(R.id.common_extensions_item2);
        mSuffixScanResultTexts[3] = (TextView)v.findViewById(R.id.common_extensions_item3);
        mSuffixScanResultTexts[4] = (TextView)v.findViewById(R.id.common_extensions_item4);

        mAverageSizeEmptyTextView = (TextView)v.findViewById(R.id.empty_average_size_item);
        mAverageSizeResultTextView = (TextView)v.findViewById(R.id.average_size_item);

        // The button to share the results
        mShareButton = (Button)v.findViewById(R.id.fragment_sdscanner_share_button);
        mShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, scanResultsToString());
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_results_title));
                i = Intent.createChooser(i, getString(R.string.share_results_chooser_text));
                startActivity(i);
            }
        });

        updateUI();

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        isAttached = true;
    }

    @Override
    /**
     * When the fragment is destroyed, ensure that the ScannerTask is
     * killed in an unkind way, rather than just telling it to stop.
     * Normally I believe cancelling the task in this fashion is not
     * encouraged, but it is the closest we can get to ensuring that
     * the scan is stopped "immediately," per the app requirement.
     */
    public void onDestroy() {
        super.onDestroy();
        if (mScannertask != null) {
            Log.d(TAG, "onDestroy, cancel the scan with extreme prejudice");
            mScannertask.cancel(true);
            mScannertask = null;
        }
    }

    @Override
    /**
     * Record that we have no activity so that we don't try to update
     * the UI when the app is shutting down.
     */
    public void onDetach() {
        super.onDetach();
        isAttached = false;
    }

    /**
     * Create a Notification so that the system informs the user the
     * scan is taking place.
     */
    private void createNotification() {
        Resources resources = getResources();

        Notification notification = new NotificationCompat.Builder(getActivity())
                .setTicker(resources.getText(R.string.notification_title))
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setContentTitle(resources.getText(R.string.notification_title))
                .setContentText(resources.getText(R.string.notification_text))
                .build();

        NotificationManagerCompat manager =
                NotificationManagerCompat.from(getActivity());
        manager.notify(NOTIFICATION_ID, notification);
    }

    /**
     * When the scan is done, have the system remove the Notification that
     * it is in progress.
     */
    private void cancelNotification() {
        NotificationManagerCompat.from(getActivity()).cancel(NOTIFICATION_ID);
    }

    /**
     * Set the UI pieces based on the current state. Do so only if we
     * have an Activity.
     */
    private void updateUI() {
        if (isAttached) {
            Resources res = getResources();
            mScanProgressText.setText(String.format(res.getString(R.string.scan_status_label),
                    mScanPercentComplete));
            mScanProgressBar.setProgress(mScanPercentComplete);

            mStartButton.setEnabled(mScannertask == null);
            mStopButton.setEnabled(mScannertask != null);

            // Update filesize TextView items
            if (mSizeScanResults != null) {
                for (int i = 0; i < mSizeScanResultTexts.length; i++) {
                    if (mSizeScanResults[i] != null) {
                        mSizeScanResultTexts[i].setText(mSizeScanResults[i]);
                        mSizeScanResultTexts[i].setVisibility(View.VISIBLE);
                    } else {
                        mSizeScanResultTexts[i].setVisibility(View.GONE);
                    }
                }
                mSizeScanEmptyTextView.setVisibility(View.GONE);
            } else {
                mSizeScanEmptyTextView.setVisibility(View.VISIBLE);
                for(TextView tv : mSizeScanResultTexts) {
                    tv.setVisibility(View.GONE);
                }
            }

            if (mSuffixScanResults != null) {
                for (int i = 0; i < mSuffixScanResultTexts.length; i++) {
                    if (mSuffixScanResults[i] != null) {
                        mSuffixScanResultTexts[i].setText(mSuffixScanResults[i]);
                        mSuffixScanResultTexts[i].setVisibility(View.VISIBLE);
                    } else {
                        mSuffixScanResultTexts[i].setVisibility(View.GONE);
                    }
                }
                mSuffixScanEmptyTextView.setVisibility(View.GONE);
            } else {
                mSuffixScanEmptyTextView.setVisibility(View.VISIBLE);
                for(TextView tv : mSuffixScanResultTexts) {
                    tv.setVisibility(View.GONE);
                }
            }

            // If our average size is 0, assume we had no results to show
            mAverageSizeResultTextView.setText(Long.toString(mAverageSizeResults));
            if (mAverageSizeResults == 0L) {
                mAverageSizeEmptyTextView.setVisibility(View.VISIBLE);
                mAverageSizeResultTextView.setVisibility(View.GONE);
            } else {
                mAverageSizeEmptyTextView.setVisibility(View.GONE);
                mAverageSizeResultTextView.setVisibility(View.VISIBLE);
            }

            // If we have results and no scan is running, enable button to
            // share them.
            mShareButton.setEnabled(mScannertask == null && mSuffixScanResults != null);
        }
    }

    /**
     * Produce a textual summary of the scan results suitable for sharing.
     * @return  The scan summary to share
     */
    private String scanResultsToString() {

        StringBuffer sb = new StringBuffer(getString(R.string.share_results_1));
        if (mSizeScanResults == null) {
            sb.append(getString(R.string.empty_list_text));
        } else {
            for (int i = 0; i < mSizeScanResults.length; i++) {
                sb.append(mSizeScanResults[i]).append("\n");
            }
        }
        sb.append(getString(R.string.share_results_2));
        if (mSuffixScanResults == null) {
            sb.append(getString(R.string.empty_list_text));
        } else {
            for (int i = 0; i < mSuffixScanResults.length; i++) {
                sb.append(mSuffixScanResults[i]).append("\n");
            }
        }
        sb.append(getString(R.string.share_results_3)).append(mAverageSizeResults).append("\n\n");
        return sb.toString();
    }

    /**
     * Encapsulate the association between a file and its size.
     */
    private class FileSizeInfo {
        String fileName;
        long fileSize;

        FileSizeInfo(String name, long size) {
            fileName = name;
            fileSize = size;
        }

        @Override
        /**
         * For ease in debugging
         */
        public String toString() {
            return "{"+fileName+","+fileSize+"}";
        }
    }

    /**
     * Encapsulate the association between file extensions and the total
     * number of times they are encounrted. Implement  the <code>Comparable</code>
     * interface to allow us to sort by value in an HashMap.
     */
    private class ExtensionCountInfo implements Comparable<ExtensionCountInfo> {
        String extension;
        int count;

        ExtensionCountInfo(String extension, int count) {
            this.extension = extension;
            this.count = count;
        }

        public int compareTo(ExtensionCountInfo other) {
            return other.count - count;
        }

        @Override
        /**
         * For ease in debugging.
         */
        public String toString() {
            return "{"+extension+"="+count+"}";
        }
    }

    /**
     * Define how we will return the results of a scan from AsyncTask.
     */
    private class ScanResults {
        FileSizeInfo[] bigFiles;
        ExtensionCountInfo[] commonExtensions;
        long averageSize;
    }

    // Internal class to allow us to scan the external storage and update
    // the UI as we go.
    private class Scannertask extends AsyncTask<Void, Integer, ScanResults> {

        // Some handy constants we will need
        private char SUFFIX_DELIMETER = '.';

        //private Map<String, Integer> largestFiles = new HashMap<String, Integer>(10);
        private ArrayList<FileSizeInfo> largestFiles = new ArrayList<FileSizeInfo>(MAX_FILESIZE_COUNT);
        private Map<String, Integer> commonExtensions = new HashMap<String, Integer>();
        private long averageSize = 0L;

        // Look at the external storage and see what's there
        @Override
        protected ScanResults doInBackground(Void... voids) {

            // Extremely remote, but make sure external storage is mounted.
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
                File file = Environment.getExternalStorageDirectory();

                // We scan twice, once to see how many files we have to look at
                // (so that we can report progress), and then once looking at
                // the files individually.
                // TODO:  Merge methods. The methods to calculate the number of
                // files and the to actually inspect them involve a large amount
                // of code duplication which should be reduced.

                // First see how many how files we have so we can report progress
                mScannerFilesFound = countNodes(file, 0);
                Log.d(TAG, "mScannerFilesFound:  " + mScannerFilesFound);

                // Now examine each file and record size and extension
                scanDirectory(file, 0);

                // Now we have results. The user may have stopped us in mid-run,
                // so interpret results even if we were cancelled.
                ScanResults results = new ScanResults();
                results.bigFiles = largestFiles.toArray(new FileSizeInfo[10]);
                results.commonExtensions = sortExtensionMap(commonExtensions);
                results.averageSize = averageSize;
                return results;
            }
            return null;
        }

        @Override
        /**
         * Track scan progress on the main thread.
         */
        protected void onProgressUpdate(Integer... values) {
            mScanPercentComplete = values[0];
            updateUI();
        }

        @Override
        /**
         * Interpret the results of a complete scan
         */
        protected void onPostExecute(ScanResults scanResults) {
            cancelNotification();
            interpretResults(scanResults);
        }

        @Override
        /**
         * If the user cancelled us, give a partial result.
         */
        protected void onCancelled(ScanResults scanResults) {
            cancelNotification();
            interpretResults(scanResults);
        }

        /**
         * Look at what the scan found and update the UI
         * appropriate
         * @param results The return from <code>doInBackground()</code>
         */
        private void interpretResults(ScanResults results) {
            if (results != null) {
                mSizeScanResults = new String[MAX_FILESIZE_COUNT];
                for (int i = 0; i < results.bigFiles.length; i++) {
                    mSizeScanResults[i] = results.bigFiles[i].fileName +
                            ":\t\t" + results.bigFiles[i].fileSize;
                }
                mSuffixScanResults = new String[MAX_SUFFIX_COUNT];
                for (int i = 0; i < results.commonExtensions.length; i++) {
                    mSuffixScanResults[i] = results.commonExtensions[i].extension +
                            ":\t\t" + results.commonExtensions[i].count;
                }
                mAverageSizeResults = results.averageSize;
            } else {
                // No results to display
                mSizeScanResults = mSuffixScanResults = null;
                mAverageSizeResults = 0L;
            }
            updateUI();
        }

        /**
         * We have accumulated information about file extensions using an
         * HashMap, but we need to sort it by value rather than key to find
         * the most frequently occurring extensions, and then return the
         * five most frequent. We'll sort in descending order and then chop
         * off the first five elements and return them in an array. Note that
         * many files have no suffix, in which case we use "<None>" as the
         * value.
         * @param map The suffix frequency information from the scan
         * @return An array of the most common five file suffix values, most
         *         frequent first.
         */
        private ExtensionCountInfo[] sortExtensionMap(Map<String, Integer> map) {
            List toSort = new ArrayList<ExtensionCountInfo>(map.size());

            for (String key : map.keySet()) {
                ExtensionCountInfo eci = new ExtensionCountInfo(key, map.get(key));
                toSort.add(eci);
            }

            // Now sort the ArrayList. The new ArrayList knows how to sort
            // itself.
            Collections.sort(toSort);

            List<ExtensionCountInfo> firstFive = toSort.subList(0, MAX_SUFFIX_COUNT);
            return firstFive.toArray(new ExtensionCountInfo[MAX_SUFFIX_COUNT]);
        }

        /**
         * Count the number of files to scan in this (and any subdirectory).
         * @param f Origin directory from which we recurse
         * @param currentCount  The total count of files so far
         * @return A running total of the file count
         */
        private long countNodes(File f, long currentCount) {

            // Be safe
            if (f == null) {
                Log.e(TAG, "null entry");
                return 0;
            }

            // Be sure we aren't shut down
            if (isCancelled()) {
                Log.d(TAG, "node counting cancelled!");
                return 0;
            }

            File[] entries = f.listFiles();
            if (entries == null) {
                return currentCount;
            }
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    currentCount = countNodes(entry, currentCount);
                } else {
                    currentCount++;
                }
            }
            return currentCount;
        }

        /**
         * Recursively scan a file directory, examining each file
         * inside and consuming its information.
         * @param f  Directory to scan
         * @return The total number of files examined so far.
         */
        private long scanDirectory(File f, long scanCount) {

            // Be safe
            if (f == null) {
                Log.e(TAG, "null entry");
                return scanCount;
            }

            // Are we shut down?
            if (isCancelled()) {
                return scanCount;
            }

            // Slow the scan so that we can demonstrate behavior
            // while it is in progress. Since we are not on the main
            // thread we can do this.
            SystemClock.sleep(125);

            Log.i(TAG, "scanning directory:  " + f.getName() + " , " + scanCount);

            File[] entries = f.listFiles();
            if (entries == null) {
                return scanCount;
            }
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    scanCount = scanDirectory(entry, scanCount);
                    //Log.d(TAG, "subdirectory " + entry.getName() + " gives us a total: " + currentCount);
                } else {
                    scanCount++;
                    consumeEntry(entry, scanCount);
                    //Log.d(TAG, "simple file " + entry.getName() + "increments count:  " + currentCount);
                }
            }
            // Update progress.
            publishProgress((int)((100.0 * scanCount) / mScannerFilesFound));

            return scanCount;
        }

        /**
         * Examine a given file node and record its information.
         * @param f The file to examine
         * @param count A running count of the files "consumed."
         */
        private void consumeEntry(File f, long count) {
            long fileSize = f.length();

            // I believe this is correct for producing a running average.
            averageSize = ((averageSize * (count - 1)) + fileSize ) / count;

            // Keep track of the extension count
            int delimiterPosition = f.getName().lastIndexOf(SUFFIX_DELIMETER);
            String suffix;
            if (delimiterPosition > 0) {
                suffix = f.getName().substring(delimiterPosition + 1);
            } else {
                // No extension, or a dot file
                suffix = "<None>";
            }
            Integer currentCount = commonExtensions.get(suffix);
            if (currentCount == null) {
                // Not there, add it
                commonExtensions.put(suffix, 1);
            } else {
                // Increment current value
                commonExtensions.put(suffix, ++currentCount);
            }

            // Keep track of biggest files. In theory we are hardwired to the biggest
            // 10, but when checking a full array use the length, just in case.
            if (largestFiles.size() < MAX_FILESIZE_COUNT ||
                    fileSize > largestFiles.get(MAX_FILESIZE_COUNT - 1).fileSize) {

                FileSizeInfo newFSI = new FileSizeInfo(f.getName(), fileSize);

                if (largestFiles.size() < MAX_FILESIZE_COUNT) {
                    largestFiles.add(newFSI);
                } else {
                    // Insert this file in the list of largest files
                    for (int i = 0; i < largestFiles.size(); i++) {
                        FileSizeInfo fsi = largestFiles.get(i);
                        if (fileSize > fsi.fileSize) {
                            largestFiles.remove(MAX_FILESIZE_COUNT - 1);
                            largestFiles.add(i, newFSI);
                            break;
                        }
                    }
                }


                // The list has changed. Sort it. We could override the
                // appropriate comparator methods in the FileSizeInfo class,
                // but that seems overdoing it in this case.
                Collections.sort(largestFiles, new Comparator<FileSizeInfo>() {
                    @Override
                    public int compare(FileSizeInfo fsi1, FileSizeInfo fsi2) {
                        return (int)(fsi2.fileSize - fsi1.fileSize);
                    }
                });
            }
        }
    }
}
