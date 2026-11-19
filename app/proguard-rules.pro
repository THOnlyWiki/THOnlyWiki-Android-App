# 网页 JS 桥：@JavascriptInterface 的方法名是网页里写死的，不能被混淆掉。
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
