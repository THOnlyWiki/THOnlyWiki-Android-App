package cc.thonly.app.ui;

/** 登录/注册成功后需要跳页面、以及两个表单之间的互相跳转。 */
public interface AuthHost {

    /** 登录或注册成功 */
    void onAuthenticated();

    /** 登录态变化但不需要跳页（比如退出登录），让主站刷新一下 */
    void onSessionChanged();

    void openLoginPage();

    void openRegisterPage();
}
