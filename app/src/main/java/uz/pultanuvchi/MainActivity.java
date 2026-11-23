package uz.pultanuvchi;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pul tanuvchi: kamera orqali O'zbekiston banknotasining qiymatini aniqlaydi va natijani
 * ekran o'quvchi o'qiy oladigan matn (jonli hudud) sifatida chiqaradi.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PulTanuvchi";
    /** Pul ko'rinmay qolgach, shuncha vaqtdan keyin keyingi pulni yangidan aytamiz. */
    private static final long RELEASE_MS = 2500L;
    /** Kadrning o'rtacha yorqinligi (0..255) shundan past bo'lsa, qorong'i deb hisoblaymiz. */
    private static final int DARK_LUMA = 30;

    private PreviewView previewView;
    private View panel;
    private TextView tvResult;
    private TextView tvDigits;
    private Button btnRepeat;
    private Button btnTorch;
    private Button btnPause;
    private Button btnMenu;

    private SharedPreferences prefs;
    private ExecutorService cameraExecutor;
    private Speaker speaker;
    private TextRecognizer recognizer;
    private final DenominationTracker tracker = new DenominationTracker();
    private Camera camera;

    private ActivityResultLauncher<String> cameraPermission;
    private ActivityResultLauncher<String> pickImage;

    private volatile boolean scanning = false;
    private boolean cameraBound = false;
    private boolean cameraStarting = false;
    private boolean paused = false;
    private boolean torchOn = false;
    private boolean torchTouchedByUser = false;
    private boolean speakerOn = false;
    private boolean newAlphabet = false;
    private boolean permanentlyDenied = false;

    private String lastMessage = "";
    private int lockedIndex = -1;
    private boolean lockedHigh = false;
    private long lastEvidenceAt = 0L;
    private long lastHintAt = 0L;
    private long scanStartAt = 0L;
    private String lastHintKey = "";
    private int hintStreak = 0;
    private int darkFrames = 0;

    // ------------------------------------------------------------ hayot sikli

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = findViewById(R.id.previewView);
        panel = findViewById(R.id.panel);
        tvResult = findViewById(R.id.tvResult);
        tvDigits = findViewById(R.id.tvDigits);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnTorch = findViewById(R.id.btnTorch);
        btnPause = findViewById(R.id.btnPause);
        btnMenu = findViewById(R.id.btnMenu);

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);
        newAlphabet = prefs.getBoolean("newAlphabet", false);

        cameraExecutor = Executors.newSingleThreadExecutor();
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        speaker = new Speaker(this);
        speakerOn = !screenReaderActive();

        cameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startCameraIfNeeded();
                    } else {
                        permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                                this, Manifest.permission.CAMERA);
                        show(t(R.string.camera_denied), "");
                    }
                });
        pickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        recognizeStill(uri);
                    }
                });

        final View root = findViewById(R.id.root);
        final int baseLeft = panel.getPaddingLeft();
        final int baseTop = panel.getPaddingTop();
        final int baseRight = panel.getPaddingRight();
        final int baseBottom = panel.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            panel.setPadding(baseLeft + bars.left, baseTop, baseRight + bars.right, baseBottom + bars.bottom);
            return insets;
        });

        btnRepeat.setOnClickListener(v -> onRepeat());
        btnTorch.setOnClickListener(v -> toggleTorch(true));
        btnPause.setOnClickListener(v -> togglePause());
        btnMenu.setOnClickListener(v -> showMenu());

        refreshLabels();
        show(t(R.string.starting), "");

        if (!hasCameraPermission()) {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanning = false;
        speaker.shutdown();
        recognizer.close();
        cameraExecutor.shutdown();
    }

    // ---------------------------------------------------------------- kamera

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCameraIfNeeded() {
        if (hasCameraPermission() && !cameraBound && !cameraStarting) {
            startCamera();
        }
    }

    private void startCamera() {
        cameraStarting = true;
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ResolutionSelector selector = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 960),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(selector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

                cameraBound = true;
                cameraStarting = false;
                scanning = !paused;
                scanStartAt = SystemClock.elapsedRealtime();
                lastHintAt = 0L;
                show(t(R.string.ready), "");
            } catch (Exception e) {
                Log.e(TAG, "Kamera ishga tushmadi", e);
                cameraStarting = false;
                show(t(R.string.camera_error), "");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** Kamera oqimidan kelgan har bir kadrni matn aniqlashga beradi. */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy proxy) {
        Image media = proxy.getImage();
        if (media == null || !scanning) {
            proxy.close();
            return;
        }
        try {
            final int luma = averageLuma(proxy);
            InputImage image = InputImage.fromMediaImage(media, proxy.getImageInfo().getRotationDegrees());
            recognizer.process(image)
                    .addOnSuccessListener(result -> onRecognized(result, luma))
                    .addOnCompleteListener(task -> proxy.close());
        } catch (RuntimeException e) {
            Log.w(TAG, "Kadrni tahlil qilib bo'lmadi", e);
            proxy.close();
        }
    }

    /** Kadrning o'rtacha yorqinligi (Y tekisligidan, siyrak tanlab olinadi). */
    private static int averageLuma(ImageProxy proxy) {
        ImageProxy.PlaneProxy plane = proxy.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int width = proxy.getWidth();
        int height = proxy.getHeight();
        long sum = 0;
        int count = 0;
        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x += 16) {
                int index = y * rowStride + x * pixelStride;
                if (index < buffer.limit()) {
                    sum += buffer.get(index) & 0xFF;
                    count++;
                }
            }
        }
        return count == 0 ? 128 : (int) (sum / count);
    }

    private static List<String> linesOf(Text text) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                lines.add(line.getText());
            }
        }
        return lines;
    }

    // ------------------------------------------------------- aniqlash va natija

    private void onRecognized(Text result, int luma) {
        if (!scanning) {
            return;
        }
        long now = SystemClock.elapsedRealtime();

        if (luma < DARK_LUMA) {
            darkFrames++;
        } else {
            darkFrames = 0;
        }
        if (darkFrames >= 3 && !torchOn && !torchTouchedByUser && hasFlash()) {
            setTorch(true);
            darkFrames = 0;
            lastHintAt = now;
            show(t(R.string.torch_auto), "");
            return;
        }

        FrameEvidence evidence = BanknoteMatcher.analyze(linesOf(result));
        if (evidence.anyEvidence()) {
            lastEvidenceAt = now;
        }

        // Pul ko'rinmay qoldi: keyingi pulni yangidan aytishga tayyor bo'lamiz.
        if (lockedIndex >= 0 && now - lastEvidenceAt > RELEASE_MS) {
            lockedIndex = -1;
            lockedHigh = false;
            tracker.reset();
            lastHintAt = now;
        }

        Decision decision = tracker.update(evidence);
        if (decision != null) {
            boolean changed = decision.index != lockedIndex;
            boolean upgraded = !changed && decision.high && !lockedHigh;
            if (changed || upgraded) {
                lockedIndex = decision.index;
                lockedHigh = decision.high;
                lastEvidenceAt = now;
                lastHintAt = now;
                announce(decision);
            }
            return;
        }
        if (lockedIndex < 0) {
            maybeHint(now, evidence);
        }
    }

    private void announce(Decision d) {
        Banknote note = Banknotes.ALL.get(d.index);
        String spoken = alpha(note.spoken);
        String message = d.high ? cap(spoken) : t(R.string.probably) + " " + spoken;
        String digits = (d.high ? "" : "\u2248 ") + note.digits + " " + alpha("so\u02BBm");
        show(message, digits);
        vibrate(d.high ? 150L : 70L);
    }

    private void maybeHint(long now, FrameEvidence evidence) {
        long interval = hintStreak >= 3 ? 15000L : 6000L;
        long reference = lastHintAt > 0L ? lastHintAt : scanStartAt;
        if (now - reference < interval) {
            return;
        }
        String key;
        int textId;
        if (darkFrames >= 3) {
            key = "dark";
            textId = R.string.hint_dark;
        } else if (tracker.isAmbiguous()) {
            key = "several";
            textId = R.string.hint_several;
        } else if (evidence.anyEvidence() || now - lastEvidenceAt < 3000L) {
            key = "found";
            textId = R.string.hint_found;
        } else {
            key = "none";
            textId = R.string.hint_none;
        }
        hintStreak = key.equals(lastHintKey) ? hintStreak + 1 : 1;
        lastHintKey = key;
        lastHintAt = now;
        show(t(textId), "");
    }

    // ----------------------------------------------------------- galereyadan rasm

    private void recognizeStill(Uri uri) {
        paused = true;
        scanning = false;
        refreshLabels();
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            show(t(R.string.gallery_working), "");
            recognizer.process(image)
                    .addOnSuccessListener(this::onStillRecognized)
                    .addOnFailureListener(e -> show(t(R.string.gallery_error), ""));
        } catch (Exception e) {
            Log.w(TAG, "Rasmni ochib bo'lmadi", e);
            show(t(R.string.gallery_error), "");
        }
    }

    private void onStillRecognized(Text result) {
        FrameEvidence evidence = BanknoteMatcher.analyze(linesOf(result));
        Decision decision = BanknoteMatcher.decideStill(evidence);
        if (decision == null) {
            show(t(R.string.gallery_none), "");
        } else {
            announce(decision);
        }
    }

    // ------------------------------------------------------------- tugmalar

    private void onRepeat() {
        if (!hasCameraPermission()) {
            if (permanentlyDenied) {
                openAppSettings();
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA);
            }
            return;
        }
        if (lastMessage.isEmpty()) {
            show(t(R.string.nothing_to_repeat), "");
            return;
        }
        if (speakerOn) {
            speaker.speak(lastMessage);
        } else {
            tvResult.announceForAccessibility(lastMessage);
        }
    }

    private boolean hasFlash() {
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    private void setTorch(boolean on) {
        if (!hasFlash()) {
            return;
        }
        torchOn = on;
        camera.getCameraControl().enableTorch(on);
        refreshLabels();
    }

    private void toggleTorch(boolean byUser) {
        if (!hasFlash()) {
            show(t(R.string.no_flash), "");
            return;
        }
        if (byUser) {
            torchTouchedByUser = true;
        }
        setTorch(!torchOn);
    }

    private void togglePause() {
        paused = !paused;
        scanning = cameraBound && !paused;
        if (paused) {
            speaker.stop();
            show(t(R.string.paused), "");
        } else {
            tracker.reset();
            lockedIndex = -1;
            lockedHigh = false;
            lastHintAt = 0L;
            scanStartAt = SystemClock.elapsedRealtime();
            show(t(R.string.resumed), "");
        }
        refreshLabels();
    }

    private void showMenu() {
        String[] items = new String[]{
                t(R.string.menu_gallery),
                t(speakerOn ? R.string.menu_voice_off : R.string.menu_voice_on),
                t(R.string.menu_alphabet),
                t(R.string.menu_about)
        };
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.menu_title))
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            pickImage.launch("image/*");
                            break;
                        case 1:
                            toggleVoice();
                            break;
                        case 2:
                            toggleAlphabet();
                            break;
                        default:
                            showAbout();
                            break;
                    }
                })
                .setNegativeButton(t(R.string.close), null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.app_name))
                .setMessage(t(R.string.about_text))
                .setPositiveButton(t(R.string.close), null)
                .show();
    }

    private void toggleVoice() {
        speakerOn = !speakerOn;
        if (!speakerOn) {
            speaker.stop();
        }
        show(t(speakerOn ? R.string.voice_on_msg : R.string.voice_off_msg), "");
    }

    private void toggleAlphabet() {
        newAlphabet = !newAlphabet;
        prefs.edit().putBoolean("newAlphabet", newAlphabet).apply();
        refreshLabels();
        show(t(newAlphabet ? R.string.alphabet_new_on : R.string.alphabet_old_on), "");
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    // ------------------------------------------------------------ yordamchilar

    /** Natijani ekranga chiqaradi (jonli hudud bo'lgani uchun ekran o'quvchi o'zi o'qiydi). */
    private void show(String message, String digits) {
        lastMessage = message;
        tvResult.setText(message);
        tvDigits.setText(digits);
        tvDigits.setVisibility(digits.isEmpty() ? View.GONE : View.VISIBLE);
        if (speakerOn) {
            speaker.speak(message);
        }
    }

    private void refreshLabels() {
        btnRepeat.setText(t(R.string.btn_repeat));
        btnTorch.setText(t(torchOn ? R.string.btn_torch_off : R.string.btn_torch_on));
        btnPause.setText(t(paused ? R.string.btn_resume : R.string.btn_pause));
        btnMenu.setText(t(R.string.btn_menu));
    }

    private String t(int resId) {
        return alpha(getString(resId));
    }

    private String alpha(String s) {
        return newAlphabet ? Alphabet.toNew(s) : s;
    }

    private static String cap(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private boolean screenReaderActive() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Titrash ishlamadi", e);
        }
    }
}
