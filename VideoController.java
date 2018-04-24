package com.wiipu.drink.Utils;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.LinearLayout;

import com.wiipu.drink.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by changliliao on 23/12/2017.
 */

public class VideoController {
    private final String TAG="VideoController";

    private MediaPlayer firstPlayer;
    private MediaPlayer nextPlayer;
    private static MediaPlayer currentPlayer;
    //资源文件列表
    private ArrayList<String> videoList;
    private SurfaceHolder surfaceHolder;
    //所有player对象的缓存
    private HashMap<String, MediaPlayer> playersCache = new HashMap<String, MediaPlayer>();
    private int currentVideoIndex=0;
    MediaPlayer.OnPreparedListener mediaPlayerOnPreparedListener=null;
    private ExecutorService cachedThreadPool;
    private static float volume = 1.0f;

    public VideoController(SurfaceHolder surfaceHolder, final SurfaceView surfaceView, final LinearLayout linearLayout){
        this.surfaceHolder=surfaceHolder;
        videoList=FileUtil.getVedioList();
        cachedThreadPool = Executors.newCachedThreadPool();
        //自适应宽高
        mediaPlayerOnPreparedListener = new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer arg0) {
                // 首先取得video的宽和高
                MediaPlayer mediaPlayer=playersCache.get(String.valueOf(currentVideoIndex%videoList.size()));
                int vWidth = mediaPlayer.getVideoWidth();
                int vHeight = mediaPlayer.getVideoHeight();
                float vProportion=(float)vHeight/(float)vWidth;

                // 该LinearLayout的父容器 android:orientation="vertical" 必须
                int lw = linearLayout.getWidth();
                int lh = linearLayout.getHeight();
                float lProportion = (float) lh/(float) lw;


                if (vWidth > lw || vHeight > lh) {
                    // 如果video的宽或者高超出了当前屏幕的大小，则要进行缩放
                    float wRatio = (float) vWidth / (float) lw;
                    float hRatio = (float) vHeight / (float) lh;

                    // 选择大的一个进行缩放
                    float ratio = Math.max(wRatio, hRatio);
                    vWidth = (int) Math.ceil((float) vWidth / ratio);
                    vHeight = (int) Math.ceil((float) vHeight / ratio);

                    // 设置surfaceView的布局参数
                    LinearLayout.LayoutParams lp= new LinearLayout.LayoutParams(vWidth, vHeight);
                    lp.gravity = Gravity.CENTER;
                    surfaceView.setLayoutParams(lp);
                }else {
                    int weight;
                    int height;
                    if(vProportion<=lProportion){
                        weight=lw;
                        height=(int)Math.ceil(((float)lw/(float) vWidth)*vHeight);
                    }else {
                        height=lh;
                        weight=(int)Math.ceil(((float)lh/(float) vHeight)*vWidth);
                    }
                    LinearLayout.LayoutParams lp= new LinearLayout.LayoutParams(weight, height);
                    lp.gravity = Gravity.CENTER;
                    surfaceView.setLayoutParams(lp);
                }
            }
        };
    }

    public void startPlay(){
        initFirstPlayer();
    }
    /*
     * 初始化播放首段视频的player
     */
    private void initFirstPlayer() {

        firstPlayer = new MediaPlayer();
        firstPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        firstPlayer.setDisplay(surfaceHolder);

        firstPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onVideoPlayCompleted(mp);
            }
        });
        firstPlayer.setOnPreparedListener(mediaPlayerOnPreparedListener);
        //设置cachePlayer为该player对象

        if(videoList.size()==1)//{
            firstPlayer.setLooping(true);
        initNextPlayer();
    }

    private void startPlayFirstVideo() {
        try {
            firstPlayer.setDataSource(videoList.get(0));
            playersCache.put("0",firstPlayer);
            firstPlayer.prepare();
            currentPlayer = firstPlayer;
            firstPlayer.start();
        } catch (IOException e) {
            // TODO 自动生成的 catch 块
            e.printStackTrace();
        }
    }

    /*
     * 新开线程负责初始化负责播放剩余视频分段的player对象,避免UI线程做过多耗时操作
     */
    private void initNextPlayer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                startPlayFirstVideo();
                for (int i = 1; i < videoList.size(); i++) {
                    nextPlayer = new MediaPlayer();
                  //  nextPlayer.setVolume( 0f , 0f );
                    nextPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

                    nextPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            onVideoPlayCompleted(mp);
                        }
                    });
                    nextPlayer.setOnPreparedListener(mediaPlayerOnPreparedListener);
                    try {
                        nextPlayer.setDataSource(videoList.get(i));
                        nextPlayer.prepare();
                    } catch (IOException e) {
                        // TODO 自动生成的 catch 块
                        e.printStackTrace();
                    }

                    //put nextMediaPlayer in cache
                    playersCache.put(String.valueOf(i), nextPlayer);
                }

            }
        }).start();
    }

    /*
     * 负责处理一段视频播放过后，切换player播放下一段视频
     */
    private void onVideoPlayCompleted(final MediaPlayer mp) {

        cachedThreadPool.execute(new Runnable() {

            @Override
            public void run() {
                mp.setDisplay(null);
                mp.stop();
                mp.reset();

                try {
                    mp.setDataSource(videoList.get(currentVideoIndex));

                    mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            onVideoPlayCompleted(mp);
                        }
                    });

                    mp.setOnPreparedListener(mediaPlayerOnPreparedListener);

                    if(videoList.size()==1)//{
                        mp.setLooping(true);
                    mp.prepare();
                } catch (Exception e) {
                    e.printStackTrace();
                    Set<String> set = playersCache.keySet();
                    for(String str : set){
                        playersCache.get(str).release();
                    }
                    initFirstPlayer();
                    return;
                }

                ++currentVideoIndex;
                if(currentVideoIndex==videoList.size())
                    currentVideoIndex=0;
                currentPlayer = playersCache.get(String.valueOf(currentVideoIndex%videoList.size()));
                if (currentPlayer != null) {

                    currentPlayer.seekTo(0);
                    currentPlayer.setVolume(volume,volume);
                    currentPlayer.start();
                    currentPlayer.setDisplay(surfaceHolder);
                } else {
                    Log.d(TAG, "循环错误");
                }
            }
        });

    }

    public void setVedioVolume(){
        currentPlayer.setVolume(volume,volume);
    }

    private static void turnDownVolume(){
        volume = 0.1f;
        currentPlayer.setVolume(volume,volume);
    }

    private static void turnUpVolume(){
        volume = 1.0f;
        currentPlayer.setVolume(volume,volume);
    }

}


