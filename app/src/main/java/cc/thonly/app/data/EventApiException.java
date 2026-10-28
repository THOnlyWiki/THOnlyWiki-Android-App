package cc.thonly.app.data;

import java.io.IOException;

/** 数据层统一抛出的异常，UI 层只需要关心这一种。 */
public class EventApiException extends IOException {

    public EventApiException(String message) {
        super(message);
    }

    public EventApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
