package com.mealplanner;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton;
import android.widget.TextView;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText inputName;
    private TextInputEditText inputSurname;
    private TextInputEditText inputEmail;
    private TextInputEditText inputPassword;
    private TextInputEditText inputMessage;
    private MaterialButton btnSubmit;
    private TextView btnSwitchToLogin;
    private boolean isSignUpMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        inputName = findViewById(R.id.input_name);
        inputSurname = findViewById(R.id.input_surname);
        inputEmail = findViewById(R.id.input_email);
        inputPassword = findViewById(R.id.input_password);
        inputMessage = findViewById(R.id.input_message);
        btnSubmit = findViewById(R.id.btn_submit);
        btnSwitchToLogin = findViewById(R.id.btn_switch_to_login);

        btnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSignUpMode) {
                    handleSignUp();
                } else {
                    handleLogin();
                }
            }
        });

        btnSwitchToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMode();
            }
        });
    }

    private void handleSignUp() {
        String name = inputName.getText().toString().trim();
        String surname = inputSurname.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();
        String message = inputMessage.getText().toString().trim();

        if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Sign up successful! Welcome " + name + "!", Toast.LENGTH_LONG).show();
        finish();
    }

    private void handleLogin() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Login successful!", Toast.LENGTH_LONG).show();
        finish();
    }

    private void toggleMode() {
        isSignUpMode = !isSignUpMode;

        if (isSignUpMode) {
            inputName.setVisibility(View.VISIBLE);
            inputSurname.setVisibility(View.VISIBLE);
            inputMessage.setVisibility(View.VISIBLE);
            btnSubmit.setText("Submit");
            btnSwitchToLogin.setText("Already have an account? Login");
        } else {
            inputName.setVisibility(View.GONE);
            inputSurname.setVisibility(View.GONE);
            inputMessage.setVisibility(View.GONE);
            btnSubmit.setText("Login");
            btnSwitchToLogin.setText("Don't have an account? Sign Up");
        }
    }
}
