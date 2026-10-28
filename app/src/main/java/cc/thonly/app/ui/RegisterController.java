package cc.thonly.app.ui;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.TimeZone;

import cc.thonly.app.R;
import cc.thonly.app.data.AuthApi;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.UserSession;
import cc.thonly.app.util.CookieBridge;

/** 注册页：走 {@code /api/user/register}，注册成功后自动保持登录。 */
public final class RegisterController {

    private final Activity activity;
    private final AuthHost host;
    private final UserSession session;
    private final AuthApi authApi = new AuthApi();

    private final EditText usernameInput;
    private final EditText passwordInput;
    private final EditText confirmInput;
    private final EditText emailInput;
    private final TextView errorView;
    private final Button submitButton;

    public RegisterController(Activity activity, View root, UserSession session, AuthHost host) {
        this.activity = activity;
        this.host = host;
        this.session = session;
        this.usernameInput = root.findViewById(R.id.registerUsername);
        this.passwordInput = root.findViewById(R.id.registerPassword);
        this.confirmInput = root.findViewById(R.id.registerConfirm);
        this.emailInput = root.findViewById(R.id.registerEmail);
        this.errorView = root.findViewById(R.id.registerError);
        this.submitButton = root.findViewById(R.id.registerSubmit);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        root.findViewById(R.id.goLogin).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openLoginPage();
            }
        });
    }

    public void onShown() {
        hideError();
    }

    private void submit() {
        final String username = usernameInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        String confirm = confirmInput.getText().toString();
        final String email = emailInput.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError(activity.getString(R.string.auth_need_input));
            return;
        }
        if (!password.equals(confirm)) {
            showError(activity.getString(R.string.auth_password_mismatch));
            return;
        }

        setBusy(true);
        hideError();
        final String timeZone = TimeZone.getDefault().getID();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthApi.Result result = authApi.register(username, password, password, email, timeZone);
                    // 个别实现注册后不下发 Cookie，补一次登录保证进站即已登录
                    if (result.isSuccess() && result.cookies().isEmpty()) {
                        result = authApi.login(username, password, true);
                    }
                    final AuthApi.Result finalResult = result;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleResult(finalResult, username);
                        }
                    });
                } catch (EventApiException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false);
                            showError(activity.getString(R.string.auth_network_error));
                        }
                    });
                }
            }
        }, "register").start();
    }

    private void handleResult(AuthApi.Result result, String fallbackName) {
        setBusy(false);
        if (!result.isSuccess()) {
            showError(result.message());
            return;
        }
        String name = result.username().isEmpty() ? fallbackName : result.username();
        session.save(name, result.cookies());
        CookieBridge.apply(activity, session.cookies());

        passwordInput.setText("");
        confirmInput.setText("");
        Toast.makeText(activity, R.string.auth_register_success, Toast.LENGTH_SHORT).show();
        host.onAuthenticated();
    }

    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        submitButton.setText(busy ? R.string.auth_submitting : R.string.auth_register_button);
    }

    private void showError(String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }
}
