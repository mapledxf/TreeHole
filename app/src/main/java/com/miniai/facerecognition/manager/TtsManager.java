package com.miniai.facerecognition.manager;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.miniai.facerecognition.App;
import com.miniai.facerecognition.callback.TtsCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TtsManager {
    private static final String TAG = "[TreeHole]TtsManager";
    private TextToSpeech tts = null;
    final private List<String> ttsSentenceSeparator = Arrays.asList("。", ".", "？", "?", "！", "!", "……", "\n"); // 用于为TTS断句
    private int ttsSentenceEndIndex = 0;
    private String ttsLastId = "";
    private TtsCallback callback;

    private static final class Holder {
        private static final TtsManager INSTANCE = new TtsManager();
    }

    /**
     * Default constructor
     */
    private TtsManager() {
    }

    /**
     * Single instance.
     *
     * @return the instance.
     */
    public static TtsManager getInstance() {
        return Holder.INSTANCE;
    }

    public boolean init() {
        tts = new TextToSpeech(App.getInstance(), status -> {
            if(status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.getDefault());
                if(res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Unsupported language.");
                }else{
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            Log.d(TAG, "onStart: " + utteranceId);
                            if (callback != null) {
                                callback.onTtsStart();
                            }
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            Log.d(TAG, "onDone: " + utteranceId);
                            if(ttsLastId.equals(utteranceId)) {
                                Log.d(TAG, "Queue finished");
                                if (callback != null) {
                                    callback.onTtsFinish();
                                }
                            }
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.e(TAG, "onError: " + utteranceId);
                            if (callback != null) {
                                callback.onTtsError();
                            }
                        }
                    });
                    Log.d(TAG, "Init success.");
                }
            }else{
                Log.e(TAG, "Init failed. ErrorCode: " + status);
            }
        });
        return true;
    }

    public void setTtsCallback(TtsCallback callback) {
        this.callback = callback;
    }

    public void play(String text) {
        int nextSentenceEndIndex = text.length();
        boolean found = false;
        for (String separator : ttsSentenceSeparator) { // 查找最后一个断句分隔符
            int index = text.indexOf(separator, ttsSentenceEndIndex);
            if (index != -1 && index < nextSentenceEndIndex) {
                nextSentenceEndIndex = index + separator.length();
                found = true;
            }
        }
        if (found) { // 找到断句分隔符则添加到朗读队列
            String sentence = text.substring(ttsSentenceEndIndex, nextSentenceEndIndex);
            ttsSentenceEndIndex = nextSentenceEndIndex;
            String id = UUID.randomUUID().toString();
            tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, id);
            ttsLastId = id;
        }
    }

    public void stop() {
        ttsSentenceEndIndex = 0;
        tts.stop();
    }

}
