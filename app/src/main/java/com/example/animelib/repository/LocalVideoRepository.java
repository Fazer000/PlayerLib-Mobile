package com.example.animelib.repository;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.animelib.data.DatabaseManager;
import com.example.animelib.data.entity.VideoPlaybackHistoryEntity;
import com.example.animelib.models.VideoItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Реализация репозитория видеофайлов на основе Android MediaStore и локальной файловой системы.
 */
public class LocalVideoRepository implements VideoRepository {

    private static final String TAG = "LocalVideoRepository";

    private final Context context;
    private final DatabaseManager databaseManager;

    public LocalVideoRepository(@NonNull Context context, @NonNull DatabaseManager databaseManager) {
        this.context = context.getApplicationContext();
        this.databaseManager = databaseManager;
    }

    @Override
    public List<VideoItem> scanVideos() {
        List<VideoItem> list = queryMediaStore();

        // Фолбэк на стандартные публичные каталоги, если MediaStore пуст (например, эмулятор без индексации)
        if (list.isEmpty()) {
            scanDirectoryFallback(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), list);
            scanDirectoryFallback(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), list);
            scanDirectoryFallback(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), list);
        }

        // Добавляем тестовые видео, если включен Тестовый режим в настройках или если локальных файлов нет
        if (databaseManager.loadTestModeSetting() || list.isEmpty()) {
            List<VideoItem> testVideos = getTestVideos();
            for (VideoItem testVideo : testVideos) {
                boolean exists = false;
                for (VideoItem existing : list) {
                    if (existing.getPath() != null && existing.getPath().equalsIgnoreCase(testVideo.getPath())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    list.add(testVideo);
                }
            }
        }

        // Синхронизация сохраненного прогресса и длительности из Room Database или MediaMetadataRetriever
        for (VideoItem item : list) {
            if (item.getPath() != null) {
                VideoPlaybackHistoryEntity history = databaseManager.getVideoHistory(item.getPath());
                if (history != null) {
                    item.setLastPositionMs(history.getLastPositionMs());
                    if (item.getDurationMs() <= 0 && history.getDurationMs() > 0) {
                        item.setDurationMs(history.getDurationMs());
                    }
                }
                if (item.getDurationMs() <= 0) {
                    long extractedDuration = extractDurationFromFile(item.getPath(), item.getUri());
                    if (extractedDuration > 0) {
                        item.setDurationMs(extractedDuration);
                    }
                }
            }
        }

        return list;
    }

    @Override
    public List<VideoItem> getTestVideos() {
        List<VideoItem> testList = new ArrayList<>();
        testList.add(new VideoItem(
                10001,
                "Big Buck Bunny (Демо)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                596000,
                158000000,
                System.currentTimeMillis() / 1000,
                "Демо Видео",
                1920, 1080, 0
        ));
        testList.add(new VideoItem(
                10002,
                "Tears of Steel (Демо)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
                734000,
                175000000,
                System.currentTimeMillis() / 1000,
                "Демо Видео",
                1920, 1080, 0
        ));
        testList.add(new VideoItem(
                10003,
                "Sintel (Демо)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
                522000,
                120000000,
                System.currentTimeMillis() / 1000,
                "Демо Видео",
                1920, 1080, 0
        ));
        testList.add(new VideoItem(
                10004,
                "Shorts: Vertical Anime Edit (Демо)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
                15000,
                15000000,
                System.currentTimeMillis() / 1000,
                "Shorts",
                1080, 1920, 0
        ));
        testList.add(new VideoItem(
                10005,
                "Shorts: Vertical Cosplay Reel (Демо)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
                18000,
                18000000,
                System.currentTimeMillis() / 1000,
                "Shorts",
                1080, 1920, 0
        ));
        return testList;
    }

    @Override
    public boolean refreshWatchProgress(List<VideoItem> items) {
        if (items == null || items.isEmpty()) return false;
        boolean changed = false;

        for (VideoItem item : items) {
            if (item.getPath() != null) {
                VideoPlaybackHistoryEntity history = databaseManager.getVideoHistory(item.getPath());
                if (history != null && history.getLastPositionMs() != item.getLastPositionMs()) {
                    item.setLastPositionMs(history.getLastPositionMs());
                    changed = true;
                }
            }
        }

        return changed;
    }

    private List<VideoItem> queryMediaStore() {
        List<VideoItem> list = new ArrayList<>();
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? MediaStore.Video.Media.BUCKET_DISPLAY_NAME : MediaStore.Video.Media.DATA
        };

        String sortOrder = MediaStore.Video.Media.DATE_MODIFIED + " DESC";

        try (Cursor cursor = context.getContentResolver().query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                int dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
                int widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH);
                int heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT);
                int bucketColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                        : dataColumn;

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    String path = cursor.getString(dataColumn);
                    long duration = cursor.getLong(durationColumn);
                    long size = cursor.getLong(sizeColumn);
                    long dateModified = cursor.getLong(dateModifiedColumn);
                    int width = widthColumn != -1 ? cursor.getInt(widthColumn) : 0;
                    int height = heightColumn != -1 ? cursor.getInt(heightColumn) : 0;

                    String folderName;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        folderName = cursor.getString(bucketColumn);
                    } else {
                        folderName = extractFolderNameFromPath(path);
                    }

                    if (folderName == null || folderName.isEmpty()) {
                        folderName = extractFolderNameFromPath(path);
                    }

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

                    if (name == null && path != null) {
                        name = new File(path).getName();
                    }

                    list.add(new VideoItem(id, name, path, contentUri, duration, size, dateModified, folderName, width, height, 0));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying MediaStore", e);
        }

        return list;
    }

    private void scanDirectoryFallback(File dir, List<VideoItem> list) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectoryFallback(f, list);
            } else if (isVideoFile(f.getName())) {
                String folderName = dir.getName();
                Uri uri = Uri.fromFile(f);
                list.add(new VideoItem(f.hashCode(), f.getName(), f.getAbsolutePath(), uri,
                        0, f.length(), f.lastModified() / 1000, folderName, 0, 0, 0));
            }
        }
    }

    private boolean isVideoFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".flv")
                || lower.endsWith(".ts") || lower.endsWith(".m4v") || lower.endsWith(".3gp");
    }

    private String extractFolderNameFromPath(String path) {
        if (path == null) return "Видео";
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null) {
                return parent.getName();
            }
        } catch (Exception ignored) {}
        return "Видео";
    }

    private long extractDurationFromFile(String path, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (path != null && new File(path).exists()) {
                retriever.setDataSource(path);
            } else if (uri != null) {
                retriever.setDataSource(context, uri);
            }
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) {
                return Long.parseLong(durStr);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
