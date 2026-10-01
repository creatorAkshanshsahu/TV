package com.livetgtv.nativeapp;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends Activity {

    private final String BASE = "https://livetgtv.lovable.app";

    private LinearLayout root;
    private LinearLayout content;
    private TextView title;
    private Button v1Button;
    private Button v2Button;

    private ExoPlayer player;
    private PlayerView playerView;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean v1Mode = true;

    private final List<Channel> channels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        showHome();
        loadChannels();
    }

    private void showHome() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 10, 14));

        // TOP BAR
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(32, 20, 32, 20);

        title = new TextView(this);
        title.setText("LiveTgTV");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(title, new LinearLayout.LayoutParams(
                0,
                80,
                1
        ));

        v1Button = createTopButton("V1");
        v2Button = createTopButton("V2");

        top.addView(v1Button);
        top.addView(v2Button);

        v1Button.setOnClickListener(v -> {
            v1Mode = true;
            updateModeButtons();
            loadChannels();
        });

        v2Button.setOnClickListener(v -> {
            v1Mode = false;
            updateModeButtons();
            loadChannels();
        });

        root.addView(top);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(30, 10, 30, 30);

        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        updateModeButtons();
    }

    private Button createTopButton(String text) {

        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        b.setFocusableInTouchMode(false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 70);
        lp.setMargins(10, 0, 10, 0);
        b.setLayoutParams(lp);

        return b;
    }

    private void updateModeButtons() {

        if (v1Mode) {
            v1Button.setText("● V1");
            v2Button.setText("V2");
        } else {
            v1Button.setText("V1");
            v2Button.setText("● V2");
        }
    }

    private void loadChannels() {

        content.removeAllViews();

        ProgressBar progress = new ProgressBar(this);
        content.addView(progress);

        executor.execute(() -> {

            try {

                String endpoint;

                if (v1Mode) {
                    endpoint = BASE + "/api/public/channels";
                } else {
                    endpoint = BASE + "/api/public/v2/channels";
                }

                String json = get(endpoint);

                JSONObject obj = new JSONObject(json);

                JSONArray arr = obj.optJSONArray("channels");

                channels.clear();

                if (arr != null) {

                    for (int i = 0; i < arr.length(); i++) {

                        JSONObject c = arr.getJSONObject(i);

                        Channel channel = new Channel();

                        channel.id = c.optString("id");
                        channel.name = c.optString("name");
                        channel.category = c.optString("category");
                        channel.logo = c.optString("logo");

                        channels.add(channel);
                    }
                }

                runOnUiThread(this::displayChannels);

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Failed to load channels: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        });
    }

    private void displayChannels() {

        content.removeAllViews();

        TextView heading = new TextView(this);

        heading.setText(
                (v1Mode ? "V1 • " : "V2 • ") +
                channels.size() +
                " Channels"
        );

        heading.setTextColor(Color.WHITE);
        heading.setTextSize(22);
        heading.setPadding(10, 10, 10, 20);

        content.addView(heading);

        GridLayout grid = new GridLayout(this);

        grid.setColumnCount(5);
        grid.setRowCount(GridLayout.UNDEFINED);

        grid.setUseDefaultMargins(true);

        content.addView(
                grid,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        for (Channel channel : channels) {

            Button button = new Button(this);

            button.setText(channel.name);
            button.setTextColor(Color.WHITE);
            button.setTextSize(15);
            button.setGravity(Gravity.CENTER);
            button.setFocusable(true);
            button.setFocusableInTouchMode(false);

            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(28, 28, 35));
            background.setCornerRadius(18);

            button.setBackground(background);

            button.setOnFocusChangeListener((v, hasFocus) -> {

                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(18);

                if (hasFocus) {
                    bg.setColor(Color.rgb(55, 55, 65));
                    button.setScaleX(1.04f);
                    button.setScaleY(1.04f);
                } else {
                    bg.setColor(Color.rgb(28, 28, 35));
                    button.setScaleX(1f);
                    button.setScaleY(1f);
                }

                button.setBackground(bg);
            });

            button.setOnClickListener(v -> playChannel(channel));

            GridLayout.LayoutParams lp =
                    new GridLayout.LayoutParams();

            lp.width = 0;
            lp.height = 110;

            lp.columnSpec =
                    GridLayout.spec(
                            GridLayout.UNDEFINED,
                            1f
                    );

            lp.setMargins(8, 8, 8, 8);

            grid.addView(button, lp);
        }

        if (!channels.isEmpty()) {
            grid.getChildAt(0).requestFocus();
        }
    }

    private void playChannel(Channel channel) {

        Toast.makeText(
                this,
                "Loading " + channel.name,
                Toast.LENGTH_SHORT
        ).show();

        executor.execute(() -> {

            try {

                String endpoint;

                if (v1Mode) {
                    endpoint =
                            BASE +
                            "/api/public/channels/" +
                            channel.id;
                } else {
                    endpoint =
                            BASE +
                            "/api/public/v2/channels/" +
                            channel.id;
                }

                String json = get(endpoint);

                JSONObject obj = new JSONObject(json);

                if (v1Mode) {

                    JSONArray sources =
                            obj.optJSONArray("sources");

                    if (sources == null ||
                            sources.length() == 0) {

                        throw new Exception(
                                "No V1 stream source"
                        );
                    }

                    String mpd =
                            sources.getString(0);

                    JSONObject drm =
                            obj.optJSONObject("drm");

                    String keyId = null;
                    String key = null;

                    if (drm != null) {

                        keyId =
                                drm.optString(
                                        "keyId",
                                        null
                                );

                        key =
                                drm.optString(
                                        "key",
                                        null
                                );
                    }

                    String finalKeyId = keyId;
                    String finalKey = key;

                    runOnUiThread(() ->
                            playV1(
                                    mpd,
                                    finalKeyId,
                                    finalKey
                            )
                    );

                } else {

                    JSONArray servers =
                            obj.optJSONArray("servers");

                    if (servers == null ||
                            servers.length() == 0) {

                        throw new Exception(
                                "No V2 servers"
                        );
                    }

                    String playable = null;

                    for (int i = 0;
                         i < servers.length();
                         i++) {

                        JSONObject server =
                                servers.getJSONObject(i);

                        String url =
                                server.optString(
                                        "playable",
                                        null
                                );

                        if (url != null &&
                                !url.isEmpty()) {

                            playable = url;
                            break;
                        }
                    }

                    if (playable == null) {
                        throw new Exception(
                                "No playable V2 server"
                        );
                    }

                    String finalPlayable = playable;

                    runOnUiThread(() ->
                            playV2(finalPlayable)
                    );
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Channel error: " +
                                e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        });
    }

    private void playV1(
            String mpd,
            String keyId,
            String key
    ) {

        releasePlayer();

        playerView = new PlayerView(this);

        playerView.setUseController(true);
        playerView.setKeepScreenOn(true);

        setContentView(playerView);

        ExoPlayer.Builder builder =
                new ExoPlayer.Builder(this);

        if (keyId != null &&
                key != null &&
                !keyId.isEmpty() &&
                !key.isEmpty()) {

            try {

                String drmJson =
                        "{\"keys\":[{\"kty\":\"oct\",\"k\":\"" +
                        key +
                        "\",\"kid\":\"" +
                        keyId +
                        "\"}],\"type\":\"temporary\"}";

                LocalMediaDrmCallback callback =
                        new LocalMediaDrmCallback(
                                drmJson.getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8
                                )
                        );

                DefaultDrmSessionManager drmManager =
                        new DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(
                                        C.CLEARKEY_UUID,
                                        FrameworkMediaDrm.DEFAULT_PROVIDER
                                )
                                .build(callback);

                DrmSessionManagerProvider provider =
                        mediaItem ->
                                drmManager;

                DashMediaSource.Factory dashFactory =
                        new DashMediaSource.Factory(
                                new DefaultHttpDataSource.Factory()
                        );

                dashFactory.setDrmSessionManagerProvider(
                        provider
                );

                MediaItem mediaItem =
                        new MediaItem.Builder()
                                .setUri(mpd)
                                .setMimeType(
                                        "application/dash+xml"
                                )
                                .build();

                DashMediaSource source =
                        dashFactory.createMediaSource(
                                mediaItem
                        );

                player =
                        builder.build();

                player.setMediaSource(source);

                addPlayerListener();

                player.prepare();
                player.play();

                return;

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "V1 DRM error: " +
                        e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        }

        MediaItem item =
                new MediaItem.Builder()
                        .setUri(mpd)
                        .setMimeType(
                                "application/dash+xml"
                        )
                        .build();

        player = builder.build();

        player.setMediaItem(item);

        addPlayerListener();

        player.prepare();
        player.play();
    }

    private void playV2(String url) {

        releasePlayer();

        playerView = new PlayerView(this);

        playerView.setUseController(true);
        playerView.setKeepScreenOn(true);

        setContentView(playerView);

        player =
                new ExoPlayer.Builder(this)
                        .build();

        MediaItem item =
                new MediaItem.Builder()
                        .setUri(url)
                        .setMimeType(
                                "application/x-mpegURL"
                        )
                        .build();

        player.setMediaItem(item);

        addPlayerListener();

        player.prepare();
        player.play();
    }

    private void addPlayerListener() {

        player.addListener(
                new androidx.media3.common.Player.Listener() {

                    @Override
                    public void onPlayerError(
                            PlaybackException e
                    ) {

                        Toast.makeText(
                                MainActivity.this,
                                "Playback error: " +
                                e.getErrorCodeName(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private String get(String urlString)
            throws Exception {

        URL url = new URL(urlString);

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);

        connection.setRequestProperty(
                "User-Agent",
                "LiveTgTV-Native-Android"
        );

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream()
                        )
                );

        StringBuilder result =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();
        connection.disconnect();

        return result.toString();
    }

    private void releasePlayer() {

        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (player != null) {

            releasePlayer();

            showHome();
            loadChannels();

        } else {

            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        releasePlayer();

        executor.shutdownNow();

        super.onDestroy();
    }

    private static class Channel {

        String id;
        String name;
        String category;
        String logo;
    }
}
