package com.novoda.downloadmanager.demo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.novoda.downloadmanager.AllBatchStatusesCallback;
import com.novoda.downloadmanager.Batch;
import com.novoda.downloadmanager.ConnectionType;
import com.novoda.downloadmanager.DownloadBatchId;
import com.novoda.downloadmanager.DownloadBatchIdCreator;
import com.novoda.downloadmanager.DownloadBatchStatus;
import com.novoda.downloadmanager.DownloadBatchStatusCallback;
import com.novoda.downloadmanager.DownloadFileId;
import com.novoda.downloadmanager.DownloadFileIdCreator;
import com.novoda.downloadmanager.LiteDownloadManagerCommands;
import com.novoda.downloadmanager.StorageRoot;
import com.novoda.downloadmanager.StorageRootFactory;
import com.novoda.downloadmanager.demo.migration.MigrationJob;

import java.io.File;
import java.util.concurrent.Executors;

import static com.novoda.downloadmanager.DownloadBatchStatus.Status.ERROR;

// Need to extract collaborators for this demo to reduce complexity. GH Issue #286
@SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.StdCyclomaticComplexity", "PMD.ModifiedCyclomaticComplexity"})
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final DownloadBatchId BATCH_ID_1 = DownloadBatchIdCreator.createSanitizedFrom("batch_id_1");
    private static final DownloadBatchId BATCH_ID_2 = DownloadBatchIdCreator.createSanitizedFrom("batch_id_2");
    private static final DownloadFileId FILE_ID_1 = DownloadFileIdCreator.createFrom("file_id_1");
    private static final String FIVE_MB_FILE_URL = "http://ipv4.download.thinkbroadband.com/5MB.zip";
    private static final String TEN_MB_FILE_URL = "http://ipv4.download.thinkbroadband.com/10MB.zip";
    private static final String TWENTY_MB_FILE_URL = "http://ipv4.download.thinkbroadband.com/20MB.zip";

    private TextView databaseCloningUpdates;
    private TextView versionOneMigrationStatus;
    private TextView textViewBatch1;
    private TextView textViewBatch2;
    private LiteDownloadManagerCommands liteDownloadManagerCommands;
    private VersionOneDatabaseCloner versionOneDatabaseCloner;
    private Spinner downloadFileSizeSpinner;
    private MigrationJob migrationJob;
    private StorageRoot primaryStorageWithDownloadsSubpackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewBatch1 = findViewById(R.id.batch_1);
        textViewBatch2 = findViewById(R.id.batch_2);

        versionOneDatabaseCloner = DatabaseClonerFactory.databaseCloner(this, cloneCallback);

        downloadFileSizeSpinner = findViewById(R.id.database_download_file_size);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.file_sizes, android.R.layout.simple_spinner_item);
        downloadFileSizeSpinner.setAdapter(adapter);

        primaryStorageWithDownloadsSubpackage = StorageRootFactory.createPrimaryStorageDownloadsDirectoryRoot(getApplicationContext());

        databaseCloningUpdates = findViewById(R.id.database_cloning_updates);
        View buttonCreateDB = findViewById(R.id.button_create_v1_db);
        buttonCreateDB.setOnClickListener(createDatabaseOnClick);

        versionOneMigrationStatus = findViewById(R.id.database_migration_updates);
        View buttonMigrate = findViewById(R.id.button_migrate);
        buttonMigrate.setOnClickListener(startMigrationOnClick);

        CheckBox checkWifiOnly = findViewById(R.id.check_wifi_only);
        checkWifiOnly.setOnCheckedChangeListener(wifiOnlyOnCheckedChange);

        View buttonDownload = findViewById(R.id.button_start_downloading);
        buttonDownload.setOnClickListener(downloadBatchesOnClick);

        View buttonDeleteAll = findViewById(R.id.button_delete_all);
        buttonDeleteAll.setOnClickListener(deleteAllOnClick);

        View buttonLogFileDirectory = findViewById(R.id.button_log_file_directory);
        buttonLogFileDirectory.setOnClickListener(logFileDirectoryOnClick);

        View buttonLogDownloadFileStatus = findViewById(R.id.button_log_download_file_status);
        buttonLogDownloadFileStatus.setOnClickListener(logDownloadFileStatusOnClick);

        DemoApplication demoApplication = (DemoApplication) getApplicationContext();
        liteDownloadManagerCommands = demoApplication.getLiteDownloadManagerCommands();
        liteDownloadManagerCommands.addDownloadBatchCallback(callback);
        liteDownloadManagerCommands.getAllDownloadBatchStatuses(batchStatusesCallback);

        Handler migrationCallbackHandler = new Handler(Looper.getMainLooper());
        migrationJob = new MigrationJob(
                getDatabasePath("downloads.db"),
                primaryStorageWithDownloadsSubpackage,
                liteDownloadManagerCommands,
                migrationCallbackHandler,
                migrationJobCallback
        );

        bindBatchViews();
    }

    private final VersionOneDatabaseCloner.CloneCallback cloneCallback = updateMessage -> databaseCloningUpdates.setText(updateMessage);

    private final View.OnClickListener createDatabaseOnClick = v -> {
        String selectedFileSize = (String) downloadFileSizeSpinner.getSelectedItem();
        versionOneDatabaseCloner.cloneDatabaseWithDownloadSize(selectedFileSize);
    };

    private final View.OnClickListener startMigrationOnClick = v -> Executors.newSingleThreadExecutor().submit(migrationJob);

    private final CompoundButton.OnCheckedChangeListener wifiOnlyOnCheckedChange = (buttonView, isChecked) -> {
        LiteDownloadManagerCommands downloadManagerCommands = ((DemoApplication) getApplication()).getLiteDownloadManagerCommands();
        if (isChecked) {
            downloadManagerCommands.updateAllowedConnectionType(ConnectionType.UNMETERED);
        } else {
            downloadManagerCommands.updateAllowedConnectionType(ConnectionType.ALL);
        }
    };

    private final View.OnClickListener downloadBatchesOnClick = v -> {
        Batch batch = Batch.with(primaryStorageWithDownloadsSubpackage, BATCH_ID_1, "Made in chelsea")
                .downloadFrom(FIVE_MB_FILE_URL).saveTo("foo/bar", "5mb.zip").withIdentifier(FILE_ID_1).apply()
                .downloadFrom(TEN_MB_FILE_URL).apply()
                .build();
        liteDownloadManagerCommands.download(batch);

        batch = Batch.with(primaryStorageWithDownloadsSubpackage, BATCH_ID_2, "Hollyoaks")
                .downloadFrom(TEN_MB_FILE_URL).apply()
                .downloadFrom(TWENTY_MB_FILE_URL).apply()
                .build();
        liteDownloadManagerCommands.download(batch);
    };

    private final View.OnClickListener deleteAllOnClick = v -> {
        liteDownloadManagerCommands.delete(BATCH_ID_1);
        liteDownloadManagerCommands.delete(BATCH_ID_2);
    };

    private final View.OnClickListener logFileDirectoryOnClick = v -> {
        File downloadsDir = new File(primaryStorageWithDownloadsSubpackage.path());
        Log.d(TAG, "LogFileDirectory. Downloads dir: " + downloadsDir.getAbsolutePath());
        if (downloadsDir.exists()) {
            logAllFiles(downloadsDir.listFiles());
        }
    };

    private void logAllFiles(File... files) {
        for (File file : files) {
            if (file.isDirectory()) {
                logAllFiles(file.listFiles());
            } else {
                Log.d(TAG, "LogFileDirectory. " + file.getAbsolutePath());
            }
        }
    }

    private final View.OnClickListener logDownloadFileStatusOnClick = v -> liteDownloadManagerCommands.getDownloadFileStatusWithMatching(
            BATCH_ID_1, FILE_ID_1,
            downloadFileStatus -> Log.d(TAG, "FileStatus: " + downloadFileStatus)
    );

    private final DownloadBatchStatusCallback callback = downloadBatchStatus -> {
        String status = getStatusMessage(downloadBatchStatus);

        String message = "Batch " + downloadBatchStatus.getDownloadBatchTitle().asString()
                + "\ndownloaded: " + downloadBatchStatus.percentageDownloaded() + "%"
                + "\nbytes: " + downloadBatchStatus.bytesDownloaded()
                + "\ntotal: " + downloadBatchStatus.bytesTotalSize()
                + status
                + "\n";

        DownloadBatchId downloadBatchId = downloadBatchStatus.getDownloadBatchId();
        if (downloadBatchId.equals(BATCH_ID_1)) {
            textViewBatch1.setText(message);
        } else if (downloadBatchId.equals(BATCH_ID_2)) {
            textViewBatch2.setText(message);
        }
    };

    private String getStatusMessage(DownloadBatchStatus downloadBatchStatus) {
        if (downloadBatchStatus.status() == ERROR) {
            return "\nstatus: " + downloadBatchStatus.status()
                    + " - " + downloadBatchStatus.downloadError().type();
        } else {
            return "\nstatus: " + downloadBatchStatus.status();
        }
    }

    private final AllBatchStatusesCallback batchStatusesCallback = downloadBatchStatuses -> {
        for (DownloadBatchStatus downloadBatchStatus : downloadBatchStatuses) {
            callback.onUpdate(downloadBatchStatus);
        }
    };

    private final MigrationJob.MigrationJobCallback migrationJobCallback = message -> versionOneMigrationStatus.setText(message);

    private void bindBatchViews() {
        View buttonPauseDownload1 = findViewById(R.id.button_pause_downloading_1);
        buttonPauseDownload1.setOnClickListener(v -> liteDownloadManagerCommands.pause(BATCH_ID_1));

        View buttonPauseDownload2 = findViewById(R.id.button_pause_downloading_2);
        buttonPauseDownload2.setOnClickListener(v -> liteDownloadManagerCommands.pause(BATCH_ID_2));

        View buttonResumeDownload1 = findViewById(R.id.button_resume_downloading_1);
        buttonResumeDownload1.setOnClickListener(v -> liteDownloadManagerCommands.resume(BATCH_ID_1));

        View buttonResumeDownload2 = findViewById(R.id.button_resume_downloading_2);
        buttonResumeDownload2.setOnClickListener(v -> liteDownloadManagerCommands.resume(BATCH_ID_2));
    }

}
