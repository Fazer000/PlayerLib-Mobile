package com.example.animelib.fragments;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.graphics.Bitmap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.animelib.MainActivity;
import com.example.animelib.R;
import com.example.animelib.VideoPlayerActivity;
import com.example.animelib.adapters.VideoFilesAdapter;
import com.example.animelib.adapters.VideoFoldersAdapter;
import com.example.animelib.data.DatabaseManager;
import com.example.animelib.data.entity.PlaylistEntity;
import com.example.animelib.data.entity.PlaylistVideoEntity;
import com.example.animelib.data.entity.VideoPlaybackHistoryEntity;
import com.example.animelib.domain.VideoListFilter;
import com.example.animelib.models.VideoFolder;
import com.example.animelib.models.VideoItem;
import com.example.animelib.repository.LocalVideoRepository;
import android.net.Uri;
import com.example.animelib.settings.AddToPlaylistBottomSheet;
import com.example.animelib.settings.PlaylistVideosBottomSheet;
import com.example.animelib.settings.VideoInfoBottomSheet;
import com.example.animelib.settings.VideoOptionsBottomSheet;
import com.example.animelib.ui.M3LinearProgressIndicator;
import com.example.animelib.util.CustomToast;
import com.example.animelib.util.DialogUtils;
import com.example.animelib.util.VideoThumbnailHelper;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ExplorerFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvVideos, rvFolders;
    private View layoutEmptyState, cardFolderCarousel, layoutControlButtonsContainer, layoutNormalButtons;
    private View layoutTopControls, layoutFloatingExplorerControls;
    private View layoutPlaylistCreationBar, layoutExpandedSearch;
    private TextView tvEmptyTitle, tvEmptySubtitle, tvPlaylistSelectionCount;
    private EditText etSearchVideos;
    private ImageButton btnSort, btnCreatePlaylist, btnSearchToggle, btnCloseSearch;
    private ImageButton btnCancelPlaylist, btnDonePlaylist;
    private com.google.android.material.button.MaterialButton btnEmptyAction;
    private ProgressBar progressBar;

    // Last Played Video block views
    private MaterialCardView cardLastPlayedVideo;
    private ImageView ivLastPlayedThumbnail;
    private TextView tvLastPlayedDuration;
    private TextView tvLastPlayedProgressPercent;
    private TextView tvLastPlayedTitle;
    private TextView tvLastPlayedSubtitle;
    private M3LinearProgressIndicator pbLastPlayedProgress;
    private View btnLastPlayedPlayCard;
    private ImageButton btnLastPlayedPlay;
    private View btnLastPlayedPlaylistCard;
    private ImageButton btnLastPlayedPlaylist;

    private VideoFilesAdapter videoFilesAdapter;
    private VideoFoldersAdapter videoFoldersAdapter;

    private final List<VideoItem> allVideos = new ArrayList<>();
    private final List<VideoItem> displayedVideos = new ArrayList<>();
    private final List<VideoFolder> foldersList = new ArrayList<>();

    private DatabaseManager databaseManager;
    private LocalVideoRepository videoRepository;
    private final VideoListFilter videoFilter = new VideoListFilter();

    private String selectedFolder = "Все";
    private String currentSearchQuery = "";
    private VideoListFilter.SortOrder currentSortOrder = VideoListFilter.SortOrder.DATE_DESC;
    private TextView tabRegularVideos, tabShortVideos;
    private View layoutTabsContainer, viewTabIndicator, layoutTabButtons, cardVideoTabs;
    private float currentTabProgress = 0.0f;
    private ValueAnimator tabPillAnimator;
    private final android.animation.ArgbEvaluator argbEvaluator = new android.animation.ArgbEvaluator();
    private boolean isShortsTabSelected = false;
    private boolean isPlaylistCreationMode = false;
    private boolean isGridViewMode = false;

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = false;
                    for (Boolean isGranted : result.values()) {
                        if (isGranted) {
                            granted = true;
                            break;
                        }
                    }
                    if (granted) {
                        loadVideosFromStorage();
                    } else {
                        showPermissionDeniedState();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explorer, container, false);
    }

    private int currentTopInset = 0;
    private int currentNavBarBottomInset = 0;
    private int currentImeBottomInset = 0;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            updateWindowInsets(statusBarInsets.top, navBarInsets.bottom, imeInsets.bottom);
            return insets;
        });

        ViewCompat.setWindowInsetsAnimationCallback(view, new androidx.core.view.WindowInsetsAnimationCompat.Callback(androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
            @NonNull
            @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull java.util.List<androidx.core.view.WindowInsetsAnimationCompat> runningAnimations) {
                Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                updateWindowInsets(statusBarInsets.top, navBarInsets.bottom, imeInsets.bottom);
                return insets;
            }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutExpandedSearch != null && layoutExpandedSearch.getVisibility() == View.VISIBLE) {
                    closeSearchAnimated();
                } else if (isPlaylistCreationMode) {
                    exitPlaylistCreationMode();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        databaseManager = DatabaseManager.getInstance(requireContext());
        videoRepository = new LocalVideoRepository(requireContext(), databaseManager);

        // Find views
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        rvVideos = view.findViewById(R.id.rvVideos);
        rvFolders = view.findViewById(R.id.rvFolders);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        cardFolderCarousel = view.findViewById(R.id.cardFolderCarousel);
        layoutTopControls = view.findViewById(R.id.layoutTopControls);
        layoutFloatingExplorerControls = view.findViewById(R.id.layoutFloatingExplorerControls);
        layoutControlButtonsContainer = view.findViewById(R.id.layoutControlButtonsContainer);
        layoutNormalButtons = view.findViewById(R.id.layoutNormalButtons);
        layoutPlaylistCreationBar = view.findViewById(R.id.layoutPlaylistCreationBar);
        layoutExpandedSearch = view.findViewById(R.id.layoutExpandedSearch);

        if (layoutTopControls != null) {
            layoutTopControls.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int newHeight = bottom - top;
                int oldHeight = oldBottom - oldTop;
                if (newHeight != oldHeight) {
                    applyListPaddings();
                }
            });
        }

        if (layoutFloatingExplorerControls != null) {
            layoutFloatingExplorerControls.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int newHeight = bottom - top;
                int oldHeight = oldBottom - oldTop;
                if (newHeight != oldHeight) {
                    applyListPaddings();
                }
            });
        }

        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = view.findViewById(R.id.tvEmptySubtitle);
        tvPlaylistSelectionCount = view.findViewById(R.id.tvPlaylistSelectionCount);
        btnEmptyAction = view.findViewById(R.id.btnEmptyAction);

        tabRegularVideos = view.findViewById(R.id.tabRegularVideos);
        tabShortVideos = view.findViewById(R.id.tabShortVideos);
        layoutTabsContainer = view.findViewById(R.id.layoutTabsContainer);
        viewTabIndicator = view.findViewById(R.id.viewTabIndicator);
        layoutTabButtons = view.findViewById(R.id.layoutTabButtons);
        cardVideoTabs = view.findViewById(R.id.cardVideoTabs);
        etSearchVideos = view.findViewById(R.id.etSearchVideos);
        btnSort = view.findViewById(R.id.btnSort);
        btnCreatePlaylist = view.findViewById(R.id.btnCreatePlaylist);
        btnSearchToggle = view.findViewById(R.id.btnSearchToggle);
        btnCloseSearch = view.findViewById(R.id.btnCloseSearch);
        btnCancelPlaylist = view.findViewById(R.id.btnCancelPlaylist);
        btnDonePlaylist = view.findViewById(R.id.btnDonePlaylist);
        progressBar = view.findViewById(R.id.progressBar);

        cardLastPlayedVideo = view.findViewById(R.id.cardLastPlayedVideo);
        ivLastPlayedThumbnail = view.findViewById(R.id.ivLastPlayedThumbnail);
        tvLastPlayedDuration = view.findViewById(R.id.tvLastPlayedDuration);
        tvLastPlayedProgressPercent = view.findViewById(R.id.tvLastPlayedProgressPercent);
        tvLastPlayedTitle = view.findViewById(R.id.tvLastPlayedTitle);
        tvLastPlayedSubtitle = view.findViewById(R.id.tvLastPlayedSubtitle);
        pbLastPlayedProgress = view.findViewById(R.id.pbLastPlayedProgress);
        btnLastPlayedPlayCard = view.findViewById(R.id.btnLastPlayedPlayCard);
        btnLastPlayedPlay = view.findViewById(R.id.btnLastPlayedPlay);
        btnLastPlayedPlaylistCard = view.findViewById(R.id.btnLastPlayedPlaylistCard);
        btnLastPlayedPlaylist = view.findViewById(R.id.btnLastPlayedPlaylist);

        try {
            SharedPreferences prefs = requireContext().getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE);
            String savedSort = prefs.getString("explorer_sort_order", VideoListFilter.SortOrder.DATE_DESC.name());
            currentSortOrder = VideoListFilter.SortOrder.valueOf(savedSort);
            isGridViewMode = prefs.getBoolean("explorer_is_grid_view", false);
        } catch (Exception e) {
            currentSortOrder = VideoListFilter.SortOrder.DATE_DESC;
            isGridViewMode = false;
        }

        setupRecyclerViews();
        setupListeners();
        setupTabDragListener();
        loadAndDisplayLastPlayedVideo();

        if (hasStoragePermission()) {
            loadVideosFromStorage();
        } else {
            showPermissionDeniedState();
        }
    }

    private void applyListPaddings() {
        if (rvVideos == null || !isAdded()) return;
        float density = getResources().getDisplayMetrics().density;

        int topMargin = currentTopInset + (int) (8 * density);
        int topControlsHeight = (layoutTopControls != null && layoutTopControls.getHeight() > 0)
                ? layoutTopControls.getHeight()
                : (int) (52 * density);
        int totalTopPadding = topMargin + topControlsHeight + (int) (14 * density);

        int targetBottomMargin;
        if (currentImeBottomInset > currentNavBarBottomInset) {
            targetBottomMargin = currentImeBottomInset + (int) (12 * density);
        } else {
            targetBottomMargin = currentNavBarBottomInset + (int) (76 * density);
        }
        int bottomControlsHeight = (layoutFloatingExplorerControls != null && layoutFloatingExplorerControls.getHeight() > 0)
                ? layoutFloatingExplorerControls.getHeight()
                : (int) (64 * density);
        int totalBottomPadding = targetBottomMargin + bottomControlsHeight + (int) (16 * density);

        int horizontalPadding = (isGridViewMode || isShortsTabSelected) ? (int) (9 * density) : 0;
        rvVideos.setPadding(horizontalPadding, totalTopPadding, horizontalPadding, totalBottomPadding);
        rvVideos.setClipToPadding(false);
        rvVideos.setClipChildren(false);
    }

    public void updateWindowInsets(int topInset, int navBarBottomInset, int imeBottomInset) {
        this.currentTopInset = topInset;
        this.currentNavBarBottomInset = navBarBottomInset;
        this.currentImeBottomInset = imeBottomInset;
        if (getView() == null || !isAdded()) return;

        float density = getResources().getDisplayMetrics().density;

        if (layoutTopControls != null && layoutTopControls.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) layoutTopControls.getLayoutParams();
            int targetTopMargin = topInset + (int) (8 * density);
            if (lp.topMargin != targetTopMargin) {
                lp.topMargin = targetTopMargin;
                layoutTopControls.setLayoutParams(lp);
            }
        }

        int targetBottomMargin;
        if (imeBottomInset > navBarBottomInset) {
            targetBottomMargin = imeBottomInset + (int) (12 * density);
        } else {
            targetBottomMargin = navBarBottomInset + (int) (76 * density);
        }

        if (layoutFloatingExplorerControls != null && layoutFloatingExplorerControls.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) layoutFloatingExplorerControls.getLayoutParams();
            if (lp.bottomMargin != targetBottomMargin) {
                lp.bottomMargin = targetBottomMargin;
                layoutFloatingExplorerControls.setLayoutParams(lp);
            }
        }

        applyListPaddings();
    }

    public void updateWindowInsets(int topInset, int bottomInset) {
        updateWindowInsets(topInset, bottomInset, currentImeBottomInset);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            if (allVideos.isEmpty()) {
                loadVideosFromStorage();
            } else {
                Executors.newSingleThreadExecutor().execute(() -> {
                    boolean changed = videoRepository.refreshWatchProgress(allVideos);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (changed) {
                                filterAndDisplayVideos();
                            }
                        });
                    }
                });
            }
        }
        loadAndDisplayLastPlayedVideo();
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_VIDEO});
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
        }
    }

    private void setupRecyclerViews() {
        rvFolders.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        videoFoldersAdapter = new VideoFoldersAdapter(foldersList, folder -> {
            selectedFolder = folder.getName();
            filterAndDisplayVideos();
        });
        rvFolders.setAdapter(videoFoldersAdapter);

        videoFilesAdapter = new VideoFilesAdapter(displayedVideos, new VideoFilesAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(VideoItem video, int position) {
                if (isPlaylistCreationMode) {
                    videoFilesAdapter.toggleSelection(video);
                    updatePlaylistSelectionBar();
                } else {
                    openVideoInPlayer(video);
                }
            }

            @Override
            public void onVideoMoreClick(VideoItem video, int position) {
                showVideoOptionsBottomSheet(video);
            }
        });
        updateLayoutManager();
        rvVideos.setAdapter(videoFilesAdapter);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadVideosFromStorage();
                swipeRefreshLayout.setRefreshing(false);
            });
        }
    }

    private void updateLayoutManager() {
        if (rvVideos == null || getContext() == null) return;
        boolean forceGrid = isShortsTabSelected || isGridViewMode;
        if (forceGrid) {
            androidx.recyclerview.widget.GridLayoutManager glm = new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3);
            glm.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (videoFilesAdapter != null && videoFilesAdapter.getItemViewType(position) == VideoFilesAdapter.VIEW_TYPE_HEADER) {
                        return 3;
                    }
                    return 1;
                }
            });
            rvVideos.setLayoutManager(glm);
        } else {
            rvVideos.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        if (videoFilesAdapter != null) {
            videoFilesAdapter.setShortsTab(isShortsTabSelected);
            videoFilesAdapter.setGridView(isGridViewMode);
        }
        applyListPaddings();
    }

    private void setGridViewMode(boolean isGrid) {
        if (this.isGridViewMode == isGrid) return;
        this.isGridViewMode = isGrid;
        if (getContext() != null) {
            try {
                requireContext().getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("explorer_is_grid_view", isGridViewMode)
                        .apply();
            } catch (Exception ignored) {}
        }
        updateLayoutManager();
    }

    private void setupListeners() {
        if (layoutTabsContainer != null) {
            layoutTabsContainer.post(() -> updateTabIndicator(isShortsTabSelected ? 1.0f : 0.0f));
            layoutTabsContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                updateTabIndicator(isShortsTabSelected ? 1.0f : 0.0f);
            });
        }



        if (btnSort != null) {
            btnSort.setOnClickListener(this::showSortMenu);
        }
        if (btnCreatePlaylist != null) {
            btnCreatePlaylist.setOnClickListener(v -> startPlaylistCreationMode());
        }
        if (btnSearchToggle != null) {
            btnSearchToggle.setOnClickListener(v -> openSearchAnimated());
        }
        if (btnCloseSearch != null) {
            btnCloseSearch.setOnClickListener(v -> closeSearchAnimated());
        }
        if (btnCancelPlaylist != null) {
            btnCancelPlaylist.setOnClickListener(v -> exitPlaylistCreationMode());
        }
        if (btnDonePlaylist != null) {
            btnDonePlaylist.setOnClickListener(v -> promptForPlaylistNameAndSave());
        }

        etSearchVideos.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim() : "";
                filterAndDisplayVideos();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (btnEmptyAction != null) {
            btnEmptyAction.setOnClickListener(v -> {
                if (!hasStoragePermission()) {
                    requestStoragePermission();
                } else {
                    loadVideosFromStorage();
                }
            });
        }
    }

    private void openSearchAnimated() {
        if (layoutExpandedSearch == null || layoutNormalButtons == null || layoutControlButtonsContainer == null) return;

        layoutControlButtonsContainer.post(() -> {
            if (!isAdded() || getView() == null) return;
            int totalWidth = layoutControlButtonsContainer.getWidth();
            int startWidth = (int) (48 * getResources().getDisplayMetrics().density);

            layoutNormalButtons.setVisibility(View.GONE);
            layoutExpandedSearch.setVisibility(View.VISIBLE);

            ViewGroup.LayoutParams lp = layoutExpandedSearch.getLayoutParams();
            lp.width = startWidth;
            layoutExpandedSearch.setLayoutParams(lp);

            ValueAnimator animator = ValueAnimator.ofInt(startWidth, totalWidth);
            animator.setDuration(220);
            animator.setInterpolator(new DecelerateInterpolator(1.6f));
            animator.addUpdateListener(animation -> {
                int val = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams p = layoutExpandedSearch.getLayoutParams();
                p.width = val;
                layoutExpandedSearch.setLayoutParams(p);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ViewGroup.LayoutParams p = layoutExpandedSearch.getLayoutParams();
                    p.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    layoutExpandedSearch.setLayoutParams(p);
                }
            });
            animator.start();

            etSearchVideos.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etSearchVideos, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void closeSearchAnimated() {
        if (layoutExpandedSearch == null || layoutNormalButtons == null) return;

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearchVideos.getWindowToken(), 0);
        }

        int currentWidth = layoutExpandedSearch.getWidth();
        int targetWidth = (int) (48 * getResources().getDisplayMetrics().density);

        ValueAnimator animator = ValueAnimator.ofInt(currentWidth, targetWidth);
        animator.setDuration(180);
        animator.setInterpolator(new AccelerateInterpolator(1.6f));
        animator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams lp = layoutExpandedSearch.getLayoutParams();
            lp.width = val;
            layoutExpandedSearch.setLayoutParams(lp);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                etSearchVideos.setText("");
                layoutExpandedSearch.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = layoutExpandedSearch.getLayoutParams();
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                layoutExpandedSearch.setLayoutParams(lp);
                layoutNormalButtons.setVisibility(View.VISIBLE);
            }
        });
        animator.start();
    }

    private void updateTabIndicator(float progress) {
        this.currentTabProgress = Math.max(0.0f, Math.min(1.0f, progress));
        if (tabRegularVideos == null || tabShortVideos == null || viewTabIndicator == null) return;

        int w0 = tabRegularVideos.getWidth();
        int w1 = tabShortVideos.getWidth();

        if (w0 == 0 || w1 == 0) return;

        int containerLeft = layoutTabButtons != null ? layoutTabButtons.getLeft() : 0;
        float left0 = containerLeft + tabRegularVideos.getLeft();
        float left1 = containerLeft + tabShortVideos.getLeft();

        float targetWidth = w0 + currentTabProgress * (w1 - w0);
        float targetX = left0 + currentTabProgress * (left1 - left0);

        ViewGroup.LayoutParams lp = viewTabIndicator.getLayoutParams();
        if (lp != null && lp.width != (int) targetWidth) {
            lp.width = (int) targetWidth;
            viewTabIndicator.setLayoutParams(lp);
        }
        viewTabIndicator.setX(targetX);

        if (isAdded() && getContext() != null) {
            int whiteColor = ContextCompat.getColor(requireContext(), R.color.white_color);
            int inactiveColor = ContextCompat.getColor(requireContext(), R.color.player_item_inactive_text);

            int regularTextColor = (Integer) argbEvaluator.evaluate(currentTabProgress, whiteColor, inactiveColor);
            int shortsTextColor = (Integer) argbEvaluator.evaluate(currentTabProgress, inactiveColor, whiteColor);

            tabRegularVideos.setTextColor(regularTextColor);
            tabShortVideos.setTextColor(shortsTextColor);
        }
    }

    private void animateTabPill(boolean toShorts, @Nullable Runnable onEnd) {
        float start = currentTabProgress;
        float target = toShorts ? 1.0f : 0.0f;

        if (tabPillAnimator != null && tabPillAnimator.isRunning()) {
            tabPillAnimator.cancel();
        }

        if (Math.abs(start - target) < 0.01f) {
            updateTabIndicator(target);
            if (onEnd != null) onEnd.run();
            return;
        }

        tabPillAnimator = ValueAnimator.ofFloat(start, target);
        tabPillAnimator.setDuration(220);
        tabPillAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        tabPillAnimator.addUpdateListener(anim -> {
            float val = (Float) anim.getAnimatedValue();
            updateTabIndicator(val);
        });
        tabPillAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                updateTabIndicator(target);
                if (onEnd != null) onEnd.run();
            }
        });
        tabPillAnimator.start();
    }

    private void setupTabDragListener() {
        if (layoutTabsContainer == null) return;

        View.OnTouchListener tabTouchListener = new View.OnTouchListener() {
            private float downX = 0f;
            private float downY = 0f;
            private float initialProgress = 0f;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        initialProgress = currentTabProgress;
                        isDragging = false;

                        // Immediately request all parent views to disallow intercepting touch events
                        ViewParent parent = v.getParent();
                        while (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                            parent = parent.getParent();
                        }

                        if (tabPillAnimator != null && tabPillAnimator.isRunning()) {
                            tabPillAnimator.cancel();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        int touchSlop = android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();

                        if (!isDragging) {
                            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                                isDragging = true;
                            }
                        }

                        if (isDragging) {
                            ViewParent p = v.getParent();
                            while (p != null) {
                                p.requestDisallowInterceptTouchEvent(true);
                                p = p.getParent();
                            }

                            float distanceBetweenTabs = 0f;
                            if (tabShortVideos != null && tabRegularVideos != null) {
                                distanceBetweenTabs = tabShortVideos.getLeft() - tabRegularVideos.getLeft();
                            }
                            if (distanceBetweenTabs <= 0 && tabRegularVideos != null && tabRegularVideos.getWidth() > 0) {
                                distanceBetweenTabs = tabRegularVideos.getWidth();
                            }
                            if (distanceBetweenTabs <= 0) {
                                distanceBetweenTabs = 120f;
                            }

                            float deltaProgress = dx / distanceBetweenTabs;
                            float newProgress = Math.max(0.0f, Math.min(1.0f, initialProgress + deltaProgress));
                            updateTabIndicator(newProgress);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!isDragging) {
                            boolean targetShorts;
                            if (v == tabShortVideos) {
                                targetShorts = true;
                            } else if (v == tabRegularVideos) {
                                targetShorts = false;
                            } else {
                                int[] location = new int[2];
                                if (tabShortVideos != null) {
                                    tabShortVideos.getLocationOnScreen(location);
                                    targetShorts = event.getRawX() >= location[0];
                                } else {
                                    targetShorts = !isShortsTabSelected;
                                }
                            }
                            selectTabWithAnimation(targetShorts);
                        } else {
                            float dxFinal = event.getRawX() - downX;
                            boolean targetShorts;
                            if (dxFinal > 20f) {
                                targetShorts = true;
                            } else if (dxFinal < -20f) {
                                targetShorts = false;
                            } else {
                                targetShorts = currentTabProgress >= 0.5f;
                            }
                            selectTabWithAnimation(targetShorts);
                        }
                        isDragging = false;
                        return true;
                }
                return false;
            }
        };

        layoutTabsContainer.setOnTouchListener(tabTouchListener);
        if (cardVideoTabs != null) cardVideoTabs.setOnTouchListener(tabTouchListener);
        if (tabRegularVideos != null) tabRegularVideos.setOnTouchListener(tabTouchListener);
        if (tabShortVideos != null) tabShortVideos.setOnTouchListener(tabTouchListener);
    }

    private void selectTabWithAnimation(boolean targetShorts) {
        animateTabPill(targetShorts, () -> selectTab(targetShorts));
    }

    private void selectTab(boolean isShorts) {
        this.isShortsTabSelected = isShorts;
        if (layoutTabsContainer != null) {
            layoutTabsContainer.post(() -> updateTabIndicator(isShorts ? 1.0f : 0.0f));
        }

        if (allVideos != null) {
            foldersList.clear();
            foldersList.addAll(videoFilter.buildFolders(allVideos, isShortsTabSelected));
            if (videoFoldersAdapter != null) {
                videoFoldersAdapter.notifyDataSetChanged();
            }
        }

        updateLayoutManager();
        filterAndDisplayVideos();
    }

    public void loadVideosFromStorage() {
        if (getContext() == null || videoRepository == null) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        Executors.newSingleThreadExecutor().execute(() -> {
            List<VideoItem> scannedVideos = videoRepository.scanVideos();
            if (getActivity() == null) return;

            getActivity().runOnUiThread(() -> {
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                allVideos.clear();
                allVideos.addAll(scannedVideos);

                foldersList.clear();
                foldersList.addAll(videoFilter.buildFolders(allVideos, isShortsTabSelected));
                videoFoldersAdapter.notifyDataSetChanged();

                filterAndDisplayVideos();
            });
        });
    }

    private void filterAndDisplayVideos() {
        VideoListFilter.SortOrder sortOrder = currentSortOrder != null ? currentSortOrder : VideoListFilter.SortOrder.DATE_DESC;

        List<VideoItem> filtered = videoFilter.filterAndSort(
                allVideos,
                selectedFolder,
                currentSearchQuery,
                sortOrder,
                isShortsTabSelected
        );

        displayedVideos.clear();
        displayedVideos.addAll(filtered);
        videoFilesAdapter.updateVideos(displayedVideos);

        if (displayedVideos.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvVideos.setVisibility(View.GONE);
            if (isShortsTabSelected) {
                tvEmptyTitle.setText("Короткие видео не найдены");
                tvEmptySubtitle.setText("Проверьте наличие вертикальных видеофайлов на устройстве");
            } else {
                tvEmptyTitle.setText("Видео не найдены");
                tvEmptySubtitle.setText("Проверьте наличие видеофайлов на устройстве или добавьте видео в папку");
            }
            btnEmptyAction.setText("Обновить");
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            rvVideos.setVisibility(View.VISIBLE);
        }
    }

    private void showPermissionDeniedState() {
        layoutEmptyState.setVisibility(View.VISIBLE);
        rvVideos.setVisibility(View.GONE);
        tvEmptyTitle.setText("Доступ к файлам не предоставлен");
        tvEmptySubtitle.setText("Предоставьте разрешение на чтение видеофайлов для сканирования локального хранилища");
        btnEmptyAction.setText("Предоставить доступ");
    }

    private void openVideoInPlayer(VideoItem video) {
        int index = displayedVideos.indexOf(video);
        if (index == -1) index = 0;

        Intent intent = new Intent(requireContext(), VideoPlayerActivity.class);
        intent.putExtra("VIDEO_LIST", new ArrayList<>(displayedVideos));
        intent.putExtra("START_INDEX", index);
        startActivity(intent);
    }

    private void showVideoOptionsBottomSheet(VideoItem video) {
        if (!isAdded() || getContext() == null || video == null) return;
        VideoOptionsBottomSheet bs = new VideoOptionsBottomSheet(requireContext(), video, new VideoOptionsBottomSheet.VideoActionCallback() {
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
                showVideoInfoDialog(video);
            }

            @Override
            public void onDelete(VideoItem video) {
                confirmDeleteVideo(video);
            }
        });
        bs.show();
    }

    private void shareVideo(VideoItem video) {
        if (!isAdded() || getContext() == null || video == null) return;
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("video/*");
            Uri uri = video.getUri();
            if (uri == null && video.getPath() != null) {
                File file = new File(video.getPath());
                uri = androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        file
                );
            }
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Поделиться видео"));
        } catch (Exception e) {
            CustomToast.showWarning(requireContext(), "Не удалось поделиться видео");
        }
    }

    private void showAddToPlaylistDialog(VideoItem video) {
        if (!isAdded() || getContext() == null || video == null) return;
        AddToPlaylistBottomSheet bs = new AddToPlaylistBottomSheet(requireContext(), video, playlistName -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).notifyPlaylistsChanged();
            }
        });
        bs.show();
    }

    private void showRenameVideoDialog(VideoItem video) {
        if (!isAdded() || getContext() == null || video == null) return;
        String currentName = video.getDisplayTitle();
        DialogUtils.showInputDialog(requireContext(), "Переименовать видео", "Введите новое имя:", currentName, "Имя файла", "Сохранить", "Отмена", new DialogUtils.InputDialogListener() {
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
                databaseManager.updateVideoTitle(video.getPath(), fullNewName);
                videoFilesAdapter.updateVideos(displayedVideos);
                loadAndDisplayLastPlayedVideo();
                CustomToast.showSuccess(requireContext(), "Видео переименовано");
            }

            @Override
            public void onCancelled() {}
        });
    }

    private void showVideoInfoDialog(VideoItem video) {
        if (!isAdded() || getContext() == null || video == null) return;
        VideoInfoBottomSheet infoBs = new VideoInfoBottomSheet(requireContext(), video);
        infoBs.show();
    }

    private void confirmDeleteVideo(VideoItem video) {
        if (!isAdded() || getContext() == null || video == null) return;
        DialogUtils.showConfirmDialog(
                requireContext(),
                "Удалить видео?",
                "Вы действительно хотите удалить «" + video.getDisplayTitle() + "»? Это действие нельзя отменить.",
                "Удалить",
                "Отмена",
                true,
                new DialogUtils.DialogListener() {
                    @Override
                    public void onPositiveClick() {
                        deleteVideoFile(video);
                    }

                    @Override
                    public void onNegativeClick() {}
                }
        );
    }

    private void deleteVideoFile(VideoItem video) {
        if (!isAdded() || getContext() == null || video == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            if (video.getPath() != null) {
                try {
                    File file = new File(video.getPath());
                    if (file.exists()) {
                        file.delete();
                    }
                } catch (Exception ignored) {}
            }
            if (video.getUri() != null && getContext() != null) {
                try {
                    requireContext().getContentResolver().delete(video.getUri(), null, null);
                } catch (Exception ignored) {}
            }
            databaseManager.deleteVideoEverywhere(video.getPath());

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                allVideos.remove(video);
                displayedVideos.remove(video);
                videoFilesAdapter.updateVideos(displayedVideos);
                foldersList.clear();
                foldersList.addAll(videoFilter.buildFolders(allVideos));
                videoFoldersAdapter.notifyDataSetChanged();
                loadAndDisplayLastPlayedVideo();
                CustomToast.showSuccess(requireContext(), "Видео удалено");
            });
        });
    }

    private void showSortDialog() {
        if (btnSort != null) {
            showSortMenu(btnSort);
        }
    }

    /**
     * Показывает выпадающий список вариантов сортировки видео (PopupWindow),
     * оформленный в стиле AnimeLib-Mobile (bg_sort_popup), аналогично выпадающему меню в BS плейлиста.
     */
    private void showSortMenu(View anchorView) {
        if (anchorView == null || getContext() == null) return;
        try {
            View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup_video_sort, null);
            float density = getContext().getResources().getDisplayMetrics().density;
            int width = (int) (230 * density);
            PopupWindow popupWindow = new PopupWindow(
                    popupView,
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );
            popupWindow.setOutsideTouchable(true);
            popupWindow.setFocusable(true);
            popupWindow.setElevation(14 * density);
            popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

            setupSortItem(popupView, popupWindow, R.id.popup_sort_date_desc, R.id.iv_check_date_desc, R.id.tv_title_date_desc, R.id.iv_icon_date_desc, VideoListFilter.SortOrder.DATE_DESC);
            setupSortItem(popupView, popupWindow, R.id.popup_sort_date_asc, R.id.iv_check_date_asc, R.id.tv_title_date_asc, R.id.iv_icon_date_asc, VideoListFilter.SortOrder.DATE_ASC);
            setupSortItem(popupView, popupWindow, R.id.popup_sort_name_asc, R.id.iv_check_name_asc, R.id.tv_title_name_asc, R.id.iv_icon_name_asc, VideoListFilter.SortOrder.NAME_ASC);
            setupSortItem(popupView, popupWindow, R.id.popup_sort_name_desc, R.id.iv_check_name_desc, R.id.tv_title_name_desc, R.id.iv_icon_name_desc, VideoListFilter.SortOrder.NAME_DESC);
            setupSortItem(popupView, popupWindow, R.id.popup_sort_size_desc, R.id.iv_check_size_desc, R.id.tv_title_size_desc, R.id.iv_icon_size_desc, VideoListFilter.SortOrder.SIZE_DESC);
            setupSortItem(popupView, popupWindow, R.id.popup_sort_size_asc, R.id.iv_check_size_asc, R.id.tv_title_size_asc, R.id.iv_icon_size_asc, VideoListFilter.SortOrder.SIZE_ASC);

            View viewModeList = popupView.findViewById(R.id.popup_view_mode_list);
            View viewModeGrid = popupView.findViewById(R.id.popup_view_mode_grid);
            View dividerViewMode = popupView.findViewById(R.id.divider_view_mode);

            if (isShortsTabSelected) {
                if (viewModeList != null) viewModeList.setVisibility(View.GONE);
                if (viewModeGrid != null) viewModeGrid.setVisibility(View.GONE);
                if (dividerViewMode != null) dividerViewMode.setVisibility(View.GONE);
            } else {
                if (viewModeList != null) viewModeList.setVisibility(View.VISIBLE);
                if (viewModeGrid != null) viewModeGrid.setVisibility(View.VISIBLE);
                if (dividerViewMode != null) dividerViewMode.setVisibility(View.VISIBLE);
                setupViewModeItem(popupView, popupWindow, R.id.popup_view_mode_list, R.id.iv_check_view_list, R.id.tv_title_view_list, R.id.iv_icon_view_list, false);
                setupViewModeItem(popupView, popupWindow, R.id.popup_view_mode_grid, R.id.iv_check_view_grid, R.id.tv_title_view_grid, R.id.iv_icon_view_grid, true);
            }

            int xOffset = -(width - anchorView.getWidth());
            int yOffset = (int) (6 * density);
            popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupViewModeItem(View popupView, PopupWindow popupWindow, int itemId, int checkId, int titleId, int iconId, boolean isGridTarget) {
        View item = popupView.findViewById(itemId);
        View check = popupView.findViewById(checkId);
        TextView title = popupView.findViewById(titleId);
        ImageView icon = popupView.findViewById(iconId);

        boolean isSelected = (isGridViewMode == isGridTarget);
        if (check != null) {
            check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }
        if (title != null && getContext() != null) {
            int color = isSelected
                    ? ContextCompat.getColor(requireContext(), R.color.accent_color)
                    : ContextCompat.getColor(requireContext(), R.color.primary_text_color);
            title.setTextColor(color);
        }
        if (icon != null && getContext() != null) {
            int color = isSelected
                    ? ContextCompat.getColor(requireContext(), R.color.accent_color)
                    : ContextCompat.getColor(requireContext(), R.color.primary_text_color);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }

        if (item != null) {
            item.setOnClickListener(v -> {
                popupWindow.dismiss();
                setGridViewMode(isGridTarget);
            });
        }
    }

    private void setupSortItem(View popupView, PopupWindow popupWindow, int itemId, int checkId, int titleId, int iconId, VideoListFilter.SortOrder order) {
        View item = popupView.findViewById(itemId);
        View check = popupView.findViewById(checkId);
        TextView title = popupView.findViewById(titleId);
        ImageView icon = popupView.findViewById(iconId);

        boolean isSelected = (currentSortOrder == order);
        if (check != null) {
            check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }
        if (title != null && getContext() != null) {
            int color = isSelected
                    ? ContextCompat.getColor(requireContext(), R.color.accent_color)
                    : ContextCompat.getColor(requireContext(), R.color.primary_text_color);
            title.setTextColor(color);
        }
        if (icon != null && getContext() != null) {
            int color = isSelected
                    ? ContextCompat.getColor(requireContext(), R.color.accent_color)
                    : ContextCompat.getColor(requireContext(), R.color.primary_text_color);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }

        if (item != null) {
            item.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (currentSortOrder != order) {
                    currentSortOrder = order;
                    if (getContext() != null) {
                        try {
                            requireContext().getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("explorer_sort_order", currentSortOrder.name())
                                    .apply();
                        } catch (Exception ignored) {}
                    }
                    filterAndDisplayVideos();
                }
            });
        }
    }

    private void startPlaylistCreationMode() {
        isPlaylistCreationMode = true;
        if (cardLastPlayedVideo != null) cardLastPlayedVideo.setVisibility(View.GONE);
        layoutPlaylistCreationBar.setVisibility(View.VISIBLE);
        btnSort.setVisibility(View.GONE);
        btnCreatePlaylist.setVisibility(View.GONE);
        videoFilesAdapter.setSelectionMode(true);
        updatePlaylistSelectionBar();
        CustomToast.showInfo(requireContext(), "Выберите видео по порядку для создания плейлиста");
    }

    private void exitPlaylistCreationMode() {
        isPlaylistCreationMode = false;
        layoutPlaylistCreationBar.setVisibility(View.GONE);
        btnSort.setVisibility(View.VISIBLE);
        btnCreatePlaylist.setVisibility(View.VISIBLE);
        videoFilesAdapter.setSelectionMode(false);
        loadAndDisplayLastPlayedVideo();
    }

    private void updatePlaylistSelectionBar() {
        int selectedCount = videoFilesAdapter.getSelectedVideos().size();
        tvPlaylistSelectionCount.setText("Выбрано: " + selectedCount + " видео");
    }

    private void promptForPlaylistNameAndSave() {
        List<VideoItem> selected = videoFilesAdapter.getSelectedVideos();
        if (selected.isEmpty()) {
            CustomToast.showWarning(requireContext(), "Выберите хотя бы одно видео");
            return;
        }

        DialogUtils.showInputDialog(requireContext(), "Название плейлиста", "Введите имя плейлиста", "", "Имя плейлиста", "Создать", "Отмена", new DialogUtils.InputDialogListener() {
            @Override
            public void onInputConfirmed(String input) {
                if (input == null || input.trim().isEmpty()) {
                    CustomToast.showWarning(requireContext(), "Имя плейлиста не может быть пустым");
                    return;
                }

                long id = databaseManager.createPlaylist(input.trim(), selected);
                if (id > 0) {
                    CustomToast.showSuccess(requireContext(), "Плейлист «" + input + "» создан!");
                    exitPlaylistCreationMode();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).notifyPlaylistsChanged();
                    }
                } else {
                    CustomToast.showWarning(requireContext(), "Ошибка создания плейлиста");
                }
            }

            @Override
            public void onCancelled() {}
        });
    }

    private void loadAndDisplayLastPlayedVideo() {
        if (!isAdded() || getContext() == null || databaseManager == null) return;
        if (isPlaylistCreationMode) {
            if (cardLastPlayedVideo != null) cardLastPlayedVideo.setVisibility(View.GONE);
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            VideoPlaybackHistoryEntity lastVideo = databaseManager.getLastPlayedVideo();
            if (lastVideo == null || lastVideo.getFilePath() == null) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (cardLastPlayedVideo != null) cardLastPlayedVideo.setVisibility(View.GONE);
                    });
                }
                return;
            }

            File file = new File(lastVideo.getFilePath());
            if (!file.exists()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (cardLastPlayedVideo != null) cardLastPlayedVideo.setVisibility(View.GONE);
                    });
                }
                return;
            }

            PlaylistEntity playlist = databaseManager.getPlaylistForVideoPath(lastVideo.getFilePath());
            Bitmap thumb = VideoThumbnailHelper.createFrameThumbnail(getContext(), lastVideo.getFilePath(), null, lastVideo.getDurationMs());

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || cardLastPlayedVideo == null || isPlaylistCreationMode) return;

                    cardLastPlayedVideo.setVisibility(View.VISIBLE);

                    if (thumb != null) {
                        ivLastPlayedThumbnail.setImageBitmap(thumb);
                    } else {
                        ivLastPlayedThumbnail.setImageResource(R.drawable.player_item_background);
                    }

                    if (lastVideo.getDurationMs() > 0) {
                        long totalSec = lastVideo.getDurationMs() / 1000;
                        long min = totalSec / 60;
                        long sec = totalSec % 60;
                        tvLastPlayedDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));
                        tvLastPlayedDuration.setVisibility(View.VISIBLE);
                    } else {
                        tvLastPlayedDuration.setVisibility(View.GONE);
                    }

                    int percent = lastVideo.getProgressPercentage();
                    if (percent > 0) {
                        tvLastPlayedProgressPercent.setText(percent + "%");
                        tvLastPlayedProgressPercent.setVisibility(View.VISIBLE);
                        pbLastPlayedProgress.setVisibility(View.VISIBLE);
                        pbLastPlayedProgress.setProgress(percent, false);
                    } else {
                        tvLastPlayedProgressPercent.setVisibility(View.GONE);
                        pbLastPlayedProgress.setVisibility(View.GONE);
                    }

                    String title = lastVideo.getTitle();
                    if (title == null || title.trim().isEmpty()) {
                        title = file.getName();
                    }
                    if (title != null && title.contains(".")) {
                        int dot = title.lastIndexOf('.');
                        if (dot > 0) {
                            title = title.substring(0, dot);
                        }
                    }
                    tvLastPlayedTitle.setText(title);

                    String subtitle;
                    if (lastVideo.getLastPositionMs() > 0) {
                        long curSec = lastVideo.getLastPositionMs() / 1000;
                        long curMin = curSec / 60;
                        long s = curSec % 60;
                        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", curMin, s);
                        subtitle = "Продолжить с " + timeStr + (percent > 0 ? " • " + percent + "%" : "");
                    } else {
                        subtitle = "Начать просмотр" + (lastVideo.getFolderName() != null && !lastVideo.getFolderName().isEmpty() ? " • " + lastVideo.getFolderName() : "");
                    }
                    tvLastPlayedSubtitle.setText(subtitle);

                    View.OnClickListener playListener = v -> playLastVideo(lastVideo, playlist);
                    cardLastPlayedVideo.setOnClickListener(playListener);
                    btnLastPlayedPlayCard.setOnClickListener(playListener);
                    btnLastPlayedPlay.setOnClickListener(playListener);

                    if (playlist != null) {
                        btnLastPlayedPlaylistCard.setVisibility(View.VISIBLE);
                        View.OnClickListener playlistClickListener = v -> {
                            PlaylistVideosBottomSheet bs = new PlaylistVideosBottomSheet(requireContext(), playlist);
                            bs.setOnDismissListener(dialog -> loadAndDisplayLastPlayedVideo());
                            bs.show();
                        };
                        btnLastPlayedPlaylistCard.setOnClickListener(playlistClickListener);
                        btnLastPlayedPlaylist.setOnClickListener(playlistClickListener);
                    } else {
                        btnLastPlayedPlaylistCard.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void playLastVideo(VideoPlaybackHistoryEntity lastVideo, PlaylistEntity playlist) {
        if (!isAdded() || getContext() == null || lastVideo == null) return;

        if (playlist != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                List<PlaylistVideoEntity> videoEntities = databaseManager.getVideosForPlaylist(playlist.getId());
                if (videoEntities != null && !videoEntities.isEmpty() && getActivity() != null) {
                    ArrayList<VideoItem> playlistVideos = new ArrayList<>();
                    int startIndex = 0;
                    long tempId = 1;
                    for (int i = 0; i < videoEntities.size(); i++) {
                        PlaylistVideoEntity entity = videoEntities.get(i);
                        String path = entity.getVideoPath();
                        File file = new File(path);
                        String title = entity.getVideoTitle() != null ? entity.getVideoTitle() : file.getName();
                        if (path.equals(lastVideo.getFilePath())) {
                            startIndex = i;
                        }
                        playlistVideos.add(new VideoItem(tempId++, title, path, null, 0, file.length(), file.lastModified(), playlist.getName()));
                    }

                    int finalStartIndex = startIndex;
                    getActivity().runOnUiThread(() -> {
                        Intent intent = new Intent(requireContext(), VideoPlayerActivity.class);
                        intent.putExtra("VIDEO_LIST", playlistVideos);
                        intent.putExtra("START_INDEX", finalStartIndex);
                        intent.putExtra("PLAYLIST_NAME", playlist.getName());
                        intent.putExtra("EXTRA_PLAYLIST_ID", playlist.getId());
                        startActivity(intent);
                    });
                    return;
                }

                // Fallback to single video play if playlist has no videos
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> playSingleOrFromAll(lastVideo));
                }
            });
        } else {
            playSingleOrFromAll(lastVideo);
        }
    }

    private void playSingleOrFromAll(VideoPlaybackHistoryEntity lastVideo) {
        if (!isAdded() || getContext() == null || lastVideo == null) return;

        int foundIndex = -1;
        for (int i = 0; i < displayedVideos.size(); i++) {
            if (lastVideo.getFilePath().equals(displayedVideos.get(i).getPath())) {
                foundIndex = i;
                break;
            }
        }

        if (foundIndex != -1) {
            Intent intent = new Intent(requireContext(), VideoPlayerActivity.class);
            intent.putExtra("VIDEO_LIST", new ArrayList<>(displayedVideos));
            intent.putExtra("START_INDEX", foundIndex);
            startActivity(intent);
        } else {
            for (int i = 0; i < allVideos.size(); i++) {
                if (lastVideo.getFilePath().equals(allVideos.get(i).getPath())) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex != -1) {
                Intent intent = new Intent(requireContext(), VideoPlayerActivity.class);
                intent.putExtra("VIDEO_LIST", new ArrayList<>(allVideos));
                intent.putExtra("START_INDEX", foundIndex);
                startActivity(intent);
            } else {
                File file = new File(lastVideo.getFilePath());
                ArrayList<VideoItem> singleList = new ArrayList<>();
                singleList.add(new VideoItem(1, lastVideo.getTitle() != null ? lastVideo.getTitle() : file.getName(), lastVideo.getFilePath(), null, lastVideo.getDurationMs(), file.length(), file.lastModified(), lastVideo.getFolderName()));
                Intent intent = new Intent(requireContext(), VideoPlayerActivity.class);
                intent.putExtra("VIDEO_LIST", singleList);
                intent.putExtra("START_INDEX", 0);
                startActivity(intent);
            }
        }
    }
}
