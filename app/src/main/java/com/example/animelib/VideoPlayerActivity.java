package com.example.animelib;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;
import com.example.animelib.views.TimebarSegmentsView;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import java.lang.reflect.Field;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.animelib.adapters.PlaylistEpisodesAdapter;
import com.example.animelib.models.VideoItem;

import okhttp3.OkHttpClient;

import com.example.animelib.controller.PlayerGestureController;
import com.example.animelib.controller.PlayerLockController;
import com.example.animelib.controller.PlayerPlaylistManager;
import com.example.animelib.controller.PlayerSystemUiController;
import com.example.animelib.data.DatabaseManager;
import com.example.animelib.data.entity.VideoPlaybackHistoryEntity;
import com.example.animelib.managers.AmbientLightManager;
import com.example.animelib.managers.SurroundSoundManager;
import com.example.animelib.managers.VideoFiltersManager;
import com.example.animelib.settings.AddToPlaylistBottomSheet;
import com.example.animelib.settings.SettingsBottomSheet;
import com.example.animelib.settings.VideoOptionsBottomSheet;
import com.example.animelib.settings.VideoInfoBottomSheet;
import com.example.animelib.settings.VideoFiltersBottomSheet;
import com.example.animelib.util.DialogUtils;
import com.example.animelib.ui.AmbientVignetteOverlayView;
import com.example.animelib.util.CustomToast;
import com.example.animelib.util.SurroundRenderersFactory;
import com.example.animelib.util.ThemeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

@UnstableApi
public class VideoPlayerActivity extends AppCompatActivity implements PlayerGestureController.GestureCallback {

    private static final String TAG = "VideoPlayerActivity";

    // Subsystem Controllers
    private PlayerSystemUiController systemUiController;
    private PlayerLockController lockController;
    private PlayerPlaylistManager playlistManager;
    private PlayerGestureController gestureController;

    // Playback & UI
    private PlayerView playerView;
    private ExoPlayer player;
    private DatabaseManager databaseManager;

    private TextView tvAnimeTitle;
    private TextView tvAnimeSubtitle;
    private TextView tvPortraitAnimeTitle;
    private TextView tvPortraitPlaylistInfo;
    TextView tvPortraitFormatTag;
    private TextView tvPortraitNewTag;
    private TextView tvPortraitFolderTag;
    private TextView tvPortraitEpisodesHeaderTitle;
    private TextView tvPortraitEpisodesCountBadge;
    private RecyclerView rvPlaylistEpisodes;
    private PlaylistEpisodesAdapter playlistEpisodesAdapter;
    private ImageButton btnClose;
    private ImageButton btnSettings;
    private ImageButton btnPip;
    private ImageButton btnPlay;
    private ImageButton btnPause;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnSkipForward;
    private ImageButton btnToggleFullscreen;
    private ImageButton btnLockScreen;
    private View loadingOverlay;
    private View bufferingIndicator;

    // Feature Managers
    private AmbientLightManager ambientLightManager;
    private SurroundSoundManager surroundSoundManager;
    private VideoFiltersManager videoFiltersManager;

    // Playback Preferences & State
    private int currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private float currentPlaybackSpeed = 1.0f;
    private int skipDurationSeconds = 85;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            saveCurrentPosition();
            progressHandler.postDelayed(this, 2000);
        }
    };

    // TimeBar & Progress State
    private TimebarSegmentsView timebarSegmentsView;
    private DefaultTimeBar timeBar;
    private boolean isScrubbing = false;
    private final Handler timebarUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable timebarUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimebarProgress();
            if (player != null && player.isPlaying()) {
                timebarUpdateHandler.postDelayed(this, 100);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        databaseManager = DatabaseManager.getInstance(this);
        ThemeUtils.applyTheme(databaseManager.loadThemeSetting());

        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_AnimeLIB_VideoPlayer);
        setContentView(R.layout.activity_video_player);

        View rootLayout = findViewById(R.id.playerRootLayout);
        View loadingOverlay = findViewById(R.id.loadingOverlay);
        boolean isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bgColor = isDarkMode ? getResources().getColor(R.color.dt_primary_color, getTheme()) : android.graphics.Color.WHITE;
        if (rootLayout != null) {
            rootLayout.setBackgroundColor(bgColor);
        }
        if (loadingOverlay != null) {
            loadingOverlay.setBackgroundColor(bgColor);
        }
        View portraitBottomContent = findViewById(R.id.portraitBottomContent);
        if (portraitBottomContent != null) {
            portraitBottomContent.setBackgroundColor(bgColor);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        systemUiController = new PlayerSystemUiController(this);
        systemUiController.hideSystemUI();

        playlistManager = new PlayerPlaylistManager();
        playlistManager.parseIntent(getIntent());

        skipDurationSeconds = databaseManager.loadLongSkipDurationSetting();

        initViews();
        setupExoPlayer();
        setupControllers();
        setupClickListeners();

        View mainVideoLayout = findViewById(R.id.mainVideoLayout);
        if (mainVideoLayout != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainVideoLayout, (v, insets) -> {
                int topInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top;
                if (topInset > 0) {
                    statusBarHeight = topInset;
                }
                updateLayoutForOrientation(getResources().getConfiguration().orientation);
                return insets;
            });
        }
        updateLayoutForOrientation(getResources().getConfiguration().orientation);

        playCurrentVideo(true);
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        tvAnimeTitle = findViewById(R.id.animeTitle);
        tvAnimeSubtitle = findViewById(R.id.animeSubtitle);
        tvPortraitAnimeTitle = findViewById(R.id.tvPortraitAnimeTitle);
        btnClose = findViewById(R.id.ibClosePlayer);
        btnSettings = findViewById(R.id.settingsButton);
        btnPip = findViewById(R.id.pipButton);
        btnPlay = findViewById(R.id.btnPlayerPlay);
        btnPause = findViewById(R.id.btnPlayerPause);
        btnPrev = findViewById(R.id.prevEpisodeButton);
        btnNext = findViewById(R.id.nextEpisodeButton);
        btnSkipForward = findViewById(R.id.skipForwardButton);
        btnToggleFullscreen = findViewById(R.id.menuToggleFullscreen);
        btnLockScreen = findViewById(R.id.btnLockScreen);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        bufferingIndicator = findViewById(R.id.playerBufferingIndicator);

        if (btnLockScreen != null) {
            btnLockScreen.setVisibility(View.VISIBLE);
        }

        rvPlaylistEpisodes = findViewById(R.id.rvPlaylistEpisodes);
        tvPortraitPlaylistInfo = findViewById(R.id.tvPortraitPlaylistInfo);
        tvPortraitFormatTag = findViewById(R.id.tvPortraitFormatTag);
        tvPortraitNewTag = findViewById(R.id.tvPortraitNewTag);
        tvPortraitFolderTag = findViewById(R.id.tvPortraitFolderTag);
        tvPortraitEpisodesHeaderTitle = findViewById(R.id.tvPortraitEpisodesHeaderTitle);
        tvPortraitEpisodesCountBadge = findViewById(R.id.tvPortraitEpisodesCountBadge);

        if (rvPlaylistEpisodes != null) {
            rvPlaylistEpisodes.setLayoutManager(new LinearLayoutManager(this));
            playlistEpisodesAdapter = new PlaylistEpisodesAdapter(
                    playlistManager.getPlaylistTitles(),
                    playlistManager.getPlaylistPaths(),
                    playlistManager.getCurrentIndex(),
                    new PlaylistEpisodesAdapter.OnEpisodeClickListener() {
                        @Override
                        public void onEpisodeClick(int position) {
                            playVideoAtIndex(position, true);
                        }

                        @Override
                        public void onEpisodeMoreClick(VideoItem video, int position) {
                            showVideoOptionsBottomSheet(video);
                        }
                    }
            );
            rvPlaylistEpisodes.setAdapter(playlistEpisodesAdapter);
        }

        // Initialize Managers
        surroundSoundManager = new SurroundSoundManager(this);
        surroundSoundManager.setEnabled(databaseManager.loadSurroundSoundSetting());

        videoFiltersManager = new VideoFiltersManager(this, playerView);
        float[] filters = databaseManager.loadVideoFilters();
        videoFiltersManager.setFilters(filters[0], filters[1], filters[2], filters[3], filters[4]);

        View ambientContainer = findViewById(R.id.ambientContainer);
        PlayerView ambientPlayerView = findViewById(R.id.ambientPlayerView);
        ImageView ambientImageView = findViewById(R.id.ambientImageView);
        AmbientVignetteOverlayView ambientVignette = findViewById(R.id.ambientVignetteOverlay);

        if (ambientContainer != null) {
            ambientLightManager = new AmbientLightManager(this, playerView, ambientContainer,
                    ambientPlayerView, ambientImageView, ambientVignette);
            ambientLightManager.setEnabled(true);
            databaseManager.saveAmbientLightSetting(true);
        }
    }

    private void setupControllers() {
        FrameLayout layoutLockedOverlay = findViewById(R.id.layoutLockedOverlay);
        LinearLayout lockedStatusBadge = findViewById(R.id.lockedStatusBadge);

        lockController = new PlayerLockController(this, layoutLockedOverlay, lockedStatusBadge);
        lockController.setOnLockStateChangeListener(isLocked -> {
            if (isLocked) {
                playerView.hideController();
            } else {
                playerView.showController();
            }
        });

        gestureController = new PlayerGestureController(
                this,
                playerView,
                findViewById(R.id.seekPreviewText),
                findViewById(R.id.holdSpeedToast),
                findViewById(R.id.skipIndicatorLeft),
                findViewById(R.id.skipTextLeft),
                findViewById(R.id.skipIndicatorRight),
                findViewById(R.id.skipTextRight),
                findViewById(R.id.gestureIndicatorPlaque),
                findViewById(R.id.gestureIndicatorIcon),
                findViewById(R.id.gestureIndicatorProgress),
                findViewById(R.id.gestureIndicatorText),
                this
        );
        gestureController.updateOrientation(getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
    }

    private void setupClickListeners() {
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());
        if (btnSettings != null) btnSettings.setOnClickListener(v -> {
            if (playerView != null) playerView.showController();
            showSettingsBottomSheet();
        });
        if (btnPip != null) btnPip.setOnClickListener(v -> enterPipMode());
        if (btnToggleFullscreen != null) btnToggleFullscreen.setOnClickListener(v -> {
            if (playerView != null) playerView.showController();
            toggleFullscreen();
        });
        if (btnLockScreen != null) btnLockScreen.setOnClickListener(v -> lockController.lock());

        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> {
                if (player != null) {
                    player.play();
                    updatePlayPauseButtons(true);
                    if (playerView != null) playerView.showController();
                }
            });
        }
        if (btnPause != null) {
            btnPause.setOnClickListener(v -> {
                if (player != null) {
                    player.pause();
                    updatePlayPauseButtons(false);
                    if (playerView != null) playerView.showController();
                }
            });
        }
        if (btnNext != null) btnNext.setOnClickListener(v -> {
            if (playerView != null) playerView.showController();
            playNextVideo();
        });
        if (btnPrev != null) btnPrev.setOnClickListener(v -> {
            if (playerView != null) playerView.showController();
            playPreviousVideo();
        });

        if (btnSkipForward != null) {
            btnSkipForward.setOnClickListener(v -> {
                if (player != null) {
                    long targetPos = Math.min(player.getDuration(), player.getCurrentPosition() + (skipDurationSeconds * 1000L));
                    player.seekTo(targetPos);
                    updateTimebarProgress();
                    gestureController.showDoubleTapSkipIndicator(true, skipDurationSeconds);
                    if (playerView != null) playerView.showController();
                }
            });
        }
    }

    private void setupExoPlayer() {
        SurroundRenderersFactory renderersFactory = new SurroundRenderersFactory(
                this,
                surroundSoundManager.getSurroundAudioProcessor()
        );

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 50000, 1500, 2500)
                .setBackBuffer(10000, true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    okhttp3.Request original = chain.request();
                    okhttp3.Request request = original.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .build();
                    return chain.proceed(request);
                })
                .build();
        OkHttpDataSource.Factory okHttpDataSourceFactory = new OkHttpDataSource.Factory(okHttpClient);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, okHttpDataSourceFactory);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory);

        player = new ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();

        playerView.setPlayer(player);
        currentResizeMode = databaseManager.loadResizeModeSetting();
        playerView.setResizeMode(currentResizeMode);
        playerView.setControllerShowTimeoutMs(3000);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            boolean isVisible = (visibility == View.VISIBLE);
            animateControlsVisibility(isVisible);
            if (isVisible && timeBar == null) {
                setupTimeBar();
            }

            if (systemUiController != null) {
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    systemUiController.hideSystemUI();
                } else {
                    systemUiController.showSystemUI();
                }
            }
        });

        if (ambientLightManager != null) {
            ambientLightManager.setPlayer(player);
        }

        surroundSoundManager.attachPlayer(player);
        setupTimeBar();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    if (bufferingIndicator != null) bufferingIndicator.setVisibility(View.VISIBLE);
                } else {
                    if (bufferingIndicator != null) bufferingIndicator.setVisibility(View.GONE);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                }

                if (playbackState == Player.STATE_READY) {
                    updateTimebarProgress();
                }

                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButtons(isPlaying);
                if (isPlaying) {
                    progressHandler.post(progressRunnable);
                    startTimebarUpdates();
                } else {
                    saveCurrentPosition();
                    progressHandler.removeCallbacks(progressRunnable);
                    stopTimebarUpdates();
                    updateTimebarProgress();
                }
            }

            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                updateTimebarProgress();
            }

            @Override
            public void onTimelineChanged(@NonNull androidx.media3.common.Timeline timeline, int reason) {
                updateTimebarProgress();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                CustomToast.showWarning(VideoPlayerActivity.this, "Ошибка воспроизведения: " + error.getMessage());
            }
        });
    }

    private String stripExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            return filename.substring(0, dot);
        }
        return filename;
    }

    private void updatePlayerTitles() {
        String mainTitle;
        String subtitleText;

        if (playlistManager.isPlaylist()) {
            mainTitle = playlistManager.getPlaylistName();
            int episodeNum = playlistManager.getCurrentIndex() + 1;
            subtitleText = "Серия " + episodeNum;
        } else {
            mainTitle = stripExtension(playlistManager.getCurrentTitle());
            subtitleText = null;
        }

        // Top player controller (Landscape & Portrait video overlay)
        if (tvAnimeTitle != null) {
            tvAnimeTitle.setText(mainTitle);
        }
        if (tvAnimeSubtitle != null) {
            if (subtitleText != null) {
                tvAnimeSubtitle.setText(subtitleText);
                tvAnimeSubtitle.setVisibility(View.VISIBLE);
            } else {
                tvAnimeSubtitle.setVisibility(View.GONE);
            }
        }

        VideoItem video = getCurrentVideoItem();

        // Portrait content below player
        if (tvPortraitAnimeTitle != null) {
            tvPortraitAnimeTitle.setText(mainTitle);
        }
        if (tvPortraitPlaylistInfo != null) {
            String sizeStr = video != null ? video.getFormattedSize() : "";
            String dateStr = video != null ? video.getFormattedDate() : "";
            if (!sizeStr.isEmpty() && !dateStr.isEmpty()) {
                tvPortraitPlaylistInfo.setText(sizeStr + " • " + dateStr);
            } else if (!sizeStr.isEmpty()) {
                tvPortraitPlaylistInfo.setText(sizeStr);
            } else if (!dateStr.isEmpty()) {
                tvPortraitPlaylistInfo.setText(dateStr);
            } else {
                String folderName = playlistManager.getCurrentFolderName();
                String infoStr = subtitleText != null ? subtitleText : (folderName != null ? "Папка: " + folderName : "Видео");
                tvPortraitPlaylistInfo.setText(infoStr);
            }
            tvPortraitPlaylistInfo.setVisibility(View.VISIBLE);
        }

        if (video != null) {
            if (tvPortraitNewTag != null) {
                tvPortraitNewTag.setVisibility(video.isNew() ? View.VISIBLE : View.GONE);
            }
            if (tvPortraitFormatTag != null) {
                String ext = video.getFileExtension();
                if (!ext.isEmpty() && ext.length() <= 5) {
                    tvPortraitFormatTag.setText(ext);
                    tvPortraitFormatTag.setVisibility(View.VISIBLE);
                } else {
                    tvPortraitFormatTag.setVisibility(View.GONE);
                }
            }
            if (tvPortraitFolderTag != null) {
                String folderName = video.getFolderName();
                if (folderName != null && !folderName.isEmpty()) {
                    tvPortraitFolderTag.setText(folderName);
                    tvPortraitFolderTag.setVisibility(View.VISIBLE);
                } else {
                    tvPortraitFolderTag.setVisibility(View.GONE);
                }
            }
        }

        int totalCount = playlistManager.getPlaylistPaths() != null ? playlistManager.getPlaylistPaths().size() : 0;
        int currentIdx = playlistManager.getCurrentIndex() + 1;

        if (tvPortraitEpisodesHeaderTitle != null) {
            if (playlistManager.isPlaylist()) {
                tvPortraitEpisodesHeaderTitle.setText("Серии в плейлисте");
            } else {
                tvPortraitEpisodesHeaderTitle.setText("Видео в этой папке");
            }
        }

        if (tvPortraitEpisodesCountBadge != null) {
            if (totalCount > 0) {
                tvPortraitEpisodesCountBadge.setText(currentIdx + " / " + totalCount);
                tvPortraitEpisodesCountBadge.setVisibility(View.VISIBLE);
            } else {
                tvPortraitEpisodesCountBadge.setVisibility(View.GONE);
            }
        }
    }

    private VideoItem getCurrentVideoItem() {
        if (playlistManager == null) return null;
        String path = playlistManager.getCurrentPath();
        String title = playlistManager.getCurrentTitle();
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        long size = f.exists() ? f.length() : 0;
        long modified = f.exists() ? f.lastModified() / 1000L : 0;
        String folder = playlistManager.getCurrentFolderName();
        if (folder == null || folder.isEmpty()) {
            folder = (f.getParentFile() != null) ? f.getParentFile().getName() : "Видео";
        }
        long duration = (player != null && player.getDuration() > 0) ? player.getDuration() : 0;
        return new VideoItem(playlistManager.getCurrentIndex(), title, path, null, duration, size, modified, folder);
    }

    private void playCurrentVideo(boolean shouldResume) {
        String videoPath = playlistManager.getCurrentPath();
        if (videoPath == null || videoPath.isEmpty()) {
            CustomToast.showWarning(this, "Путь к видео не указан");
            finish();
            return;
        }

        updatePlayerTitles();

        if (playlistEpisodesAdapter != null) {
            playlistEpisodesAdapter.setCurrentIndex(playlistManager.getCurrentIndex());
        }

        Uri videoUri;
        if (videoPath.startsWith("content://") || videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
            videoUri = Uri.parse(videoPath);
        } else {
            videoUri = Uri.fromFile(new File(videoPath));
        }

        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        player.setMediaItem(mediaItem);

        if (ambientLightManager != null) {
            ambientLightManager.setVideoUrl(videoPath);
        }

        long resumePosition = 0;
        if (shouldResume) {
            VideoPlaybackHistoryEntity history = databaseManager.getVideoHistory(videoPath);
            if (history != null && history.getLastPositionMs() > 5000) {
                resumePosition = history.getLastPositionMs();
            }
        }

        player.prepare();
        if (resumePosition > 0) {
            player.seekTo(resumePosition);
        }
        player.play();
        updateTimebarProgress();

        databaseManager.saveVideoProgress(
                videoPath,
                playlistManager.getCurrentTitle(),
                0,
                resumePosition,
                playlistManager.getCurrentFolderName()
        );

        updatePlaylistNavigationButtons();
    }

    private void playVideoAtIndex(int index, boolean shouldResume) {
        if (playlistManager.isEmpty() || index < 0 || index >= playlistManager.getPlaylistPaths().size()) return;
        saveCurrentPosition();
        playlistManager.setCurrentIndex(index);
        playCurrentVideo(shouldResume);
        if (playlistEpisodesAdapter != null) {
            playlistEpisodesAdapter.setCurrentIndex(index);
        }
    }

    private void playNextVideo() {
        if (playlistManager.hasNext()) {
            playVideoAtIndex(playlistManager.getNextIndex(), false);
        } else {
            CustomToast.showInfo(this, "Это последнее видео в списке");
        }
    }

    private void playPreviousVideo() {
        if (playlistManager.hasPrevious()) {
            playVideoAtIndex(playlistManager.getPreviousIndex(), false);
        } else {
            CustomToast.showInfo(this, "Это первое видео в списке");
        }
    }

    private void onPlaybackEnded() {
        saveCurrentPosition();
        if (databaseManager.loadAutoPlaySetting() && playlistManager.hasNext()) {
            CustomToast.showInfo(this, "Воспроизведение следующего видео...");
            playNextVideo();
        }
    }

    private void updatePlaylistNavigationButtons() {
        if (btnPrev != null) {
            btnPrev.setAlpha(playlistManager.hasPrevious() ? 1.0f : 0.4f);
            btnPrev.setEnabled(playlistManager.hasPrevious());
        }
        if (btnNext != null) {
            btnNext.setAlpha(playlistManager.hasNext() ? 1.0f : 0.4f);
            btnNext.setEnabled(playlistManager.hasNext());
        }
    }

    private void updatePlayPauseButtons(boolean isPlaying) {
        if (btnPlay == null || btnPause == null) return;

        View showView = isPlaying ? btnPause : btnPlay;
        View hideView = isPlaying ? btnPlay : btnPause;

        if (showView.getVisibility() == View.VISIBLE && showView.getAlpha() == 1.0f && hideView.getVisibility() == View.GONE) {
            return;
        }

        hideView.animate().cancel();
        showView.animate().cancel();

        hideView.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .setDuration(150)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    hideView.setVisibility(View.GONE);
                    hideView.setScaleX(1f);
                    hideView.setScaleY(1f);
                    hideView.setAlpha(1f);
                })
                .start();

        showView.setVisibility(View.VISIBLE);
        showView.setAlpha(0f);
        showView.setScaleX(0.5f);
        showView.setScaleY(0.5f);
        showView.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator(1.4f))
                .start();
    }

    private void animateControlsVisibility(boolean isVisible) {
        View topBar = findViewById(R.id.topBarContainer);
        View bottomBar = findViewById(R.id.playersControlBar);
        View centerBar = findViewById(R.id.centerControlsContainer);
        View btnLock = findViewById(R.id.btnLockScreen);

        long duration = isVisible ? 220L : 180L;
        Interpolator interpolator = isVisible 
                ? new DecelerateInterpolator(1.2f) 
                : new AccelerateInterpolator(1.2f);

        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        View[] viewsToAnimate = new View[]{topBar, bottomBar, centerBar, isPortrait ? null : btnLock};

        for (View view : viewsToAnimate) {
            if (view == null) continue;

            view.animate().cancel();
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);

            if (isVisible) {
                if (view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE);
                    view.setAlpha(0f);
                }
                view.animate()
                        .alpha(1f)
                        .setDuration(duration)
                        .setInterpolator(interpolator)
                        .start();
            } else {
                view.animate()
                        .alpha(0f)
                        .setDuration(duration)
                        .setInterpolator(interpolator)
                        .withEndAction(() -> {
                            view.setVisibility(View.GONE);
                            view.setAlpha(1f);
                        })
                        .start();
            }
        }

        if (btnLock != null && isPortrait) {
            btnLock.animate().cancel();
            btnLock.setVisibility(View.GONE);
        }
    }

    private void saveCurrentPosition() {
        if (player == null) return;
        String path = playlistManager.getCurrentPath();
        if (path == null || path.isEmpty()) return;

        long currentPos = player.getCurrentPosition();
        long duration = player.getDuration();
        databaseManager.saveVideoProgress(
                path,
                playlistManager.getCurrentTitle(),
                duration > 0 ? duration : 0,
                currentPos,
                playlistManager.getCurrentFolderName()
        );
    }

    private void toggleFullscreen() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Rational rational = new Rational(16, 9);
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .build();
                enterPictureInPictureMode(params);
            } catch (Exception e) {
                CustomToast.showWarning(this, "Режим PiP недоступен");
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            playerView.hideController();
            findViewById(R.id.layoutLockedOverlay).setVisibility(View.GONE);
        } else {
            playerView.showController();
            systemUiController.hideSystemUI();
        }
    }

    private void showSettingsBottomSheet() {
        SettingsBottomSheet bottomSheet = new SettingsBottomSheet(
                this,
                currentPlaybackSpeed,
                speed -> {
                    currentPlaybackSpeed = speed;
                    if (player != null) player.setPlaybackParameters(new PlaybackParameters(speed));
                },
                databaseManager.loadAmbientLightSetting(),
                enabled -> {
                    databaseManager.saveAmbientLightSetting(enabled);
                    if (ambientLightManager != null) ambientLightManager.setEnabled(enabled);
                },
                databaseManager.loadSurroundSoundSetting(),
                enabled -> {
                    databaseManager.saveSurroundSoundSetting(enabled);
                    surroundSoundManager.setEnabled(enabled);
                },
                databaseManager.loadAutoPlaySetting(),
                enabled -> databaseManager.saveAutoPlaySetting(enabled),
                skipDurationSeconds,
                duration -> {
                    skipDurationSeconds = duration;
                    databaseManager.saveLongSkipDurationSetting(duration);
                },
                databaseManager.loadThemeSetting(),
                theme -> {
                    databaseManager.saveThemeSetting(theme);
                    ThemeUtils.applyTheme(theme);
                },
                currentResizeMode,
                mode -> {
                    currentResizeMode = mode;
                    if (playerView != null) playerView.setResizeMode(mode);
                }
        );

        bottomSheet.setOfflineMode(true);
        bottomSheet.show();
    }

    // --- GestureCallback Implementation ---

    @Override
    public void onSingleTap() {
        if (playerView.isControllerFullyVisible()) {
            playerView.hideController();
        } else {
            playerView.showController();
        }
    }

    @Override
    public void onSeekCommit(long targetPositionMs) {
        if (player != null) {
            player.seekTo(targetPositionMs);
            updateTimebarProgress();
        }
    }

    @Override
    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    @Override
    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    @Override
    public void onHoldSpeed(boolean isHolding) {
        if (player != null) {
            float speed = isHolding ? 2.0f : currentPlaybackSpeed;
            player.setPlaybackParameters(new PlaybackParameters(speed));
        }
    }

    @Override
    public void onHoldSpeedChanged(float speed) {
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(speed));
        }
    }

    @Override
    public boolean isLocked() {
        return lockController != null && lockController.isLocked();
    }

    @Override
    public int getSkipDurationSeconds() {
        return skipDurationSeconds;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLayoutForOrientation(newConfig.orientation);
        if (ambientLightManager != null) {
            ambientLightManager.onConfigurationChanged();
        }
    }

    private int statusBarHeight = 0;

    private void updateLayoutForOrientation(int orientation) {
        View playerContainer = findViewById(R.id.playerContainer);
        View portraitBottomContent = findViewById(R.id.portraitBottomContent);
        View ambientContainer = findViewById(R.id.ambientContainer);
        View animeInfoContainer = findViewById(R.id.animeInfoContainer);
        View btnLock = findViewById(R.id.btnLockScreen);
        View topBar = findViewById(R.id.topBarContainer);
        View timeAndControls = findViewById(R.id.timeAndControlsContainer);
        View timeBarContainer = findViewById(R.id.timeBarContainer);
        if (timeBar != null) {
            applyRoundedCornersToTimeBar(timeBar);
        }
        updateTimebarProgress();

        if (playerContainer == null) return;

        boolean isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);

        if (gestureController != null) {
            gestureController.updateOrientation(isLandscape);
        }

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) playerContainer.getLayoutParams();

        if (isLandscape) {
            params.width = LinearLayout.LayoutParams.MATCH_PARENT;
            params.height = LinearLayout.LayoutParams.MATCH_PARENT;
            params.topMargin = 0;
            params.leftMargin = 0;
            params.rightMargin = 0;
            params.bottomMargin = 0;
            playerContainer.setLayoutParams(params);

            if (playerContainer instanceof com.example.animelib.ui.RoundedCornerFrameLayout) {
                ((com.example.animelib.ui.RoundedCornerFrameLayout) playerContainer).setCornerRadius(0f);
            }

            if (portraitBottomContent != null) {
                portraitBottomContent.setVisibility(View.GONE);
            }

            if (animeInfoContainer != null) {
                animeInfoContainer.setVisibility(View.VISIBLE);
                if (animeInfoContainer.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
                    android.widget.RelativeLayout.LayoutParams aParams = (android.widget.RelativeLayout.LayoutParams) animeInfoContainer.getLayoutParams();
                    aParams.leftMargin = dpToPx(80);
                    aParams.rightMargin = dpToPx(80);
                    animeInfoContainer.setLayoutParams(aParams);
                }
            }

            if (btnLock != null) {
                btnLock.setVisibility(View.VISIBLE);
            }

            if (topBar != null && topBar.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams topBarParams = (android.view.ViewGroup.MarginLayoutParams) topBar.getLayoutParams();
                topBarParams.topMargin = dpToPx(16);
                topBarParams.leftMargin = dpToPx(16);
                topBarParams.rightMargin = dpToPx(16);
                topBar.setLayoutParams(topBarParams);
            }

            if (timeAndControls != null && timeAndControls.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams tcParams = (android.view.ViewGroup.MarginLayoutParams) timeAndControls.getLayoutParams();
                tcParams.leftMargin = dpToPx(16);
                tcParams.rightMargin = dpToPx(16);
                timeAndControls.setLayoutParams(tcParams);
            }

            if (timeBarContainer != null && timeBarContainer.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams tbParams = (android.view.ViewGroup.MarginLayoutParams) timeBarContainer.getLayoutParams();
                tbParams.leftMargin = dpToPx(8);
                tbParams.rightMargin = dpToPx(8);
                tbParams.bottomMargin = dpToPx(16);
                timeBarContainer.setLayoutParams(tbParams);
            }

            if (systemUiController != null) {
                systemUiController.hideSystemUI();
            }
        } else {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int playerWidth = screenWidth;
            int playerHeight = (int) (playerWidth * (9.0f / 16.0f));

            int topMargin = statusBarHeight > 0 ? statusBarHeight : dpToPx(0);

            params.width = playerWidth;
            params.height = playerHeight;
            params.topMargin = topMargin;
            params.leftMargin = 0;
            params.rightMargin = 0;
            params.bottomMargin = 0;
            playerContainer.setLayoutParams(params);

            if (playerContainer instanceof com.example.animelib.ui.RoundedCornerFrameLayout) {
                ((com.example.animelib.ui.RoundedCornerFrameLayout) playerContainer).setCornerRadius(0f);
            }

            if (portraitBottomContent != null) {
                portraitBottomContent.setVisibility(View.VISIBLE);
            }

            if (animeInfoContainer != null) {
                animeInfoContainer.setVisibility(View.GONE);
            }

            if (btnLock != null) {
                btnLock.setVisibility(View.GONE);
            }

            // Compact control margins in portrait mode
            if (topBar != null && topBar.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams topBarParams = (android.view.ViewGroup.MarginLayoutParams) topBar.getLayoutParams();
                topBarParams.topMargin = dpToPx(6);
                topBarParams.leftMargin = dpToPx(8);
                topBarParams.rightMargin = dpToPx(8);
                topBar.setLayoutParams(topBarParams);
            }

            if (timeAndControls != null && timeAndControls.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams tcParams = (android.view.ViewGroup.MarginLayoutParams) timeAndControls.getLayoutParams();
                tcParams.leftMargin = dpToPx(8);
                tcParams.rightMargin = dpToPx(8);
                timeAndControls.setLayoutParams(tcParams);
            }

            if (timeBarContainer != null && timeBarContainer.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams tbParams = (android.view.ViewGroup.MarginLayoutParams) timeBarContainer.getLayoutParams();
                tbParams.leftMargin = dpToPx(4);
                tbParams.rightMargin = dpToPx(4);
                tbParams.bottomMargin = dpToPx(4);
                timeBarContainer.setLayoutParams(tbParams);
            }

            if (ambientContainer != null) {
                ambientContainer.setVisibility(View.VISIBLE);
            }
            if (ambientLightManager != null) {
                ambientLightManager.setEnabled(true);
            }

            if (systemUiController != null) {
                systemUiController.showSystemUI();
            }
        }

        AmbientVignetteOverlayView ambientVignetteOverlay = findViewById(R.id.ambientVignetteOverlay);

        if (ambientVignetteOverlay != null) {
            ambientVignetteOverlay.setPortraitMode(!isLandscape);
        }

        if (ambientContainer != null) {
            FrameLayout.LayoutParams ambParams = (FrameLayout.LayoutParams) ambientContainer.getLayoutParams();
            if (isLandscape) {
                ambParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                ambParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                ambParams.topMargin = 0;
                ambParams.leftMargin = 0;
                ambParams.rightMargin = 0;
                ambParams.bottomMargin = 0;
            } else {
                int topMargin = statusBarHeight > 0 ? statusBarHeight : dpToPx(24);

                ambParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                ambParams.height = topMargin + dpToPx(48);
                ambParams.topMargin = 0;
                ambParams.leftMargin = 0;
                ambParams.rightMargin = 0;
                ambParams.bottomMargin = 0;
            }
            ambientContainer.setLayoutParams(ambParams);
        }

        updatePlayerTitles();
    }

    private void setupTimeBar() {
        timebarSegmentsView = findViewById(R.id.timebarSegmentsView);
        timeBar = findViewById(R.id.exo_progress);
        if (timebarSegmentsView == null && playerView != null) {
            timebarSegmentsView = playerView.findViewById(R.id.timebarSegmentsView);
        }
        if (timeBar == null && playerView != null) {
            timeBar = playerView.findViewById(R.id.exo_progress);
        }

        if (timeBar != null) {
            applyRoundedCornersToTimeBar(timeBar);
            timeBar.addListener(new TimeBar.OnScrubListener() {
                @Override
                public void onScrubStart(@NonNull TimeBar timeBar, long position) {
                    isScrubbing = true;
                    if (timebarSegmentsView != null && player != null) {
                        timebarSegmentsView.setProgress(position, player.getBufferedPosition(), player.getDuration());
                    }
                }

                @Override
                public void onScrubMove(@NonNull TimeBar timeBar, long position) {
                    isScrubbing = true;
                    if (timebarSegmentsView != null && player != null) {
                        timebarSegmentsView.setProgress(position, player.getBufferedPosition(), player.getDuration());
                    }
                }

                @Override
                public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
                    isScrubbing = false;
                    if (timebarSegmentsView != null && player != null) {
                        timebarSegmentsView.setProgress(position, player.getBufferedPosition(), player.getDuration());
                    }
                }
            });
        }
    }

    private void applyRoundedCornersToTimeBar(View timeBarView) {
        if (timeBarView == null) return;
        try {
            float density = getResources().getDisplayMetrics().density;
            if (timeBarView instanceof com.example.animelib.views.RoundedTimeBar) {
                ((com.example.animelib.views.RoundedTimeBar) timeBarView).setCornerRadius(-1f);
            }
            float radiusPx = 3f * density;
            CornerPathEffect pathEffect = new CornerPathEffect(radiusPx);
            Class<?> clazz = timeBarView.getClass();
            while (clazz != null && clazz != View.class) {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    if (Paint.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Paint paint = (Paint) field.get(timeBarView);
                        if (paint != null) {
                            paint.setPathEffect(pathEffect);
                            paint.setStrokeCap(Paint.Cap.ROUND);
                            paint.setStrokeJoin(Paint.Join.ROUND);
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            timeBarView.invalidate();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to round timebar corners", e);
        }
    }

    private void updateTimebarProgress() {
        if (player != null && timebarSegmentsView != null && !isScrubbing) {
            long duration = player.getDuration();
            if (duration > 0) {
                timebarSegmentsView.setDuration(duration);
                timebarSegmentsView.setProgress(player.getCurrentPosition(), player.getBufferedPosition(), duration);
            }
        }
    }

    private void startTimebarUpdates() {
        stopTimebarUpdates();
        timebarUpdateHandler.post(timebarUpdateRunnable);
    }

    private void stopTimebarUpdates() {
        timebarUpdateHandler.removeCallbacks(timebarUpdateRunnable);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && systemUiController != null) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                systemUiController.hideSystemUI();
            } else {
                systemUiController.showSystemUI();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (systemUiController != null) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                systemUiController.hideSystemUI();
            } else {
                systemUiController.showSystemUI();
            }
        }
    }

    private void showVideoOptionsBottomSheet(VideoItem video) {
        if (isFinishing() || isDestroyed() || video == null) return;
        VideoOptionsBottomSheet bs = new VideoOptionsBottomSheet(this, video, new VideoOptionsBottomSheet.VideoActionCallback() {
            @Override
            public void onShare(VideoItem video) {
                shareVideo(video);
            }

            @Override
            public void onAddToPlaylist(VideoItem video) {
                showAddToPlaylistDialog(video);
            }

            @Override
            public void onRename(VideoItem video) {
                showRenameVideoDialog(video);
            }

            @Override
            public void onInfo(VideoItem video) {
                VideoInfoBottomSheet infoBs = new VideoInfoBottomSheet(VideoPlayerActivity.this, video);
                infoBs.show();
            }

            @Override
            public void onDelete(VideoItem video) {
                confirmDeleteVideo(video);
            }
        });
        bs.show();
    }

    private void shareVideo(VideoItem video) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("video/*");
            Uri uri = video.getUri();
            if (uri == null && video.getPath() != null) {
                File file = new File(video.getPath());
                uri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        file
                );
            }
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Поделиться видео"));
        } catch (Exception e) {
            CustomToast.showWarning(this, "Не удалось поделиться видео");
        }
    }

    private void showAddToPlaylistDialog(VideoItem video) {
        if (video == null) return;
        AddToPlaylistBottomSheet bs = new AddToPlaylistBottomSheet(this, video, null);
        bs.show();
    }

    private void showRenameVideoDialog(VideoItem video) {
        String currentName = video.getDisplayTitle();
        DialogUtils.showInputDialog(this, "Переименовать видео", "Введите новое имя:", currentName, "Имя файла", "Сохранить", "Отмена", new DialogUtils.InputDialogListener() {
            @Override
            public void onInputConfirmed(String input) {
                if (input == null || input.trim().isEmpty() || input.trim().equals(currentName)) return;
                String newName = input.trim();
                String ext = video.getFileExtension();
                String fullNewName = !ext.isEmpty() ? newName + "." + ext.toLowerCase(Locale.ROOT) : newName;

                if (video.getPath() != null) {
                    try {
                        File file = new File(video.getPath());
                        if (file.exists()) {
                            File newFile = new File(file.getParentFile(), fullNewName);
                            file.renameTo(newFile);
                        }
                    } catch (Exception ignored) {}
                }

                video.setTitle(fullNewName);
                if (playlistEpisodesAdapter != null) {
                    playlistEpisodesAdapter.notifyDataSetChanged();
                }
                CustomToast.showSuccess(VideoPlayerActivity.this, "Видео переименовано");
            }

            @Override
            public void onCancelled() {}
        });
    }

    private void confirmDeleteVideo(VideoItem video) {
        DialogUtils.showConfirmDialog(
                this,
                "Удалить видео?",
                "Вы действительно хотите удалить файл «" + video.getDisplayTitle() + "»? Это действие нельзя отменить.",
                "Удалить",
                "Отмена",
                true,
                new DialogUtils.DialogListener() {
                    @Override
                    public void onPositiveClick() {
                        if (video.getPath() != null) {
                            try {
                                File file = new File(video.getPath());
                                if (file.exists()) {
                                    file.delete();
                                }
                            } catch (Exception ignored) {}
                        }
                        CustomToast.showSuccess(VideoPlayerActivity.this, "Видео удалено");
                    }

                    @Override
                    public void onNegativeClick() {}
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimebarUpdates();
        saveCurrentPosition();
        if (player != null && !isInPictureInPictureMode()) {
            player.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveCurrentPosition();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimebarUpdates();
        saveCurrentPosition();
        progressHandler.removeCallbacksAndMessages(null);
        timebarUpdateHandler.removeCallbacksAndMessages(null);

        if (gestureController != null) {
            gestureController.release();
        }
        if (lockController != null) {
            lockController.release();
        }
        if (ambientLightManager != null) {
            ambientLightManager.release();
        }
        if (surroundSoundManager != null) {
            surroundSoundManager.release();
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
