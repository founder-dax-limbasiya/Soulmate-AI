package studio.luna

import android.content.Intent
import androidx.test.uiautomator.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSmoke {
    @Test fun sendFromChatScreenInAirplaneMode() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        val device=UiDevice.getInstance(instrumentation)
        ctx.startActivity(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun button(text:String) = requireNotNull(device.wait(Until.findObject(By.text(text)),15000)) { "Missing control: "+text }
        button("I am 18 or older").click()
        button("Chat").click()
        val editor=requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000))
        editor.text="Hello! Name a flower."
        button("Send").click()
        assertTrue("A generated assistant bubble should offer read-aloud",device.wait(Until.hasObject(By.text("Listen")),180000))
        val journal=Journal(ctx)
        try {assertTrue(journal.read("role='assistant'",1).first().text.isNotBlank())}
        finally {journal.close()}
    }
    @Test fun localGenerationAfterNetworkDisconnect() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(ctx.filesDir, "companion.gguf")
        assertTrue("Expected pre-verified compact model", model.length() == 428730208L)
        val question = "<|im_start|>system\nYou are a friendly AI companion. Reply briefly in English.<|im_end|>\n" +
            "<|im_start|>user\nSay hello and name a flower.<|im_end|>\n<|im_start|>assistant\n"
        val answer = ByteArrayOutputStream()
        Engine.arm()
        Engine.generate(model.path, question.toByteArray(), 768, 2) { bytes ->
            answer.write(bytes)
            if (answer.size() >= 80) Engine.cancel()
        }
        assertTrue("Expected real generated text", answer.toString("UTF-8").any(Char::isLetter))
        val journal = Journal(ctx)
        try {
            journal.add("memory", "Birthday: 7 April · જન્મદિવસ")
            assertTrue(journal.read("role='memory'").any { it.text.contains("જન્મદિવસ") })
            journal.erase("role='memory'")
            assertEquals(0L,journal.count("memory"))
        } finally { journal.close() }
    }
}
