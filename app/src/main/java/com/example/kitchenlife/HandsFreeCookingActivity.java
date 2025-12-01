package com.example.kitchenlife.ui.mealplan;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.kitchenlife.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스마트 핸즈프리 요리 도우미 화면
 * - 조리 단계 텍스트에 "분" 이 포함되면 타이머 제안 카드 표시
 * - OneUI 느낌의 넓은 여백 + 라운드 카드 구성
 */
public class HandsFreeCookingActivity extends AppCompatActivity {

    private TextView tvStepIndicator;
    private TextView tvInstruction;

    private LinearLayout timerSuggestionCard;
    private TextView tvTimerSuggestionText;
    private ImageView ivTimerIcon;

    private View micButton;

    private View btnPrev;
    private View btnRepeat;
    private View btnNext;

    // 데모용 단계 데이터
    private int currentStep = 1;
    private final int totalStep = 8;

    private final String[] stepTexts = new String[]{
            "양파를 얇게 썰어주세요. 찬물에 3분 정도 담가두면 매운맛이 빠집니다.",
            "돼지고기를 중불에서 3분간 뒤집어가며 굽습니다.",
            "간장, 설탕, 다진 마늘을 넣고 약불에서 2분간 졸입니다.",
            "불을 끄고 참기름을 한 바퀴 둘러 섞어줍니다.",
            "그릇에 밥을 담고 위에 고기를 올려주세요.",
            "김가루와 깨를 살짝 뿌려 마무리합니다.",
            "기호에 따라 계란프라이를 올려도 좋습니다.",
            "완성된 요리를 접시에 담아 식탁에 올려주세요."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hands_free_cooking);

        initToolbar();
        initViews();
        renderStep();
    }

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

        micButton = findViewById(R.id.btn_mic);

        btnPrev = findViewById(R.id.btn_prev);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnNext = findViewById(R.id.btn_next);

        micButton.setOnClickListener(v ->
                Toast.makeText(this, "음성 인식 기능은 추후 구현 예정입니다.", Toast.LENGTH_SHORT).show()
        );

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

        btnRepeat.setOnClickListener(v ->
                Toast.makeText(this, "조리 단계를 다시 읽어줍니다. (TTS 연동 예정)", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * 현재 단계에 맞춰 텍스트와 타이머 카드 갱신
     */
    private void renderStep() {
        tvStepIndicator.setText("Step " + currentStep + " / " + totalStep);

        String instruction = stepTexts[currentStep - 1];
        tvInstruction.setText(instruction);

        // "숫자 + 분" 패턴이 있는 경우만 타이머 카드 노출
        Pattern pattern = Pattern.compile("(\\d+)분");
        Matcher matcher = pattern.matcher(instruction);

        if (matcher.find()) {
            String minute = matcher.group(1);
            String suggestion = minute + "분 타이머를 설정할까요?";
            tvTimerSuggestionText.setText(suggestion);
            timerSuggestionCard.setVisibility(View.VISIBLE);
        } else {
            timerSuggestionCard.setVisibility(View.GONE);
        }
    }
}
