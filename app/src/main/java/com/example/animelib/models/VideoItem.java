package com.example.animelib.models;

import android.net.Uri;
import java.io.Serializable;
import java.util.Locale;

public class VideoItem implements Serializable {
    private final long id;
    private String title;
    private final String path;
    private final String uriString;
    private long durationMs;
    private final long sizeBytes;
    private final long dateModified;
    private final String folderName;
    private long lastPositionMs;
    private int progressPercent;
    private int width;
    private int height;
    private int rotation;

    public VideoItem(long id, String title, String path, Uri uri, long durationMs,
                     long sizeBytes, long dateModified, String folderName) {
        this(id, title, path, uri, durationMs, sizeBytes, dateModified, folderName, 0, 0, 0);
    }

    public VideoItem(long id, String title, String path, Uri uri, long durationMs,
                     long sizeBytes, long dateModified, String folderName,
                     int width, int height, int rotation) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.uriString = uri != null ? uri.toString() : "";
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.dateModified = dateModified;
        this.folderName = folderName != null && !folderName.isEmpty() ? folderName : "Видео";
        this.lastPositionMs = 0;
        this.progressPercent = 0;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDisplayTitle() {
        if (title == null) return "";
        int dot = title.lastIndexOf('.');
        if (dot > 0) {
            return title.substring(0, dot);
        }
        return title;
    }

    public String getFileExtension() {
        String source = title;
        if (source != null && source.contains(".")) {
            int dot = source.lastIndexOf('.');
            if (dot > 0 && dot < source.length() - 1) {
                return source.substring(dot + 1).toUpperCase(Locale.ROOT);
            }
        }
        if (path != null && path.contains(".")) {
            int dot = path.lastIndexOf('.');
            if (dot > 0 && dot < path.length() - 1) {
                return path.substring(dot + 1).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    public boolean isNew() {
        if (dateModified <= 0) return false;
        long timeMs = dateModified * 1000L;
        long now = System.currentTimeMillis();
        long diff = now - timeMs;
        return diff >= -86400000L && diff <= 7L * 24 * 60 * 60 * 1000L;
    }

    public String getPath() { return path; }
    public Uri getUri() { return uriString != null && !uriString.isEmpty() ? Uri.parse(uriString) : null; }
    public String getUriString() { return uriString; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public long getSizeBytes() { return sizeBytes; }
    public long getDateModified() { return dateModified; }
    public String getFolderName() { return folderName; }
    public long getLastPositionMs() { return lastPositionMs; }
    public void setLastPositionMs(long lastPositionMs) {
        this.lastPositionMs = lastPositionMs;
        if (durationMs > 0) {
            this.progressPercent = (int) Math.min(100, Math.max(0, (lastPositionMs * 100) / durationMs));
        }
    }
    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public int getRotation() { return rotation; }
    public void setRotation(int rotation) { this.rotation = rotation; }

    public boolean isShortVideo() {
        if (width > 0 && height > 0) {
            int effectiveW = (rotation == 90 || rotation == 270) ? height : width;
            int effectiveH = (rotation == 90 || rotation == 270) ? width : height;
            return effectiveH > effectiveW;
        }

        if (title != null) {
            String lower = title.toLowerCase(Locale.ROOT);
            if (lower.contains("short") || lower.contains("reel") || lower.contains("tiktok") || lower.contains("vertical")) {
                return true;
            }
        }
        if (path != null) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.contains("short") || lower.contains("reel") || lower.contains("tiktok") || lower.contains("vertical")) {
                return true;
            }
        }

        return durationMs > 0 && durationMs <= 60000;
    }

    public String getFormattedDuration() {
        if (durationMs <= 0) return "--:--";
        long totalSecs = durationMs / 1000;
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, mins, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", mins, secs);
        }
    }

    public String getFormattedSize() {
        if (sizeBytes <= 0) return "";
        double kb = sizeBytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;
        if (gb >= 1.0) {
            return String.format(Locale.getDefault(), "%.1f ГБ", gb);
        } else if (mb >= 1.0) {
            return String.format(Locale.getDefault(), "%.0f МБ", mb);
        } else {
            return String.format(Locale.getDefault(), "%.0f КБ", kb);
        }
    }

    public String getFormattedDate() {
        if (dateModified <= 0) return "";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            return sdf.format(new java.util.Date(dateModified * 1000L));
        } catch (Exception e) {
            return "";
        }
    }
}
