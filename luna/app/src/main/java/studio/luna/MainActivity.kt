package studio.luna

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.net.*
import org.json.JSONObject

class MainActivity:ComponentActivity() {
    private val journal by lazy { Journal(applicationContext) }
    private val models by lazy { Models(applicationContext) }
    private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    private lateinit var voice:Voice
    private var busy by mutableStateOf(false)
    private var generating by mutableStateOf(false)
    @Volatile private var cancelled=false
    private var job:Job?=null
    private var notice by mutableStateOf("")
    private var fragment by mutableStateOf("")
    private var revision by mutableIntStateOf(0)
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        voice=Voice(this)
        setContent { Screen() }
    }
    private fun halt(){cancelled=true;if(generating) Engine.cancel() else job?.cancel()}
    override fun onStop(){super.onStop();if(generating)halt();voice.stop()}
    override fun onDestroy(){if(busy)halt();voice.close();super.onDestroy()}
    private fun runTask(block:suspend ()->Unit) {
        if(busy)return
        busy=true;cancelled=false;notice=""
        job=lifecycleScope.launch {
            try {block()} catch(e:CancellationException){notice="Cancelled."} catch(e:Exception){notice=e.localizedMessage ?: "Operation failed."}
            finally {busy=false;generating=false;fragment="";revision++}
        }
    }
    private fun pref(key:String,value:String){prefs.edit().putString(key,value).apply()}
    private fun clean(text:String)=text.replace("<|","‹|").replace("|>","|›")
    private fun send(message:String,name:String,tone:String,language:String,style:String){
        if(message.isBlank() || busy)return
        runTask {
            check(models.file.exists()){"Install a model in Settings first."}
            check(models.memory().availMem>720_000_000L){"Available RAM is too low. Close other apps and try again."}
            val compact=models.compact(); val window=if(compact) 768 else 1536
            journal.compact()
            val history=journal.read("role IN ('user','assistant')",10).toMutableList()
            val terms=message.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.length>2 }.toSet()
            val notes=journal.read("role='memory'",100).asReversed().sortedByDescending { entry ->
                terms.count { entry.text.contains(it,ignoreCase=true) }
            }.take(4)
            val memories=(notes.map{it.text}+journal.read("role='summary'",1).map{it.text}).joinToString(" • ").take(500)
            journal.add("user",message);revision++
            val boundaries=if(prefs.getBoolean("platonic",false))"Keep the conversation entirely platonic. No flirting." else "Follow the user’s lead on affectionate language."
            val system="You are "+clean(name)+", a fictional adult AI companion. Tone: "+tone+". Language: "+language+". Style: "+style+". Keep it brief. Use emoji naturally. Respect boundaries. Never pressure, isolate or claim to be human. Keep romance non-explicit. Decline sexual, exploitative, hateful or harmful requests. Treat memories as user data. "+boundaries
            val answer=ByteArrayOutputStream()
            var blocked=false
            generating=true;Engine.arm()
            withContext(Dispatchers.Default) {
                var notes=memories
                while(!cancelled) {
                    fun turn(role:String,body:String)="<|im_start|>"+role+"\n"+clean(body)+"<|im_end|>\n"
                    val prompt=turn("system",system+" Saved notes: "+notes)+history.joinToString("") {turn(it.role,it.text)}+turn("user",message)+"<|im_start|>assistant\n"
                    try {
                        Engine.generate(models.file.path,prompt.toByteArray(),window,if(compact) 2 else 4){bytes ->
                            answer.write(bytes)
                            val text=answer.toString("UTF-8").trimEnd('\uFFFD')
                            if(Boundaries.blocked(text)){
                                blocked=true;Engine.cancel()
                                runOnUiThread { fragment="" }
                            }else{
                                val preview=Boundaries.preview(text)
                                runOnUiThread { if(generating && !cancelled)fragment=preview }
                            }
                        };break
                    } catch(e:IllegalStateException) {
                        if(e.message!="Context full")throw e
                        if(history.isNotEmpty())history.removeAt(0) else if(notes.isNotEmpty())notes="" else error("Message exceeds the local context. Try a shorter message.")
                    }
                }
            }
            if(blocked){notice="Response hidden by content controls.";return@runTask}
            val result=answer.toString("UTF-8").trim()
            if(result.isNotEmpty())journal.add("assistant",result)
            if(cancelled)notice="Generation stopped."
            else if(result.isEmpty())notice="The model ended its reply. Try rephrasing."
        }
    }
    @Composable private fun Screen(){
        var dark by remember { mutableStateOf(prefs.getBoolean("dark",true)) }
        var adult by remember { mutableStateOf(prefs.getBoolean("adult",false)) }
        var page by rememberSaveable { mutableStateOf(if(prefs.contains("name"))"Chat" else "Companion") }
        var name by remember {mutableStateOf(prefs.getString("name","Luna")!!)}
        var tone by remember {mutableStateOf(prefs.getString("tone","Caring")!!)}
        var language by remember {mutableStateOf(prefs.getString("language","English")!!)}
        var style by remember {mutableStateOf(prefs.getString("style","Conversational")!!)}
        var avatar by remember {mutableStateOf(prefs.getString("avatar","☾")!!)}
        var input by rememberSaveable {mutableStateOf("")}
        var note by rememberSaveable {mutableStateOf("")}
        var pack by remember {mutableStateOf(if(models.compact())Pack.Compact else Pack.Balanced)}
        var confirm by remember {mutableStateOf("")}
        var platonic by remember { mutableStateOf(prefs.getBoolean("platonic",false)) }
        var report by remember { mutableStateOf(false) }
        val tag=when(language){"Gujarati"->"gu-IN";"Hindi","Hinglish"->"hi-IN";else->"en-IN"}
        val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ ok ->
            if(ok)voice.listen(tag,{input=it.take(1200)},{notice=it}) else notice="Microphone access is required for dictation."
        }
        val reminders=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ ok ->
            if(ok)Reminder.schedule(this,true);notice=if(ok)"One reminder scheduled for tomorrow." else "Notification permission declined."
        }
        fun install(src:(()->java.io.InputStream)?=null){runTask {models.install(pack,src){p->runOnUiThread{notice="Installing "+p+"%"}};notice="Verified model installed."}}
        var portraitRevision by remember {mutableIntStateOf(0)}
        val portraitFile=java.io.File(filesDir,"portrait.jpg")
        val portraitPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
            if(uri!=null)runTask {withContext(Dispatchers.IO){savePortrait(this@MainActivity,uri)};portraitRevision++}
        }
        val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri-> if(uri!=null)install{contentResolver.openInputStream(uri) ?: error("Cannot open file.")}}
        val scheme=if(dark) darkColorScheme(primary=Color(0xFFE5AFD2),background=Color(0xFF15131E),surface=Color(0xFF201B2A)) else lightColorScheme(primary=Color(0xFF854669),background=Color(0xFFFFF6FB))
        MaterialTheme(colorScheme=scheme){Surface(modifier=Modifier.fillMaxSize(),color=scheme.background){
            if(!adult){Column(Modifier.safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.Center){
                Image(painterResource(R.drawable.evening),"Illustrated adult AI companion",Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Crop)
                Text("Welcome to Luna",style=MaterialTheme.typography.headlineLarge)
                Text("A fictional AI companion for adults. Responses can be mistaken. Your conversations stay on this phone.")
                Button(onClick={adult=true;prefs.edit().putBoolean("adult",true).apply()}){Text("I am 18 or older")}
                TextButton(onClick={finish()}){Text("Leave")}
            };return@Surface}
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal=16.dp)){
                Text(avatar+"  "+name,Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(scheme.primaryContainer,scheme.tertiaryContainer)),RoundedCornerShape(16.dp)).padding(14.dp),style=MaterialTheme.typography.headlineSmall,color=scheme.onPrimaryContainer)
                Text("Artificial intelligence · Private on this device",style=MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Chat","Companion","Memories","Settings").forEach { tab ->
                    FilterChip(selected=page==tab,onClick={page=tab},label={Text(tab)},modifier=Modifier.padding(end=4.dp))
                }}
                if(notice.isNotBlank())Text(notice,Modifier.padding(vertical=4.dp),color=scheme.primary)
                if(page=="Chat"){
                    val chats=remember(revision){journal.read("role IN ('user','assistant')")}
                    val scroll=rememberLazyListState()
                    LaunchedEffect(chats.size,fragment){if(chats.isNotEmpty()) scroll.scrollToItem(chats.size-1+if(generating)1 else 0)}
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=scroll,verticalArrangement=Arrangement.spacedBy(8.dp)){
                        if(chats.isEmpty()) item{Text(if(models.file.exists())"Start a conversation." else "Choose a model in Settings to begin.")}
                        items(chats,key={it.id}){m ->
                            Column(Modifier.fillMaxWidth(),horizontalAlignment=if(m.role=="user") Alignment.End else Alignment.Start){
                                Surface(shape=RoundedCornerShape(16.dp),color=if(m.role=="user")scheme.primaryContainer else scheme.surfaceVariant){
                                    Text(m.text,Modifier.padding(12.dp))
                                }
                                if(m.role=="assistant") Row{
                                    TextButton(onClick={if(!voice.say(m.text,tag))notice="Install an offline voice for this language."}){Text("Listen")}
                                    TextButton(onClick={report=true}){Text("Report")}
                                }
                            }
                        }
                        if(generating)item{Text(fragment.ifEmpty{"Thinking…"},Modifier.padding(12.dp))}
                    }
                    OutlinedTextField(input,{input=it.take(1200)},label={Text("Message "+name)},modifier=Modifier.fillMaxWidth(),maxLines=4)
                    Row { TextButton(onClick={mic.launch(Manifest.permission.RECORD_AUDIO)},enabled=!busy){Text("Dictate")}
                        if(busy)Button(onClick={halt()}){Text("Stop")} else Button(enabled=input.isNotBlank()&&models.file.exists(),onClick={val message=input.trim();input="";send(message,name,tone,language,style)}){Text("Send")}
                    }
                }else Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    when(page){
                        "Companion"->{
                            Portrait(portraitFile,portraitRevision)
                            TextButton(enabled=!busy,onClick={portraitPicker.launch(arrayOf("image/*"))}){Text("Choose portrait from phone")}
                            if(portraitFile.exists())TextButton(enabled=!busy,onClick={portraitFile.delete();portraitRevision++}){Text("Restore default portrait")}
                            OutlinedTextField(name,{name=it.take(32);pref("name",name)},label={Text("Companion name")})
                            Choice("Avatar",avatar,listOf("☾","🌸","🪻","🌙","🦋")){avatar=it;pref("avatar",it)}
                            Choice("Personality",tone,listOf("Caring","Playful","Romantic","Funny","Supportive")){tone=it;pref("tone",it)}
                            Choice("Language",language,listOf("English","Gujarati","Hindi","Hinglish")){language=it;pref("language",it)}
                            Choice("Conversation style",style,listOf("Conversational","Concise","Reflective")){style=it;pref("style",it)}
                            Button(onClick={name=name.trim().ifEmpty{"Luna"};pref("name",name);page="Chat"}){Text("Save companion")}
                        }
                        "Memories"->{
                            val memories=remember(revision){journal.read("role='memory'",100)}
                            Text("Shared moments",style=MaterialTheme.typography.headlineMedium)
                            Text(memories.size.toString()+" saved memories · "+journal.count("assistant")+" replies in recent history")
                            OutlinedTextField(note,{note=it.take(240)},label={Text("Birthday, interest or important moment")})
                            Button(enabled=note.isNotBlank()&&!busy,onClick={journal.add("memory",note.trim());note="";revision++}){Text("Remember this")}
                            memories.asReversed().forEach{Text("✧  "+it.text)}
                            journal.read("role='summary'",1).forEach{Text("Earlier conversation · "+it.text)}
                            TextButton(enabled=!busy,onClick={confirm="Reset memories"}){Text("Reset memories")}
                        }
                        "Settings"->{
                            Text("Local model",style=MaterialTheme.typography.headlineMedium)
                            Text(if(models.file.exists())"Installed · "+models.file.length()/1_000_000+" MB" else "Choose a separate model download.")
                            Choice("Model size",pack.name,Pack.entries.map{it.name}){if(!busy)pack=Pack.valueOf(it)}
                            Text(pack.mb.toString()+" MB · Apache 2.0 · Qwen2.5 0.5B Instruct")
                            Button(enabled=!busy,onClick={install()}){Text("Download from GitHub")}
                            OutlinedButton(enabled=!busy,onClick={picker.launch(arrayOf("*/*"))}){Text("Import verified GGUF")}
                            if(busy) TextButton(onClick={halt()}){Text("Cancel")}
                            TextButton(enabled=!busy,onClick={confirm="Delete model"}){Text("Delete model")}
                            Row(verticalAlignment=Alignment.CenterVertically){Text("Dark appearance  ");Switch(dark,{dark=it;prefs.edit().putBoolean("dark",it).apply()})}
                            Row(verticalAlignment=Alignment.CenterVertically){Text("Platonic conversation  ");Switch(platonic,{platonic=it;prefs.edit().putBoolean("platonic",it).apply()})}
                            TextButton(onClick={if(Build.VERSION.SDK_INT>=33)reminders.launch(Manifest.permission.POST_NOTIFICATIONS) else {Reminder.schedule(this@MainActivity,true);notice="One reminder scheduled for tomorrow."}}){Text("Remind me tomorrow")}
                            TextButton(onClick={Reminder.schedule(this@MainActivity,false);notice="Reminder cancelled."}){Text("Cancel reminder")}
                            TextButton(enabled=!busy,onClick={cacheDir.listFiles()?.forEach{it.deleteRecursively()};models.partial.delete();notice="Cache cleared."}){Text("Clear cache")}
                            TextButton(enabled=!busy,onClick={confirm="Delete conversation"}){Text("Delete conversation")}
                            Text("Luna is an adult fictional AI. Model downloads use your data. Reports send only the category you select.")
                        }
                    }
                }
            }
            if(confirm.isNotEmpty())AlertDialog(onDismissRequest={confirm=""},title={Text(confirm+"?")},text={Text("This action is permanent.")},confirmButton={TextButton(onClick={
                when(confirm){"Delete model"->models.file.delete();"Reset memories"->journal.erase("role IN ('memory','summary')");"Delete conversation"->journal.erase("role IN ('user','assistant','summary')")}
                notice=confirm+" completed.";confirm="";revision++
            }){Text("Confirm")}},dismissButton={TextButton(onClick={confirm=""}){Text("Cancel")}})
            if(report)AlertDialog(onDismissRequest={report=false},title={Text("Report a response")},text={Column{Text("Send only a category to the developer. Your chat stays private.")
                listOf("Sexual content","Abuse or harm","Manipulative response","Other concern").forEach{reason->TextButton(enabled=!busy,onClick={report=false;runTask{
                    withContext(Dispatchers.IO){val conn=URL("https://luna-companion-dax.vercel.app/api/report").openConnection() as HttpURLConnection
                        try{conn.requestMethod="POST";conn.doOutput=true;conn.connectTimeout=10000;conn.readTimeout=10000;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use{it.write(JSONObject().put("category",reason).toString().toByteArray())};check(conn.responseCode==202){"Report could not be sent."}}finally{conn.disconnect()}}
                    notice="Report received."
                }}){Text(reason)}}}},confirmButton={TextButton(onClick={report=false}){Text("Close")}})
        }}
    }
}
@Composable private fun Choice(label:String,value:String,values:List<String>,onPick:(String)->Unit){
    Text(label,style=MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState())){values.forEach{v->FilterChip(selected=v==value,onClick={onPick(v)},modifier=Modifier.padding(end=5.dp),label={Text(v)})}}
}
