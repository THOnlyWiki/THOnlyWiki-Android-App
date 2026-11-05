package cc.thonly.app.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationQuizTest {
    @Test void enabledChallengePostsItsOwnIndexAndTrimmedAnswer() throws Exception {
        RegistrationQuiz quiz = new RegistrationQuiz(true, 7, "本站名称？");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("username", "原用户名");
        quiz.appendTo(form, " 东方维基 ");
        assertEquals("7", form.get("registerQuizIndex"));
        assertEquals("东方维基", form.get("registerAnswer"));
        assertEquals("原用户名", form.get("username"));
    }

    @Test void disabledChallengePostsNoQuizFields() throws Exception {
        RegistrationQuiz quiz = new RegistrationQuiz(false, 7, "旧题目");
        Map<String, String> form = new LinkedHashMap<>();
        quiz.appendTo(form, "旧答案");
        assertTrue(form.isEmpty());
        assertFalse(quiz.required());
    }

    @Test void existingServerFormUsesTheHiddenIndexAndItsQuestion() throws Exception {
        String html = "<input name='registerQuizIndex' value='999'>"
                + "<form id=\"wiki-register-form\"><label for=\"wpRegisterAnswer\">谁是 &lt;主角&gt;？</label>"
                + "<input value='2' type='hidden' name='registerQuizIndex'/>"
                + "<input name='registerAnswer' id='wpRegisterAnswer' required/></form>";
        RegistrationQuiz quiz = RegistrationQuiz.fromRegistrationHtml(html);
        assertTrue(quiz.required());
        assertEquals(2, quiz.index());
        assertEquals("谁是 &lt;主角&gt;？", quiz.question());
        assertTrue(quiz.htmlQuestion());
    }

    @Test void formWithoutQuizAllowsNormalRegistration() throws Exception {
        assertFalse(RegistrationQuiz.fromRegistrationHtml(
                "<FORM class='wiki-form' ID='wiki-register-form'><input name='username'></FORM>").required());
    }

    @Test void malformedAndBlockedPagesCannotDisableVerification() {
        for (String html : new String[]{"<h1>403 Forbidden</h1>", "<html>登录后访问</html>",
                "<form id='wiki-register-form'><input name='registerQuizIndex' value='0'></form>",
                "<form id='wiki-register-form'><input name='registerAnswer'></form>"}) {
            assertThrows(IOException.class, () -> RegistrationQuiz.fromRegistrationHtml(html));
        }
    }

    @Test void malformedChallengeIndicesAndMissingQuestionsAreRejected() {
        for (String index : new String[]{"-1", "abc", "9999999999999999999"}) {
            String html = "<form id='wiki-register-form'><label for='wpRegisterAnswer'>问题</label>"
                    + "<input name='registerQuizIndex' value='" + index + "'><input name='registerAnswer'></form>";
            assertThrows(IOException.class, () -> RegistrationQuiz.fromRegistrationHtml(html));
        }
        assertThrows(IOException.class, () -> new RegistrationQuiz(true, 0, " "));
    }

    @Test void refreshedChallengeReplacesPreviousIndexOnSubmission() throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        new RegistrationQuiz(true, 0, "旧题目").appendTo(form, "旧答案");
        new RegistrationQuiz(true, 3, "新题目").appendTo(form, "新答案");
        assertEquals(Map.of("registerQuizIndex", "3", "registerAnswer", "新答案"), form);
    }
}
