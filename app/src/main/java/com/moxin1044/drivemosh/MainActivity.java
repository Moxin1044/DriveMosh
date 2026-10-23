package com.moxin1044.drivemosh;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.imageview.ShapeableImageView;
import com.moxin1044.drivemosh.ui.ClockView;
import com.moxin1044.drivemosh.ui.DiscRingView;
import com.moxin1044.drivemosh.ui.EqualizerView;
import com.moxin1044.drivemosh.ui.GForceView;
import com.moxin1044.drivemosh.ui.SpeedView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements GForceView.Listener {

    private static final long TICK_MS = 500L;
    private static final int SESSION_POLL_TICKS = 4;
    private static final float STANDARD_GRAVITY = 9.80665f;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private GForceView gForceView;
    private TextView gValue;
    private TextView gAxis;
    private TextView gPeak;
    private ClockView clockView;
    private TextView liveDot;
    private SpeedView speedView;

    private ShapeableImageView albumArt;
    private TextView trackTitle;
    private TextView trackArtist;
    private TextView timePosition;
    private TextView timeDuration;
    private ProgressBar progressBar;
    private ImageButton btnPrevious;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private TextView mediaHint;
    private DiscRingView discRingView;
    private EqualizerView equalizerView;

    private SensorManager sensorManager;
    private Sensor motionSensor;
    private boolean useLinearAcceleration;
    private final float[] gravityEstimate = new float[3];
    private boolean gravitySeeded;

    private MediaSessionManager sessionManager;
    private AudioManager audioManager;
    private NotificationManager notificationManager;
    private ComponentName listenerComponent;
    private MediaController mediaController;
    private MediaController.Callback mediaCallback;
    private PlaybackState playbackState;
    private MediaMetadata metadata;
    private String sourceName = "";
    private boolean isPlaying;
    private boolean fallbackPlaying;
    private boolean showingEmpty = true;

    private float peak;
    private long lastValueUpdate;

    private LocationManager locationManager;
    private long lastLocationUpdate;
    private boolean locationUpdatesRegistered;
    private boolean locationPermissionAsked;

    private int basePadding;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startLocationUpdates();
                }
                updateSpeedState();
            });

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            applySpeedLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
            startLocationUpdates();
        }

        @Override
        public void onProviderDisabled(String provider) {
            updateSpeedState();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (lastLocationUpdate > 0
                    && SystemClock.elapsedRealtime() - lastLocationUpdate > 4000L) {
                lastLocationUpdate = 0;
                speedView.setFix(false);
                speedView.setSpeed(0f);
            }
            if (++tickCount % SESSION_POLL_TICKS == 0) {
                refreshSessions();
                startLocationUpdates();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private int tickCount;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x;
            float y;
            if (useLinearAcceleration) {
                x = event.values[0];
                y = event.values[1];
            } else {
                if (!gravitySeeded) {
                    gravityEstimate[0] = event.values[0];
                    gravityEstimate[1] = event.values[1];
                    gravitySeeded = true;
                }
                final float alpha = 0.82f;
                gravityEstimate[0] = alpha * gravityEstimate[0] + (1 - alpha) * event.values[0];
                gravityEstimate[1] = alpha * gravityEstimate[1] + (1 - alpha) * event.values[1];
                x = event.values[0] - gravityEstimate[0];
                y = event.values[1] - gravityEstimate[1];
            }

            float screenX;
            float screenY;
            int rotation = Surface.ROTATION_0;
            if (getDisplay() != null) {
                rotation = getDisplay().getRotation();
            }
            switch (rotation) {
                case Surface.ROTATION_90:
                    screenX = -y;
                    screenY = x;
                    break;
                case Surface.ROTATION_180:
                    screenX = -x;
                    screenY = -y;
                    break;
                case Surface.ROTATION_270:
                    screenX = y;
                    screenY = -x;
                    break;
                default:
                    screenX = x;
                    screenY = y;
                    break;
            }

            gForceView.setAcceleration(screenX / STANDARD_GRAVITY, screenY / STANDARD_GRAVITY);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        basePadding = dp(18);
        setupWindow();
        bindViews();
        setupMedia();
        setupInteractions();
        setupPulseAnimations();
        updateSpeedState();

        showEmptyState();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            View content = findViewById(R.id.content);
            content.setPadding(
                    basePadding + Math.max(bars.left, cutout.left),
                    basePadding + Math.max(bars.top, cutout.top),
                    basePadding + Math.max(bars.right, cutout.right),
                    basePadding + Math.max(bars.bottom, cutout.bottom));
            return insets;
        });
    }

    private void setupWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(params);
        hideSystemBars();
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void bindViews() {
        gForceView = findViewById(R.id.gForceView);
        gValue = findViewById(R.id.gValue);
        gAxis = findViewById(R.id.gAxis);
        gPeak = findViewById(R.id.gPeak);
        clockView = findViewById(R.id.clockView);
        liveDot = findViewById(R.id.liveDot);
        speedView = findViewById(R.id.speedView);

        albumArt = findViewById(R.id.albumArt);
        trackTitle = findViewById(R.id.trackTitle);
        trackArtist = findViewById(R.id.trackArtist);
        timePosition = findViewById(R.id.timePosition);
        timeDuration = findViewById(R.id.timeDuration);
        progressBar = findViewById(R.id.progressBar);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        mediaHint = findViewById(R.id.mediaHint);
        discRingView = findViewById(R.id.discRingView);
        equalizerView = findViewById(R.id.equalizerView);

        gForceView.setListener(this);
        trackTitle.setSelected(true);

        sensorManager = getSystemService(SensorManager.class);
        Sensor linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (linear != null) {
            useLinearAcceleration = true;
            motionSensor = linear;
        } else {
            useLinearAcceleration = false;
            motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        locationManager = getSystemService(LocationManager.class);
    }

    private void setupMedia() {
        sessionManager = getSystemService(MediaSessionManager.class);
        audioManager = getSystemService(AudioManager.class);
        notificationManager = getSystemService(NotificationManager.class);
        listenerComponent = new ComponentName(this, MediaNotificationListener.class);

        mediaCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                playbackState = state;
                applyPlaybackState();
                updateProgress();
            }

            @Override
            public void onMetadataChanged(MediaMetadata changedMetadata) {
                metadata = changedMetadata;
                renderMetadata();
                updateProgress();
                updateHint();
            }

            @Override
            public void onSessionDestroyed() {
                detachController();
                showEmptyState();
            }
        };
    }

    private void setupInteractions() {
        gForceView.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            gForceView.resetPeak();
            peak = 0f;
            gPeak.setText(getString(R.string.label_peak));
        });

        btnPrevious.setOnClickListener(view -> {
            tap(view);
            skipToPrevious();
        });
        btnPlayPause.setOnClickListener(view -> {
            tap(view);
            togglePlayPause();
        });
        btnNext.setOnClickListener(view -> {
            tap(view);
            skipToNext();
        });

        speedView.setOnClickListener(view -> handleSpeedClick());

        mediaHint.setOnClickListener(view -> {
            if (!hasNotificationAccess()) {
                openNotificationListenerSettings();
            }
        });
    }

    private void setupPulseAnimations() {
        android.animation.ObjectAnimator livePulse =
                android.animation.ObjectAnimator.ofFloat(liveDot, View.ALPHA, 1f, 0.15f);
        livePulse.setDuration(1200);
        livePulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        livePulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        livePulse.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (motionSensor != null) {
            sensorManager.registerListener(sensorEventListener, motionSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        updateHint();
        refreshSessions();
        updateSpeedState();
        startLocationUpdates();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
        stopLocationUpdates();
        lastLocationUpdate = 0;
        speedView.setFix(false);
        speedView.setSpeed(0f);
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        detachController();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onValues(float magnitude, float x, float y) {
        if (magnitude > peak) {
            peak = magnitude;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastValueUpdate < 90) {
            return;
        }
        lastValueUpdate = now;
        gValue.setText(String.format(Locale.US, "%.2f", magnitude));
        gAxis.setText(String.format(Locale.US, "X %+.2f   Y %+.2f", x, y));
        gPeak.setText(String.format(Locale.US, "峰值 %.2f G", peak));
    }

    private void refreshSessions() {
        boolean access = hasNotificationAccess();
        if (!access) {
            detachController();
            if (!showingEmpty) {
                showEmptyState();
            }
            updateHint();
            return;
        }

        List<MediaController> sessions;
        try {
            sessions = sessionManager.getActiveSessions(listenerComponent);
        } catch (SecurityException | IllegalStateException e) {
            return;
        }

        MediaController chosen = null;
        for (MediaController candidate : sessions) {
            PlaybackState state = candidate.getPlaybackState();
            int playState = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (playState == PlaybackState.STATE_PLAYING
                    || playState == PlaybackState.STATE_BUFFERING
                    || playState == PlaybackState.STATE_CONNECTING) {
                chosen = candidate;
                break;
            }
        }

        if (chosen == null && mediaController != null) {
            for (MediaController candidate : sessions) {
                if (candidate.getSessionToken().equals(mediaController.getSessionToken())) {
                    chosen = candidate;
                    break;
                }
            }
        }
        if (chosen == null && !sessions.isEmpty()) {
            chosen = sessions.get(0);
        }

        if (chosen == null) {
            detachController();
            if (!showingEmpty) {
                showEmptyState();
            }
            updateHint();
            return;
        }

        boolean same = mediaController != null
                && mediaController.getSessionToken().equals(chosen.getSessionToken());
        if (!same) {
            attachController(chosen);
        } else {
            MediaMetadata changed = chosen.getMetadata();
            if (changed != metadata) {
                metadata = changed;
                renderMetadata();
            }
            playbackState = chosen.getPlaybackState();
            applyPlaybackState();
            updateProgress();
        }
        updateHint();
    }

    private void attachController(MediaController controller) {
        detachController();
        mediaController = controller;
        sourceName = resolveSourceName(controller.getPackageName());
        mediaController.registerCallback(mediaCallback, handler);
        playbackState = controller.getPlaybackState();
        metadata = controller.getMetadata();
        fallbackPlaying = playbackState != null
                && playbackState.getState() == PlaybackState.STATE_PLAYING;
        renderMetadata();
        applyPlaybackState();
        updateProgress();
        updateHint();
    }

    private void detachController() {
        if (mediaController != null) {
            try {
                mediaController.unregisterCallback(mediaCallback);
            } catch (Exception ignored) {
            }
            mediaController = null;
        }
        playbackState = null;
    }

    private void applyPlaybackState() {
        boolean playing;
        if (mediaController == null) {
            playing = fallbackPlaying;
        } else {
            playing = playbackState != null
                    && playbackState.getState() == PlaybackState.STATE_PLAYING;
        }
        setPlayStateUi(playing);
    }

    private void setPlayStateUi(boolean playing) {
        isPlaying = playing;
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlayPause.setContentDescription(getString(playing ? R.string.cd_pause : R.string.cd_play));
        discRingView.setPlaying(playing);
        equalizerView.setPlaying(playing);
    }

    private void renderMetadata() {
        showingEmpty = false;
        if (metadata == null) {
            trackTitle.setText(R.string.unknown_track);
            trackArtist.setText(sourceName.isEmpty()
                    ? getString(R.string.unknown_artist) : sourceName);
            showPlaceholderArt();
            return;
        }

        String title = metaText(metadata, MediaMetadata.METADATA_KEY_TITLE);
        if (title.isEmpty()) {
            title = metaText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        if (title.isEmpty()) {
            title = getString(R.string.unknown_track);
        }

        String artist = metaText(metadata, MediaMetadata.METADATA_KEY_ARTIST);
        if (artist.isEmpty()) {
            artist = metaText(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        if (artist.isEmpty()) {
            artist = metaText(metadata, MediaMetadata.METADATA_KEY_ALBUM);
        }
        if (artist.isEmpty()) {
            artist = sourceName.isEmpty() ? getString(R.string.unknown_artist) : sourceName;
        } else if (!sourceName.isEmpty()) {
            artist = artist + "  ·  " + sourceName;
        }

        trackTitle.setText(title);
        trackArtist.setText(artist);
        trackTitle.setSelected(true);

        Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (artwork == null) {
            artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (artwork != null) {
            albumArt.setBackground(null);
            albumArt.setPadding(0, 0, 0, 0);
            albumArt.setImageTintList(null);
            albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            albumArt.setImageBitmap(artwork);
        } else {
            showPlaceholderArt();
        }
    }

    private void showPlaceholderArt() {
        int padding = dp(19);
        albumArt.setImageDrawable(null);
        albumArt.setImageTintList(null);
        albumArt.setBackgroundResource(R.drawable.bg_disc_placeholder);
        albumArt.setPadding(padding, padding, padding, padding);
        albumArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        albumArt.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.neon_cyan)));
        albumArt.setImageResource(R.drawable.ic_music_note);
    }

    private void showEmptyState() {
        detachController();
        metadata = null;
        playbackState = null;
        showingEmpty = true;
        fallbackPlaying = false;

        trackTitle.setText(R.string.no_track);
        trackArtist.setText(hasNotificationAccess()
                ? R.string.no_track_sub : R.string.no_access_sub);
        trackTitle.setSelected(true);
        showPlaceholderArt();

        timePosition.setText("0:00");
        timeDuration.setText("--:--");
        progressBar.setProgress(0);
        setPlayStateUi(false);
        updateHint();
    }

    private void updateProgress() {
        if (mediaController == null || playbackState == null) {
            timePosition.setText("0:00");
            timeDuration.setText("--:--");
            progressBar.setProgress(0);
            return;
        }

        long duration = metadata == null ? 0L
                : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = playbackState.getPosition();
        if (playbackState.getState() == PlaybackState.STATE_PLAYING) {
            long elapsed = SystemClock.elapsedRealtime()
                    - playbackState.getLastPositionUpdateTime();
            position += (long) (elapsed * Math.max(0f, playbackState.getPlaybackSpeed()));
        }

        if (duration > 0) {
            position = Math.max(0L, Math.min(position, duration));
            progressBar.setProgress((int) (position * 1000L / duration));
            timeDuration.setText(formatTime(duration));
        } else {
            progressBar.setProgress(0);
            timeDuration.setText("--:--");
        }
        timePosition.setText(formatTime(position));
    }

    private void skipToPrevious() {
        if (mediaController != null) {
            try {
                mediaController.getTransportControls().skipToPrevious();
            } catch (Exception ignored) {
            }
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
        scheduleRefresh();
    }

    private void skipToNext() {
        if (mediaController != null) {
            try {
                mediaController.getTransportControls().skipToNext();
            } catch (Exception ignored) {
            }
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        }
        scheduleRefresh();
    }

    private void togglePlayPause() {
        if (mediaController != null) {
            boolean playing = playbackState != null
                    && playbackState.getState() == PlaybackState.STATE_PLAYING;
            try {
                if (playing) {
                    mediaController.getTransportControls().pause();
                } else {
                    mediaController.getTransportControls().play();
                }
                setPlayStateUi(!playing);
            } catch (Exception ignored) {
            }
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            fallbackPlaying = !fallbackPlaying;
            setPlayStateUi(fallbackPlaying);
        }
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        handler.postDelayed(this::refreshSessions, 650);
    }

    private void dispatchMediaKey(int keyCode) {
        try {
            long now = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now,
                    KeyEvent.ACTION_DOWN, keyCode, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now,
                    KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Exception ignored) {
        }
    }

    private void updateHint() {
        boolean access = hasNotificationAccess();
        if (!access) {
            mediaHint.setVisibility(View.VISIBLE);
            mediaHint.setText(R.string.enable_media_access);
            mediaHint.setClickable(true);
            mediaHint.setFocusable(true);
        } else {
            mediaHint.setVisibility(View.GONE);
        }
        if (showingEmpty) {
            trackArtist.setText(access ? R.string.no_track_sub : R.string.no_access_sub);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationUpdates() {
        if (locationManager == null || !hasLocationPermission()) {
            updateSpeedState();
            return;
        }
        boolean enabled;
        try {
            enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            enabled = false;
        }
        if (!enabled) {
            updateSpeedState();
            return;
        }
        if (!locationUpdatesRegistered) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, locationListener, Looper.getMainLooper());
                locationUpdatesRegistered = true;
            } catch (SecurityException | IllegalArgumentException e) {
                return;
            }
        }
        try {
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocationUpdate == 0 && last != null
                    && SystemClock.elapsedRealtimeNanos() - last.getElapsedRealtimeNanos()
                    < 5_000_000_000L) {
                applySpeedLocation(last);
            }
        } catch (SecurityException ignored) {
        }
        updateSpeedState();
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationUpdatesRegistered) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
            locationUpdatesRegistered = false;
        }
    }

    private void applySpeedLocation(Location location) {
        float speedKmh = 0f;
        if (location.hasSpeed()) {
            speedKmh = location.getSpeed() * 3.6f;
        }
        if (Float.isNaN(speedKmh) || Float.isInfinite(speedKmh)
                || speedKmh < 0f || speedKmh > 300f) {
            speedKmh = 0f;
        }
        lastLocationUpdate = SystemClock.elapsedRealtime();
        speedView.setFix(true);
        speedView.setSpeed(speedKmh);
    }

    private void updateSpeedState() {
        boolean granted = hasLocationPermission();
        boolean enabled = false;
        if (granted) {
            try {
                enabled = locationManager != null
                        && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception e) {
                enabled = false;
            }
        }
        speedView.setPermissionGranted(granted);
        speedView.setGpsEnabled(enabled);
        if (!granted || !enabled) {
            lastLocationUpdate = 0;
            speedView.setFix(false);
            speedView.setSpeed(0f);
        }
    }

    private void handleSpeedClick() {
        if (!hasLocationPermission()) {
            if (locationPermissionAsked && !shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                openAppSettings();
                return;
            }
            locationPermissionAsked = true;
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        boolean enabled;
        try {
            enabled = locationManager != null
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            enabled = false;
        }
        if (!enabled) {
            try {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private boolean hasNotificationAccess() {
        try {
            return notificationManager.isNotificationListenerAccessGranted(listenerComponent);
        } catch (Exception e) {
            return false;
        }
    }

    private void openNotificationListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            intent.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    listenerComponent.flattenToString());
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private String resolveSourceName(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(info);
            return label == null ? "" : label.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String metaText(MediaMetadata metadata, String key) {
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String formatTime(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long seconds = totalSeconds % 60L;
        long minutes = (totalSeconds / 60L) % 60L;
        long hours = totalSeconds / 3600L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void tap(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(140).start())
                .start();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
