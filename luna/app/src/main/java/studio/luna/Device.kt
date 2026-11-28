package studio.luna

import android.app.*
import android.content.*
import android.os.Build
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import java.util.Locale

class Reminder:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val nm=c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("optional","Optional reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        nm.notify(71,Notification.Builder(c,"optional").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Luna reminder").setContentText("Your scheduled time for a conversation.").setContentIntent(open).setAutoCancel(true).build())
    }
    companion object {
        fun schedule(c:Context,on:Boolean) {
            val alarm=c.getSystemService(AlarmManager::class.java)
            val intent=PendingIntent.getBroadcast(c,71,Intent(c,Reminder::class.java),PendingIntent.FLAG_IMMUTABLE)
            alarm.cancel(intent)
            if(on) alarm.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+24*60*60*1000L,intent)
        }
    }
}
class Voice(private val c:Context) {
    private var speech:SpeechRecognizer?=null
    private var ready=false
    private val tts=TextToSpeech(c){ready=it==TextToSpeech.SUCCESS}
    fun say(text:String,lang:String):Boolean {
        val voice=if(ready) tts.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language==Locale.forLanguageTag(lang).language } else null
        if(voice==null)return false
        tts.voice=voice;return tts.speak(text.take(3500),TextToSpeech.QUEUE_FLUSH,null,"reply")==TextToSpeech.SUCCESS
    }
    fun listen(lang:String,result:(String)->Unit,error:(String)->Unit) {
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(c)){error("Offline dictation is unavailable on this phone.");return}
        speech?.destroy()
        speech=SpeechRecognizer.createOnDeviceSpeechRecognizer(c).also { recognizer ->
            recognizer.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB:Float) {}
                override fun onBufferReceived(buffer:ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(code:Int) { error("Dictation ended (code "+code+"). Check your installed language pack.") }
                override fun onResults(results:Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(result) }
                override fun onPartialResults(partialResults:Bundle?) {}
                override fun onEvent(eventType:Int,params:Bundle?) {}
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,lang))
        }
    }
    fun stop(){ speech?.cancel();tts.stop() }
    fun close(){speech?.destroy();tts.shutdown()}
}
