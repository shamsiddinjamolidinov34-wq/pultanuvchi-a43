package uz.pultanuvchi;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Ilovaning o'z ovozi (Android TextToSpeech, tizimdagi tanlangan ovoz dvigateli ishlatiladi).
 * Ekran o'quvchi ishlayotgan bo'lsa, odatda o'chiq turadi (ikki marta o'qilmasligi uchun).
 */
public final class Speaker implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private boolean ready = false;

    public Speaker(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            return;
        }
        Locale uz = Locale.forLanguageTag("uz-UZ");
        if (tts.isLanguageAvailable(uz) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(uz);
        }
        ready = true;
    }

    public void speak(String text) {
        if (ready && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pul");
        }
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
