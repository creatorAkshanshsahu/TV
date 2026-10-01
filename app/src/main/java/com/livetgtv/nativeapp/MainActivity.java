package com.livetgtv.nativeapp;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.*;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.ExoMediaDrm;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String API_V1 = "https://livetgtv.lovable.app/api/public/channels";
    private static final String API_V2 = "https://livetgtv.lovable.app/api/public/v2/channels";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root, content, tabs;
    private TextView status;
    private boolean v1 = true;
    private ArrayList<Channel> channels = new ArrayList<>();
    private ExoPlayer player;
    private PlayerView playerView;
    private int lastFocus = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(1024, 1024);
        showHome();
        loadChannels();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackgroundResource(com.livetgtv.nativeapp.R.drawable.bg); b.setFocusable(true); b.setPadding(dp(12),0,dp(12),0); return b;
    }

    private void showHome() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(8,8,8));
        root.setPadding(dp(28),dp(18),dp(28),dp(18)); setContentView(root);

        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("LiveTgTV", 26, Color.WHITE); logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(logo, new LinearLayout.LayoutParams(0,dp(55),1));
        status = text("Loading channels…", 13, Color.LTGRAY); header.addView(status, new LinearLayout.LayoutParams(dp(240),dp(55)));
        root.addView(header);

        tabs = new LinearLayout(this); tabs.setGravity(Gravity.CENTER_VERTICAL); tabs.setPadding(0,0,0,dp(10));
        Button b1 = button("V1 • DASH"); Button b2 = button("V2 • HLS"); Button refresh = button("↻ Refresh");
        tabs.addView(b1,new LinearLayout.LayoutParams(dp(145),dp(48))); tabs.addView(b2,new LinearLayout.LayoutParams(dp(145),dp(48)));
        Space sp = new Space(this); tabs.addView(sp,new LinearLayout.LayoutParams(0,1,1)); tabs.addView(refresh,new LinearLayout.LayoutParams(dp(125),dp(48)));
        root.addView(tabs);
        b1.setOnClickListener(v->{v1=true; loadChannels();}); b2.setOnClickListener(v->{v1=false; loadChannels();}); refresh.setOnClickListener(v->loadChannels());
        b1.setNextFocusRightId(b2.getId()); b2.setNextFocusLeftId(b1.getId()); b1.requestFocus();

        ScrollView sv = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); sv.addView(content); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void loadChannels() {
        status.setText((v1?"V1":"V2")+" • Loading…");
        executor.execute(() -> {
            try {
                String json = http(v1?API_V1:API_V2); ArrayList<Channel> list = parseList(json,v1);
                main.post(() -> { channels=list; renderGrid(); status.setText((v1?"V1":"V2")+" • "+list.size()+" channels"); });
            } catch(Exception e) { main.post(() -> status.setText("Could not load channels")); }
        });
    }

    private void renderGrid() {
        content.removeAllViews();
        int cols = getResources().getDisplayMetrics().widthPixels >= dp(1000) ? 6 : 4;
        for(int i=0;i<channels.size();i+=cols){
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            for(int j=0;j<cols && i+j<channels.size();j++){
                Channel c=channels.get(i+j); Button b=button(c.name); b.setTextSize(12); b.setMinHeight(dp(72)); b.setMaxLines(2);
                final int index=i+j; b.setOnClickListener(v->playChannel(channels.get(index)));
                row.addView(b,new LinearLayout.LayoutParams(0,dp(78),1));
                if(j<cols-1) { Space x=new Space(this); row.addView(x,new LinearLayout.LayoutParams(dp(8),1)); }
            }
            content.addView(row,new LinearLayout.LayoutParams(-1,dp(86)));
        }
        if(channels.size()>0) content.getChildAt(0).requestFocus();
    }

    private ArrayList<Channel> parseList(String s, boolean isV1) throws Exception {
        ArrayList<Channel> out=new ArrayList<>(); JSONObject o=new JSONObject(s); JSONArray a=o.optJSONArray("channels"); if(a==null)return out;
        for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i); out.add(new Channel(c.optString("id"),c.optString("name"),c.optString("category")));} return out;
    }

    private String http(String u) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(15000); c.setRequestProperty("User-Agent","LiveTgTV-Native/1.0");
        BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); String line; while((line=r.readLine())!=null)s.append(line); r.close(); c.disconnect(); return s.toString();
    }

    private void playChannel(Channel ch) {
        status.setText("Resolving "+ch.name+"…");
        executor.execute(() -> {
            try {
                String base=(v1?"https://livetgtv.lovable.app/api/public/channels/":"https://livetgtv.lovable.app/api/public/v2/channels/");
                JSONObject o=new JSONObject(http(base+Uri.encode(ch.id)));
                String uri=null; String kid=null,key=null;
                if(v1){ JSONArray src=o.optJSONArray("sources"); if(src==null||src.length()==0)throw new Exception("No V1 source"); uri=src.getString(0); JSONObject drm=o.optJSONObject("drm"); if(drm!=null){kid=drm.optString("keyId",null);key=drm.optString("key",null);} }
                else { JSONArray servers=o.optJSONArray("servers"); if(servers!=null){ for(int i=0;i<servers.length();i++){String p=servers.getJSONObject(i).optString("playable",null); if(p!=null&&!p.isEmpty()){uri=p;break;}} } if(uri==null)throw new Exception("No V2 server"); }
                final String fUri=uri, fKid=kid, fKey=key; main.post(()->startPlayer(ch.name,fUri,fKid,fKey));
            }catch(Exception e){main.post(()->{status.setText("Playback source unavailable"); Toast.makeText(this,"Could not resolve "+ch.name,Toast.LENGTH_SHORT).show();});}
        });
    }

    private void startPlayer(String title,String uri,String kid,String key) {
        root.removeAllViews();
        FrameLayoutWrapper frame=new FrameLayoutWrapper(this); root.addView(frame,new LinearLayout.LayoutParams(-1,-1));
        playerView=new PlayerView(this); playerView.setUseController(true); playerView.setControllerAutoShow(true); playerView.setControllerHideOnTouch(true); frame.addView(playerView,new android.widget.FrameLayout.LayoutParams(-1,-1));
        TextView label=text(title,18,Color.WHITE); label.setPadding(dp(18),dp(10),dp(18),dp(10)); frame.addView(label,new android.widget.FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.LEFT));
        TextView hint=text("BACK = channels  •  OK = controls  •  ◀/▶ = seek",12,Color.LTGRAY); hint.setGravity(Gravity.CENTER); frame.addView(hint,new android.widget.FrameLayout.LayoutParams(-1,dp(40),Gravity.BOTTOM));

        ExoPlayer.Builder builder=new ExoPlayer.Builder(this);
        if(v1 && kid!=null && key!=null && !kid.isEmpty()&&!key.isEmpty()){
            try {
                byte[] response=clearKeyResponse(kid,key);
                LocalMediaDrmCallback callback=new LocalMediaDrmCallback(response);
                DefaultDrmSessionManager drm=new DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER).build(callback);
                builder.setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDrmSessionManagerProvider(item->drm));
            }catch(Exception ignored){}
        }
        player=builder.build(); playerView.setPlayer(player);
        MediaItem.Builder mb=new MediaItem.Builder().setUri(uri);
        if(v1) mb.setMimeType(MimeTypes.APPLICATION_MPD); else mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(mb.build()); player.addListener(new Player.Listener(){@Override public void onPlayerError(androidx.media3.common.PlaybackException e){Toast.makeText(MainActivity.this,"Playback error: "+e.errorCodeName,Toast.LENGTH_LONG).show();}}); player.prepare(); player.play();
        playerView.requestFocus();
    }

    private byte[] clearKeyResponse(String kidHex,String keyHex) throws Exception {
        byte[] kid=hex(kidHex), key=hex(keyHex);
        String k=base64Url(key), kId=base64Url(kid);
        String json="{\"keys\":[{\"kty\":\"oct\",\"k\":\""+k+"\",\"kid\":\""+kId+"\"}],\"type\":\"temporary\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
    private byte[] hex(String s){ if(s.startsWith("0x"))s=s.substring(2); byte[] b=new byte[s.length()/2]; for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return b; }
    private String base64Url(byte[] b){ return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

    @Override public void onBackPressed(){ if(player!=null){player.release();player=null;playerView=null;showHome();loadChannels();} else super.onBackPressed(); }
    @Override protected void onDestroy(){if(player!=null)player.release();executor.shutdownNow();super.onDestroy();}

    static class Channel {String id,name,category; Channel(String i,String n,String c){id=i;name=n;category=c;}}
    static class FrameLayoutWrapper extends android.widget.FrameLayout { FrameLayoutWrapper(Activity c){super(c);setBackgroundColor(Color.BLACK);} }
}
