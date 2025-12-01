package com.example.kitchenlife.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.kitchenlife.R;
import com.example.kitchenlife.data.RecipeStep;
import com.example.kitchenlife.net.SupabaseClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class HandsFreeCookingActivity extends AppCompatActivity {

    // 전달받는 값
    private long recipeId = -1;
    private String recipeTitleText = "";

    // UI
    private TextView tvStepIndicator;
    private TextView tvInstruction;

    private LinearLayout timerSuggestionCard;
    private TextView tvTimerSuggestionText;
    private ImageView ivTimerIcon;

    private TextView tvTimerRunning;

    private View micButton;
    private View btnPrev, btnRepeat, btnNext;
    private View micInnerCircle;

    // 조리 단계 데이터
    private int currentStep = 1;
    private int totalStep = 0;
    private final ArrayList<RecipeStep> steps = new ArrayList<>();

    // STT
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;

    // TTS
    private TextToSpeech tts;
    private boolean ttsReady = false;

    // 첫 진입 자동 안내 1회만 실행
    private boolean firstIntroDone = false;

    // steps 로딩 여부
    private boolean stepsLoaded = false;

    // 타이머
    private CountDownTimer countDownTimer;
    private int timerSeconds = 0;

    // Retrofit API
    interface LocalApi {
        @GET("/rest/v1/v_recipe_steps")
        Call<List<RecipeStep>> getSteps(
                @Query("select") String select,
                @Query("recipe_id") String idEq,
                @Query("order") String order
        );
    }

    private LocalApi api() {
        return SupabaseClient.get().create(LocalApi.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // 🔐 음성 권한 요청
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    1001
            );
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hands_free_cooking);

        initTTS();

        // 인텐트 받기
        recipeId = getIntent().getLongExtra("recipe_id", -1);
        String recipeTitle = getIntent().getStringExtra("recipe_title");

        if (recipeId <= 0) {
            Toast.makeText(this, "레시피 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 타이틀 적용
        TextView tvTitle = findViewById(R.id.tv_title);
        if (recipeTitle != null && !recipeTitle.isEmpty()) {
            tvTitle.setText(recipeTitle);
        }
        recipeTitleText = (recipeTitle != null) ? recipeTitle : "";

        initToolbar();
        initViews();

        loadStepsFromDB();
        initSTT();
    }

    // -----------------------------
    // TTS 초기화
    // -----------------------------
    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.0f);

                ttsReady = true;
                // TTS 준비 완료 후, steps까지 로드되었다면 자동 안내 시도
                tryAutoIntro();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }

    // -----------------------------
    // UI 초기화
    // -----------------------------
    private void initToolbar() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnMenu = findViewById(R.id.btn_more);

        btnBack.setOnClickListener(v -> onBackPressed());
        btnMenu.setOnClickListener(v ->
                Toast.makeText(this, "옵션 메뉴는 추후 구현 예정입니다.", Toast.LENGTH_SHORT).show()
        );
    }

    private void initViews() {

        tvStepIndicator = findViewById(R.id.tv_step_indicator);
        tvInstruction = findViewById(R.id.tv_instruction);

        timerSuggestionCard = findViewById(R.id.layout_timer_suggestion);
        tvTimerSuggestionText = findViewById(R.id.tv_timer_suggestion);
        ivTimerIcon = findViewById(R.id.iv_timer_icon);

        tvTimerRunning = findViewById(R.id.tv_timer_running);

        micButton = findViewById(R.id.btn_mic);
        micInnerCircle = findViewById(R.id.mic_inner_circle);

        btnPrev = findViewById(R.id.btn_prev);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnNext = findViewById(R.id.btn_next);

        // 🎤 STT 시작
        micButton.setOnClickListener(v -> startListening());

        // ⭐ 타이머 제안 카드 클릭 → 타이머 시작
        timerSuggestionCard.setOnClickListener(v -> {
            if (timerSeconds > 0) {
                startTimer(timerSeconds);
                timerSuggestionCard.setVisibility(View.GONE);
                tvTimerRunning.setVisibility(View.VISIBLE);
                tvTimerRunning.setBackgroundResource(R.drawable.bg_timer_green);
                animateTimerAppearance(tvTimerRunning);
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (currentStep > 1) {
                currentStep--;
                renderStep();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentStep < totalStep) {
                currentStep++;
                renderStep();
            }
        });

        btnRepeat.setOnClickListener(v -> {
            if (!steps.isEmpty()) {
                speakInstruction(steps.get(currentStep - 1).description);
            }
        });
    }

    // -----------------------------
    // 타이머 등장 애니메이션
    // -----------------------------
    private void animateTimerAppearance(View view) {
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);
        view.setAlpha(0f);

        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .start();
    }

    // -----------------------------
    // STT
    // -----------------------------
    private void startListening() {

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "먼저 음성 권한을 허용해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(speechIntent);
        }
    }

    private void initSTT() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }

            @Override
            public void onError(int error) {
                Toast.makeText(
                        HandsFreeCookingActivity.this,
                        "음성 인식 오류",
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    handleVoiceCommand(matches.get(0));
                }
            }

            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
    }

    private void handleVoiceCommand(String command) {
        command = command.replace(" ", "").toLowerCase();

        // 다음 단계
        if (command.contains("다음") || command.contains("넘겨")) {
            if (currentStep < totalStep) {
                currentStep++;
                renderStep();
                speakInstruction(steps.get(currentStep - 1).description);
            }
            return;
        }

        // 이전 단계
        if (command.contains("이전") || command.contains("뒤로")) {
            if (currentStep > 1) {
                currentStep--;
                renderStep();
                speakInstruction(steps.get(currentStep - 1).description);
            }
            return;
        }

        // 다시 설명
        if (command.contains("다시")) {
            speakInstruction(steps.get(currentStep - 1).description);
            return;
        }

        Toast.makeText(this, "명령을 이해하지 못했어요", Toast.LENGTH_SHORT).show();
    }

    // -----------------------------
    // 타이머
    // -----------------------------
    private void startTimer(int totalSeconds) {

        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(totalSeconds * 1000L, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);

                int m = secondsLeft / 60;
                int s = secondsLeft % 60;
                tvTimerRunning.setText(String.format("%02d:%02d", m, s));

                float progress = (float) secondsLeft / totalSeconds;

                if (progress > 0.6f) {
                    tvTimerRunning.setBackgroundResource(R.drawable.bg_timer_green);
                } else if (progress > 0.3f) {
                    tvTimerRunning.setBackgroundResource(R.drawable.bg_timer_yellow);
                } else {
                    tvTimerRunning.setBackgroundResource(R.drawable.bg_timer_red);
                }
            }

            @Override
            public void onFinish() {
                tvTimerRunning.setText("00:00");
                tvTimerRunning.setBackgroundResource(R.drawable.bg_timer_red);

                // 알람 TTS
                if (ttsReady) {
                    tts.speak("타이머가 끝났어요", TextToSpeech.QUEUE_FLUSH, null, "timer_done");
                }

                // 진동
                Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vib != null) vib.vibrate(500);
            }

        }.start();
    }

    // -----------------------------
    // DB에서 조리 단계 불러오기
    // -----------------------------
    private void loadStepsFromDB() {

        String idEq = "eq." + recipeId;

        api().getSteps("*", idEq, "step_no.asc").enqueue(new Callback<List<RecipeStep>>() {
            @Override
            public void onResponse(@NonNull Call<List<RecipeStep>> call,
                                   @NonNull Response<List<RecipeStep>> res) {

                if (!res.isSuccessful() || res.body() == null) {
                    Toast.makeText(HandsFreeCookingActivity.this,
                            "조리 단계를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                steps.clear();
                steps.addAll(res.body());
                totalStep = steps.size();

                if (totalStep == 0) {
                    Toast.makeText(HandsFreeCookingActivity.this,
                            "등록된 조리 단계가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                currentStep = 1;
                renderStep();

                // steps 로딩 완료 플래그
                stepsLoaded = true;
                // TTS가 이미 준비되었으면 자동 안내 시도
                tryAutoIntro();
            }

            @Override
            public void onFailure(@NonNull Call<List<RecipeStep>> call,
                                  @NonNull Throwable t) {
                Toast.makeText(HandsFreeCookingActivity.this,
                        "네트워크 오류로 조리 단계를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -----------------------------
    // TTS/데이터 준비 후 자동 안내
    // -----------------------------
    private void tryAutoIntro() {
        if (!ttsReady || !stepsLoaded) return;
        if (firstIntroDone) return;

        firstIntroDone = true;

        // 1) 타이틀 + 도우미 시작 멘트
        String intro = recipeTitleText + " 핸즈프리 도우미를 시작합니다.";
        tts.speak(intro, TextToSpeech.QUEUE_FLUSH, null, "intro");

        // 2) 0.5초 후 Step 1 읽기
        new Handler().postDelayed(() -> {
            if (!steps.isEmpty()) {
                String step1msg = "순서 1. " + steps.get(0).description;
                tts.speak(step1msg, TextToSpeech.QUEUE_ADD, null, "step1");
            }
        }, 500);
    }

    private void updateButtonState() {

        if (currentStep == 1) {
            btnPrev.setEnabled(false);
            btnPrev.setBackgroundResource(R.drawable.bg_button_disabled);
        } else {
            btnPrev.setEnabled(true);
            btnPrev.setBackgroundResource(R.drawable.bg_primary_button);
        }

        if (currentStep == totalStep) {
            btnNext.setEnabled(false);
            btnNext.setBackgroundResource(R.drawable.bg_button_disabled);
        } else {
            btnNext.setEnabled(true);
            btnNext.setBackgroundResource(R.drawable.bg_primary_button);
        }
    }

    // -----------------------------
    // UI 업데이트
    // -----------------------------
    private void renderStep() {

        if (steps.isEmpty()) return;

        tvStepIndicator.setText("Step " + currentStep + " / " + totalStep);

        RecipeStep step = steps.get(currentStep - 1);
        String instruction = (step.description != null) ? step.description : "";
        tvInstruction.setText(instruction);

        // 기존 타이머 UI 초기화
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        tvTimerRunning.setVisibility(View.GONE);
        tvTimerRunning.setText("00:00");
        tvTimerRunning.setBackgroundResource(R.drawable.bg_timer_card);

        // 타이머 제안 표시
        Pattern pattern = Pattern.compile("(\\d+)분");
        Matcher matcher = pattern.matcher(instruction);

        if (matcher.find()) {
            String minute = matcher.group(1);
            timerSeconds = Integer.parseInt(minute) * 60;

            tvTimerSuggestionText.setText(minute + "분 타이머를 설정할까요?");
            timerSuggestionCard.setVisibility(View.VISIBLE);

        } else {
            timerSuggestionCard.setVisibility(View.GONE);
            timerSeconds = 0;
        }

        updateButtonState();
        // 여기서는 자동으로 읽지 않음 (intro와 겹치지 않게)
        // speakInstruction(instruction);
    }

    // -----------------------------
    // 현재 단계 읽기 (버튼/음성 명령 전용)
    // -----------------------------
    private void speakInstruction(String text) {
        if (tts != null) {
            tts.stop();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "step_tts");
        }
    }
}
