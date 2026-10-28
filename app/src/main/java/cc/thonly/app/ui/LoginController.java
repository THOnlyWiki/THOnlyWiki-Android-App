package cc.thonly.app.ui;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cc.thonly.app.R;
import cc.thonly.app.data.AuthApi;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.UserSession;
import cc.thonly.app.util.CookieBridge;

/** 登录页：走 {@code /api/user/login}，成功后把 Cookie 同步给主站 WebView。 */
public final class LoginController {

    private final Activity activity;
    private final AuthHost host;
    private final UserSession session;
    private final AuthApi authApi = new AuthApi();

    private final LinearLayout formContainer;
    private final LinearLayout statusContainer;
    private final TextView statusText;
    private final EditText usernameInput;
    private final EditText passwordInput;
    private final CheckBox rememberCheck;
    private final TextView errorView;
    private final Button submitButton;

    public LoginController(Activity activity, View root, UserSession session, AuthHost host) {
        this.activity = activity;
        this.host = host;
        this.session = session;
        this.formContainer = root.findViewById(R.id.loginForm);
        this.statusContainer = root.findViewById(R.id.loginStatus);
        this.statusText = root.findViewById(R.id.loginStatusText);
        this.usernameInput = root.findViewById(R.id.loginUsername);
        this.passwordInput = root.findViewById(R.id.loginPassword);
        this.rememberCheck = root.findViewById(R.id.loginRemember);
        this.errorView = root.findViewById(R.id.loginError);
        this.submitButton = root.findViewById(R.id.loginSubmit);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        root.findViewById(R.id.goRegister).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openRegisterPage();
            }
        });
        root.findViewById(R.id.logoutButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });

        render();
    }

    public void onShown() {
        render();
    }

    private void render() {
        boolean loggedIn = session.isLoggedIn();
        formContainer.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        statusContainer.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        if (loggedIn) {
            statusText.setText(activity.getString(R.string.auth_logged_in_as, session.username()));
        }
    }

    private void submit() {
        final String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            showError(activity.getString(R.string.auth_need_input));
            return;
        }

        setBusy(true);
        hideError();
        final String passwordValue = password;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final AuthApi.Result result =
                            authApi.login(username, passwordValue, rememberCheck.isChecked());
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleResult(result, username);
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
        }, "login").start();
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
        render();
        Toast.makeText(activity, R.string.auth_login_success, Toast.LENGTH_SHORT).show();
        host.onAuthenticated();
    }

    /** 退出登录：清本地会话与 WebView Cookie，抽屉会跟着变回「登录/注册」 */
    public void logout() {
        session.clear();
        CookieBridge.clear();
        render();
        Toast.makeText(activity, R.string.auth_logged_out, Toast.LENGTH_SHORT).show();
        host.onSessionChanged();
    }

    private void setBusy(boolean busy) {
        submitButton.setEnabled(!busy);
        submitButton.setText(busy ? R.string.auth_submitting : R.string.auth_login_button);
    }

    private void showError(String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }
}
