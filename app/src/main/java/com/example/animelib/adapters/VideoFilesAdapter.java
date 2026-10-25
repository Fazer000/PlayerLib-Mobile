package com.example.animelib.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.animelib.R;
import com.example.animelib.models.VideoItem;
import com.example.animelib.ui.M3LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoFilesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_VIDEO_LIST = 1;
    public static final int VIEW_TYPE_VIDEO_GRID = 2;
    public static final int VIEW_TYPE_VIDEO_SHORT_GRID = 3;
    public static final int VIEW_TYPE_VIDEO = 1;

    public static class DateHeader {
        public final String title;
        public final int count;

        public DateHeader(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }

    public interface OnVideoClickListener {
        void onVideoClick(VideoItem video, int position);
        void onVideoMoreClick(VideoItem video, int position);
    }

    private final List<VideoItem> videos = new ArrayList<>();
    private final List<Object> items = new ArrayList<>();
    private final OnVideoClickListener listener;

    private boolean isSelectionMode = false;
    private boolean isGridView = false;
    private boolean isShortsTab = false;
    private final List<VideoItem> selectedVideos = new ArrayList<>();

    public void setGridView(boolean gridView) {
        if (this.isGridView != gridView) {
            this.isGridView = gridView;
            notifyDataSetChanged();
        }
    }

    public boolean isGridView() {
        return isGridView;
    }

    public void setShortsTab(boolean shortsTab) {
        if (this.isShortsTab != shortsTab) {
            this.isShortsTab = shortsTab;
            notifyDataSetChanged();
        }
    }

    public boolean isShortsTab() {
        return isShortsTab;
    }

    private static final int CACHE_SIZE = 40 * 1024 * 1024; // 40MB memory cache
    private static final LruCache<String, Bitmap> thumbnailCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private static final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public VideoFilesAdapter(List<VideoItem> initialVideos, OnVideoClickListener listener) {
        this.listener = listener;
        if (initialVideos != null) {
            this.videos.addAll(initialVideos);
        }
        rebuildItems();
    }

    public void updateVideos(List<VideoItem> newVideos) {
        this.videos.clear();
        if (newVideos != null) {
            this.videos.addAll(newVideos);
        }
        rebuildItems();
        notifyDataSetChanged();
    }

    public void rebuildItems() {
        items.clear();
        if (videos.isEmpty()) return;

        Map<String, List<VideoItem>> groups = new LinkedHashMap<>();
        for (VideoItem video : videos) {
            String header = formatGroupDate(video.getDateModified());
            List<VideoItem> group = groups.get(header);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(header, group);
            }
            group.add(video);
        }

        for (Map.Entry<String, List<VideoItem>> entry : groups.entrySet()) {
            items.add(new DateHeader(entry.getKey(), entry.getValue().size()));
            items.addAll(entry.getValue());
        }
    }

    public static String formatGroupDate(long dateModifiedSeconds) {
        if (dateModifiedSeconds <= 0) return "Другие";
        long timeMs = dateModifiedSeconds * 1000L;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
        String formattedDate = sdf.format(new Date(timeMs));

        if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            return "Сегодня • " + formattedDate;
        } else if (cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)) {
            return "Вчера • " + formattedDate;
        } else {
            return formattedDate;
        }
    }

    public void setSelectionMode(boolean selectionMode) {
        this.isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedVideos.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public List<VideoItem> getSelectedVideos() {
        return selectedVideos;
    }

    public void toggleSelection(VideoItem video) {
        int index = -1;
        for (int i = 0; i < selectedVideos.size(); i++) {
            VideoItem item = selectedVideos.get(i);
            if (item.getId() == video.getId() || (item.getPath() != null && item.getPath().equals(video.getPath()))) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            selectedVideos.remove(index);
        } else {
            selectedVideos.add(video);
        }
        notifyDataSetChanged();
    }


    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof DateHeader) {
            return VIEW_TYPE_HEADER;
        }
        if (isShortsTab) {
            return VIEW_TYPE_VIDEO_SHORT_GRID;
        }
        if (items.get(position) instanceof VideoItem) {
            VideoItem video = (VideoItem) items.get(position);
            if (video != null && video.isShortVideo() && isGridView) {
                return VIEW_TYPE_VIDEO_SHORT_GRID;
            }
        }
        return isGridView ? VIEW_TYPE_VIDEO_GRID : VIEW_TYPE_VIDEO_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_date_header, parent, false);
            return new DateHeaderViewHolder(view);
        } else if (viewType == VIEW_TYPE_VIDEO_SHORT_GRID) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_short_grid, parent, false);
            return new VideoViewHolder(view);
        } else if (viewType == VIEW_TYPE_VIDEO_GRID) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_grid, parent, false);
            return new VideoViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_card, parent, false);
            return new VideoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        Object itemObj = items.get(position);

        if (rawHolder instanceof DateHeaderViewHolder && itemObj instanceof DateHeader) {
            DateHeader header = (DateHeader) itemObj;
            DateHeaderViewHolder holder = (DateHeaderViewHolder) rawHolder;
            holder.tvDateHeaderTitle.setText(header.title);
            holder.tvDateHeaderCount.setText(header.count + " видео");
            return;
        }

        if (!(rawHolder instanceof VideoViewHolder) || !(itemObj instanceof VideoItem)) {
            return;
        }

        VideoViewHolder holder = (VideoViewHolder) rawHolder;
        VideoItem video = (VideoItem) itemObj;

        boolean isGridFormat = isGridView || isShortsTab || rawHolder.getItemViewType() == VIEW_TYPE_VIDEO_SHORT_GRID;

        if (holder.tvVideoTitle != null) {
            if (isGridFormat) {
                holder.tvVideoTitle.setVisibility(View.GONE);
            } else {
                holder.tvVideoTitle.setText(video.getDisplayTitle());
                holder.tvVideoTitle.setVisibility(View.VISIBLE);
            }
        }

        // "НОВОЕ" Tag
        if (holder.tvVideoNewTag != null) {
            holder.tvVideoNewTag.setVisibility(video.isNew() ? View.VISIBLE : View.GONE);
        }

        // Extension Tag
        String extension = video.getFileExtension();
        if (holder.tvVideoExtensionTag != null) {
            if (isGridFormat) {
                holder.tvVideoExtensionTag.setVisibility(View.GONE);
            } else {
                if (!extension.isEmpty() && extension.length() <= 5) {
                    holder.tvVideoExtensionTag.setText(extension);
                    holder.tvVideoExtensionTag.setVisibility(View.VISIBLE);
                } else {
                    holder.tvVideoExtensionTag.setVisibility(View.GONE);
                }
            }
        }

        if (holder.tvVideoDuration != null) {
            holder.tvVideoDuration.setText(video.getFormattedDuration());
            holder.tvVideoDuration.setVisibility(View.VISIBLE);
        }

        if (holder.tvVideoFolder != null) {
            if (isGridFormat) {
                holder.tvVideoFolder.setVisibility(View.GONE);
            } else {
                holder.tvVideoFolder.setText(video.getFolderName());
                holder.tvVideoFolder.setVisibility(View.VISIBLE);
            }
        }

        if (holder.tvVideoSize != null) {
            if (isGridFormat) {
                holder.tvVideoSize.setVisibility(View.GONE);
            } else {
                String sizeStr = video.getFormattedSize();
                String dateStr = video.getFormattedDate();
                if (!sizeStr.isEmpty() && !dateStr.isEmpty()) {
                    holder.tvVideoSize.setText(sizeStr + " • " + dateStr);
                } else if (!sizeStr.isEmpty()) {
                    holder.tvVideoSize.setText(sizeStr);
                } else {
                    holder.tvVideoSize.setText(dateStr);
                }
                holder.tvVideoSize.setVisibility(View.VISIBLE);
            }
        }

        if (video.getProgressPercent() > 0) {
            if (holder.pbWatchProgress != null) {
                holder.pbWatchProgress.setVisibility(View.VISIBLE);
                holder.pbWatchProgress.setProgress(video.getProgressPercent(), false);
            }
            if (holder.tvWatchProgressStatus != null) {
                holder.tvWatchProgressStatus.setVisibility(View.VISIBLE);
                holder.tvWatchProgressStatus.setText(video.getProgressPercent() + "%");
            }
        } else {
            if (holder.pbWatchProgress != null) {
                holder.pbWatchProgress.setVisibility(View.GONE);
            }
            if (holder.tvWatchProgressStatus != null) {
                holder.tvWatchProgressStatus.setVisibility(View.GONE);
            }
        }

        int selectionIndex = -1;
        if (isSelectionMode) {
            for (int i = 0; i < selectedVideos.size(); i++) {
                VideoItem sel = selectedVideos.get(i);
                if (sel.getId() == video.getId() || (sel.getPath() != null && sel.getPath().equals(video.getPath()))) {
                    selectionIndex = i;
                    break;
                }
            }
        }

        if (holder.tvSelectionBadge != null) {
            if (selectionIndex >= 0) {
                holder.tvSelectionBadge.setVisibility(View.VISIBLE);
                holder.tvSelectionBadge.setText(String.valueOf(selectionIndex + 1));
                holder.itemView.setAlpha(1.0f);
            } else if (isSelectionMode) {
                holder.tvSelectionBadge.setVisibility(View.VISIBLE);
                holder.tvSelectionBadge.setText("");
                holder.itemView.setAlpha(0.6f);
            } else {
                holder.tvSelectionBadge.setVisibility(View.GONE);
                holder.itemView.setAlpha(1.0f);
            }
        } else {
            holder.itemView.setAlpha(1.0f);
        }

        // 3-dots More Button
        if (holder.btnVideoMore != null) {
            holder.btnVideoMore.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);
            holder.btnVideoMore.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = videos.indexOf(video);
                    listener.onVideoMoreClick(video, pos != -1 ? pos : 0);
                }
            });
        }

        // Thumbnail loading
        String cacheKey = com.example.animelib.util.VideoThumbnailHelper.buildCacheKey(video.getPath(), video.getUri());
        holder.ivVideoThumbnail.setTag(cacheKey);

        Bitmap cached = null;
        if (cacheKey != null) {
            cached = thumbnailCache.get(cacheKey);
            if (cached == null) {
                cached = com.example.animelib.util.VideoThumbnailHelper.getCachedThumbnail(cacheKey);
            }
        }
        if (cached != null) {
            holder.ivVideoThumbnail.setImageBitmap(cached);
        } else {
            holder.ivVideoThumbnail.setImageResource(R.drawable.placeholder_image);
            if (cacheKey != null) {
                loadThumbnailAsync(holder.itemView.getContext(), video, cacheKey, holder.ivVideoThumbnail);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = videos.indexOf(video);
                listener.onVideoClick(video, pos != -1 ? pos : 0);
            }
        });
    }

    private void loadThumbnailAsync(Context context, VideoItem video, String cacheKey, ImageView imageView) {
        final Context appContext = context.getApplicationContext();
        thumbnailExecutor.execute(() -> {
            Bitmap thumb = com.example.animelib.util.VideoThumbnailHelper.createFrameThumbnail(
                    appContext,
                    video.getPath(),
                    video.getUri(),
                    video.getDurationMs()
            );

            if (thumb != null) {
                final Bitmap finalThumb = thumb;
                if (cacheKey != null) {
                    thumbnailCache.put(cacheKey, finalThumb);
                }
                mainHandler.post(() -> {
                    if (cacheKey != null && cacheKey.equals(imageView.getTag())) {
                        imageView.setImageBitmap(finalThumb);
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeaderTitle;
        TextView tvDateHeaderCount;

        DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateHeaderTitle = itemView.findViewById(R.id.tvDateHeaderTitle);
            tvDateHeaderCount = itemView.findViewById(R.id.tvDateHeaderCount);
        }
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivVideoThumbnail;
        TextView tvVideoDuration;
        TextView tvVideoTitle;
        TextView tvVideoNewTag;
        TextView tvVideoExtensionTag;
        TextView tvVideoFolder;
        TextView tvVideoSize;
        TextView tvWatchProgressStatus;
        TextView tvSelectionBadge;
        M3LinearProgressIndicator pbWatchProgress;
        ImageButton btnVideoMore;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
            tvVideoDuration = itemView.findViewById(R.id.tvVideoDuration);
            tvVideoTitle = itemView.findViewById(R.id.tvVideoTitle);
            tvVideoNewTag = itemView.findViewById(R.id.tvVideoNewTag);
            tvVideoExtensionTag = itemView.findViewById(R.id.tvVideoExtensionTag);
            tvVideoFolder = itemView.findViewById(R.id.tvVideoFolder);
            tvVideoSize = itemView.findViewById(R.id.tvVideoSize);
            tvWatchProgressStatus = itemView.findViewById(R.id.tvWatchProgressStatus);
            tvSelectionBadge = itemView.findViewById(R.id.tvSelectionBadge);
            pbWatchProgress = itemView.findViewById(R.id.pbWatchProgress);
            btnVideoMore = itemView.findViewById(R.id.btnVideoMore);
        }
    }
}
