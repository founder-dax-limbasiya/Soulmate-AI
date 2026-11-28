package studio.luna

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSmoke {
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
