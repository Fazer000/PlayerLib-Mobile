package com.example.animelib;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.animelib.fragments.ExplorerFragment;
import com.example.animelib.fragments.PlaylistsFragment;
import com.example.animelib.fragments.SettingsFragment;
import com.example.animelib.util.ThemeUtils;

public class MainActivity extends AppCompatActivity {

    private View tabExplorer, tabPlaylists, tabSettings;
    private TextView tvTabExplorerText, tvTabPlaylistsText, tvTabSettingsText;
    private ImageView ivTabExplorerIcon, ivTabPlaylistsIcon, ivTabSettingsIcon;
    private View vTabIndicator;

    private ExplorerFragment explorerFragment;
    private PlaylistsFragment playlistsFragment;
    private SettingsFragment settingsFragment;
    private Fragment activeFragment;

    private int currentTabIndex = 0;
    private int cachedTopInset = 0;
    private int cachedBottomInset = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install Android SplashScreen API
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        // Apply theme preference
        ThemeUtils.initTheme(this);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }
        setContentView(R.layout.activity_main);

        setupWindowInsets();
        initViews();
        initFragments(savedInstanceState);
        setupTabListeners();
    }

    private int cachedImeInset = 0;

    private void setupWindowInsets() {
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        View layoutBottomBar = findViewById(R.id.layoutBottomBar);
        if (fragmentContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (v, insets) -> {
                Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                cachedTopInset = statusBarInsets.top;
                cachedBottomInset = navBarInsets.bottom;
                cachedImeInset = imeInsets.bottom;

                v.setPadding(0, 0, 0, 0);

                if (layoutBottomBar != null) {
                    if (imeInsets.bottom > navBarInsets.bottom) {
                        layoutBottomBar.setVisibility(View.GONE);
                    } else {
                        layoutBottomBar.setVisibility(View.VISIBLE);
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) layoutBottomBar.getLayoutParams();
                        int baseBottomMargin = (int) (14 * getResources().getDisplayMetrics().density);
                        lp.bottomMargin = baseBottomMargin + navBarInsets.bottom;
                        layoutBottomBar.setLayoutParams(lp);
                    }
                }

                dispatchWindowInsetsToFragments(cachedTopInset, cachedBottomInset, cachedImeInset);
                return insets;
            });

            ViewCompat.setWindowInsetsAnimationCallback(fragmentContainer, new androidx.core.view.WindowInsetsAnimationCompat.Callback(androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                @NonNull
                @Override
                public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull java.util.List<androidx.core.view.WindowInsetsAnimationCompat> runningAnimations) {
                    Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                    Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                    cachedTopInset = statusBarInsets.top;
                    cachedBottomInset = navBarInsets.bottom;
                    cachedImeInset = imeInsets.bottom;

                    if (layoutBottomBar != null) {
                        if (imeInsets.bottom > navBarInsets.bottom) {
                            layoutBottomBar.setVisibility(View.GONE);
                        } else {
                            layoutBottomBar.setVisibility(View.VISIBLE);
                        }
                    }

                    dispatchWindowInsetsToFragments(statusBarInsets.top, navBarInsets.bottom, imeInsets.bottom);
                    return insets;
                }
            });
        }
    }

    public void dispatchWindowInsetsToFragments(int topInset, int bottomInset, int imeInset) {
        this.cachedTopInset = topInset;
        this.cachedBottomInset = bottomInset;
        this.cachedImeInset = imeInset;
        if (explorerFragment != null && explorerFragment.isAdded()) {
            explorerFragment.updateWindowInsets(topInset, bottomInset, imeInset);
        }
        if (playlistsFragment != null && playlistsFragment.isAdded()) {
            playlistsFragment.updateWindowInsets(topInset, bottomInset);
        }
        if (settingsFragment != null && settingsFragment.isAdded()) {
            settingsFragment.updateWindowInsets(topInset, bottomInset);
        }
    }

    public void dispatchWindowInsetsToFragments(int topInset, int bottomInset) {
        dispatchWindowInsetsToFragments(topInset, bottomInset, cachedImeInset);
    }

    private void initViews() {
        tabExplorer = findViewById(R.id.tabExplorer);
        tabPlaylists = findViewById(R.id.tabPlaylists);
        tabSettings = findViewById(R.id.tabSettings);

        tvTabExplorerText = findViewById(R.id.tvTabExplorerText);
        tvTabPlaylistsText = findViewById(R.id.tvTabPlaylistsText);
        tvTabSettingsText = findViewById(R.id.tvTabSettingsText);

        ivTabExplorerIcon = findViewById(R.id.ivTabExplorerIcon);
        ivTabPlaylistsIcon = findViewById(R.id.ivTabPlaylistsIcon);
        ivTabSettingsIcon = findViewById(R.id.ivTabSettingsIcon);

        vTabIndicator = findViewById(R.id.vTabIndicator);
    }

    private static final String STATE_CURRENT_TAB = "state_current_tab";

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_TAB, currentTabIndex);
    }

    private void initFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            explorerFragment = new ExplorerFragment();
            playlistsFragment = new PlaylistsFragment();
            settingsFragment = new SettingsFragment();

            fm.beginTransaction()
                    .add(R.id.fragmentContainer, settingsFragment, "SETTINGS").hide(settingsFragment)
                    .add(R.id.fragmentContainer, playlistsFragment, "PLAYLISTS").hide(playlistsFragment)
                    .add(R.id.fragmentContainer, explorerFragment, "EXPLORER")
                    .commit();

            activeFragment = explorerFragment;
            currentTabIndex = 0;
        } else {
            explorerFragment = (ExplorerFragment) fm.findFragmentByTag("EXPLORER");
            playlistsFragment = (PlaylistsFragment) fm.findFragmentByTag("PLAYLISTS");
            settingsFragment = (SettingsFragment) fm.findFragmentByTag("SETTINGS");

            if (explorerFragment == null) explorerFragment = new ExplorerFragment();
            if (playlistsFragment == null) playlistsFragment = new PlaylistsFragment();
            if (settingsFragment == null) settingsFragment = new SettingsFragment();

            currentTabIndex = savedInstanceState.getInt(STATE_CURRENT_TAB, 0);

            androidx.fragment.app.FragmentTransaction ft = fm.beginTransaction();
            if (explorerFragment.isAdded()) ft.hide(explorerFragment);
            if (playlistsFragment.isAdded()) ft.hide(playlistsFragment);
            if (settingsFragment.isAdded()) ft.hide(settingsFragment);

            switch (currentTabIndex) {
                case 1:
                    activeFragment = playlistsFragment;
                    ft.show(playlistsFragment);
                    break;
                case 2:
                    activeFragment = settingsFragment;
                    ft.show(settingsFragment);
                    break;
                case 0:
                default:
                    activeFragment = explorerFragment;
                    ft.show(explorerFragment);
                    break;
            }
            ft.commit();
        }

        updateTabSelection(currentTabIndex, false);
    }

    private void setupTabListeners() {
        if (tabExplorer != null) {
            tabExplorer.setOnClickListener(v -> selectTab(0));
        }
        if (tabPlaylists != null) {
            tabPlaylists.setOnClickListener(v -> selectTab(1));
        }
        if (tabSettings != null) {
            tabSettings.setOnClickListener(v -> selectTab(2));
        }
    }

    public void selectTab(int index) {
        if (currentTabIndex == index && activeFragment != null) return;

        FragmentManager fm = getSupportFragmentManager();
        Fragment targetFragment = null;

        switch (index) {
            case 0:
                targetFragment = explorerFragment;
                break;
            case 1:
                targetFragment = playlistsFragment;
                break;
            case 2:
                targetFragment = settingsFragment;
                break;
        }

        if (targetFragment != null) {
            fm.beginTransaction()
                    .setCustomAnimations(R.anim.fragment_scale_fade_in, R.anim.fragment_scale_fade_out)
                    .hide(activeFragment)
                    .show(targetFragment)
                    .commit();
            activeFragment = targetFragment;
            currentTabIndex = index;
            if (index == 1 && playlistsFragment != null) {
                playlistsFragment.loadAndDisplayPlaylists();
            }
            updateTabSelection(index, true);
            dispatchWindowInsetsToFragments(cachedTopInset, cachedBottomInset);
        }
    }

    public void notifyPlaylistsChanged() {
        if (playlistsFragment != null && playlistsFragment.isAdded()) {
            playlistsFragment.loadAndDisplayPlaylists();
        }
    }

    private void updateTabSelection(int targetIndex, boolean animate) {
        currentTabIndex = targetIndex;
        View[] tabs = {tabExplorer, tabPlaylists, tabSettings};
        TextView[] texts = {tvTabExplorerText, tvTabPlaylistsText, tvTabSettingsText};
        ImageView[] icons = {ivTabExplorerIcon, ivTabPlaylistsIcon, ivTabSettingsIcon};

        if (animate && tabs[0] != null && tabs[0].getParent() instanceof ViewGroup) {
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) tabs[0].getParent(), new android.transition.AutoTransition().setDuration(130));
        }

        for (int i = 0; i < 3; i++) {
            if (tabs[i] != null && tabs[i].getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tabs[i].getLayoutParams();
                lp.weight = (i == targetIndex) ? 1.5f : 0.75f;
                tabs[i].setLayoutParams(lp);
            }
            if (i == targetIndex) {
                if (texts[i] != null) {
                    texts[i].setVisibility(View.VISIBLE);
                    texts[i].setTextColor(ContextCompat.getColor(this, R.color.white_color));
                }
                if (icons[i] != null) {
                    icons[i].setColorFilter(ContextCompat.getColor(this, R.color.white_color));
                }
            } else {
                if (texts[i] != null) {
                    texts[i].setVisibility(View.GONE);
                }
                if (icons[i] != null) {
                    icons[i].setColorFilter(ContextCompat.getColor(this, R.color.player_item_inactive_text));
                }
            }
        }

        if (vTabIndicator != null) {
            View containerView = (View) vTabIndicator.getParent();
            containerView.post(() -> {
                if (vTabIndicator == null || containerView == null) return;
                int containerWidth = containerView.getWidth() - containerView.getPaddingLeft() - containerView.getPaddingRight();
                int paddingLeft = containerView.getPaddingLeft();

                int targetWidth = containerWidth / 2;
                int targetLeft;

                if (targetIndex == 0) {
                    targetLeft = 0;
                } else if (targetIndex == 1) {
                    targetLeft = containerWidth / 4;
                } else {
                    targetLeft = containerWidth / 2;
                }

                ViewGroup.LayoutParams lp = vTabIndicator.getLayoutParams();
                int startWidth = vTabIndicator.getWidth();
                float startTranslationX = vTabIndicator.getTranslationX();

                if (startWidth <= 0) {
                    startWidth = targetWidth;
                    startTranslationX = targetLeft;
                }

                if (animate) {
                    android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
                    animator.setDuration(130);
                    animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    int finalStartWidth = startWidth;
                    float finalStartTranslationX = startTranslationX;
                    animator.addUpdateListener(animation -> {
                        float fraction = (float) animation.getAnimatedValue();
                        int currentWidth = (int) (finalStartWidth + (targetWidth - finalStartWidth) * fraction);
                        float currentTranslationX = finalStartTranslationX + (targetLeft - finalStartTranslationX) * fraction;

                        lp.width = currentWidth;
                        vTabIndicator.setLayoutParams(lp);
                        vTabIndicator.requestLayout();
                        vTabIndicator.setTranslationX(currentTranslationX);
                    });
                    animator.start();
                } else {
                    vTabIndicator.animate().cancel();
                    lp.width = targetWidth;
                    vTabIndicator.setLayoutParams(lp);
                    vTabIndicator.requestLayout();
                    vTabIndicator.setTranslationX(targetLeft);
                }
            });
        }
    }
}
