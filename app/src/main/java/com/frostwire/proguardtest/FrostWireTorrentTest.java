package com.frostwire.proguardtest;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert;
import com.frostwire.jlibtorrent.alerts.PieceFinishedAlert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static com.frostwire.jlibtorrent.alerts.AlertType.ADD_TORRENT;
import static com.frostwire.jlibtorrent.alerts.AlertType.BLOCK_FINISHED;
import static com.frostwire.jlibtorrent.alerts.AlertType.PIECE_FINISHED;
import static com.frostwire.jlibtorrent.alerts.AlertType.TORRENT_REMOVED;

public class FrostWireTorrentTest {
    private Runner runner;

    public void start(File dir) {
        if (runner == null) {
            runner = new Runner(dir);
            runner.start();
        }
    }

    private static class Runner extends Thread {

        private static final String TAG = "Runner";


        private boolean isTorrentComplete = false;
        private File directory;
        private SessionManager sessionManager;
        private TorrentHandle torrentHandle;

        Runner(File dir) {
            directory = dir;
        }

        private SettingsPack getSessionParams() {
            return new SettingsPack()
                    .anonymousMode(Boolean.TRUE)
                    .connectionsLimit(8)
                    .downloadRateLimit(0)
                    .uploadRateLimit(0)
                    .activeDhtLimit(88);
        }

        private byte[] getTorrentFromWeb(String torrentUrl) throws TorrentInfoNotFound {
            try {
                Log.d(TAG, "loading torrent info from '" + torrentUrl + "'.");

                URL url = new URL(torrentUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                InputStream inputStream = connection.getInputStream();

                byte[] responseByteArray = new byte[0];

                if (connection.getResponseCode() == 200) {
                    responseByteArray = readBytesFromInputStream(inputStream);
                }

                inputStream.close();
                connection.disconnect();

                Log.d(TAG, "torrent info loaded.");

                if (responseByteArray.length > 0) {
                    return responseByteArray;
                }
            }
            catch (IOException | IllegalArgumentException e) {
                throw new TorrentInfoNotFound(e);
            }

            throw new TorrentInfoNotFound();
        }

        private byte[] readBytesFromInputStream(InputStream in) throws IOException {
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            int len;
            while ((len = in.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }

            return byteBuffer.toByteArray();
        }

        @Override
        public void run() {
            sessionManager = new SessionManager();

            if (!sessionManager.isRunning()) {
                sessionManager.start(new SessionParams(getSessionParams()));
            }
            else {
                Log.e(TAG, "Attempt to re-initialize an already running Session.");
            }

            final CountDownLatch torrentLatch = new CountDownLatch(1);

            sessionManager.addListener(new AlertListener() {
                @Override
                public int[] types() {
                    return new int[] {
                            ADD_TORRENT.swig(),
                            TORRENT_REMOVED.swig(),
                            BLOCK_FINISHED.swig(),
                            PIECE_FINISHED.swig(),
                    };
                }

                @Override
                public void alert(Alert<?> alert) {

                    switch (alert.type()) {
                        case ADD_TORRENT: {
                            torrentHandle = sessionManager.find(((AddTorrentAlert) alert).handle().infoHash());
                            torrentLatch.countDown();
                            break;
                        }

                        case TORRENT_REMOVED: {
                            Log.d(TAG, "Torrent Removed.");
                            isTorrentComplete = true;
                            synchronized (Runner.this) {
                                Runner.this.notify();
                            }
                            break;
                        }

                        case PIECE_FINISHED: {
                            Log.d(TAG, "Piece finished: " + ((PieceFinishedAlert) alert).pieceIndex());
                            break;
                        }

                        case BLOCK_FINISHED: {
                            Log.d(TAG, "Block finished: " + ((BlockFinishedAlert) alert).pieceIndex() + ", " + ((BlockFinishedAlert) alert).blockIndex());
                            break;
                        }
                    }
                }
            });
            sessionManager.startDht();

            TorrentInfo torrentInfo = null;

            try {
                torrentInfo = TorrentInfo.bdecode(getTorrentFromWeb("https://yts.mx/torrent/download/972FCD0BF171F0FA2611E82E46EA455A154B47D2"));
            }
            catch (TorrentInfoNotFound ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }

            Log.d(TAG, "torrent info=" + torrentInfo);

            Priority[] filesPriority = new Priority[Objects.requireNonNull(torrentInfo).files().numFiles()];

            Arrays.fill(filesPriority, Priority.SEVEN);

            sessionManager.download(
                    torrentInfo,
                    directory,
                    null,
                    filesPriority,
                    null
            );

            try {
                Log.d(TAG, "Waiting for TorrentAdd");
                torrentLatch.await();
            }
            catch (InterruptedException e) {
                Log.d(TAG, "Thread Interrupted...", e);
            }

            Log.d(TAG, "Starting download.");
            torrentHandle.resume();

            try {
                synchronized (Runner.this) {
                    Log.d(TAG, "Waiting for torrent to finish...");
                    wait();
                }

                Log.d(TAG, "run: Torrent Download Complete.");
            }
            catch (InterruptedException ex) {
                Log.e(TAG, "interrupted: ", ex);
            }
        }
    }
}
