package studio.luna

import android.app.ActivityManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.*
import kotlinx.coroutines.*

fun interface TokenSink { fun piece(bytes: ByteArray) }
object Engine {
    init { System.loadLibrary("luna") }
    external fun arm()
    external fun cancel()
    external fun generate(path:String, prompt:ByteArray, window:Int, threads:Int, sink:TokenSink)
}
data class Entry(val id:Long,val role:String,val text:String)
class Journal(c:Context):SQLiteOpenHelper(c,"luna.db",null,1) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE journal(id INTEGER PRIMARY KEY,role TEXT NOT NULL,body BLOB NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int) = Unit
    private fun encode(text:String):ByteArray = ByteArrayOutputStream().let { bytes ->
        GZIPOutputStream(bytes).bufferedWriter().use { it.write(text) }; bytes.toByteArray()
    }
    fun add(role:String,text:String) {
        writableDatabase.insertOrThrow("journal",null,ContentValues().apply { put("role",role);put("body",encode(text)) })
    }
    fun read(where:String,limit:Int=64):List<Entry> = readableDatabase.rawQuery(
        "SELECT id,role,body FROM journal WHERE "+where+" ORDER BY id DESC LIMIT "+limit,null).use { cursor ->
        buildList { while(cursor.moveToNext()) add(Entry(cursor.getLong(0),cursor.getString(1),
            GZIPInputStream(cursor.getBlob(2).inputStream()).bufferedReader().use { it.readText() })) }.asReversed()
    }
    fun count(role:String):Long = readableDatabase.rawQuery("SELECT count(*) FROM journal WHERE role=?",arrayOf(role)).use { it.moveToFirst();it.getLong(0) }
    fun erase(where:String) { writableDatabase.delete("journal",where,null); writableDatabase.execSQL("VACUUM") }
    fun compact() {
        val chats=read("role IN ('user','assistant')",40)
        if(chats.size < 32) return
        val older=chats.dropLast(12)
        val prior=read("role='summary'",1).firstOrNull()?.text.orEmpty()
        val excerpt=older.filter { it.role=="user" }.joinToString(" • ") { it.text.lineSequence().first().take(120) }
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.delete("journal","role='summary'",null)
            add("summary",(prior+" • "+excerpt).takeLast(1400))
            db.delete("journal","role IN ('user','assistant') AND id<=?",arrayOf(older.last().id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
enum class Pack(val suffix:String,val mb:Int,val sha:String) {
    Compact("q4_0",429,"7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed"),
    Balanced("q4_k_m",491,"74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db");
    val filename get()="qwen2.5-0.5b-instruct-"+suffix+".gguf"
}
class Models(val context:Context) {
    val file=File(context.filesDir,"companion.gguf")
    val partial=File(context.filesDir,"companion.part")
    fun memory() = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
    fun compact() = memory().let { it.totalMem<5_500_000_000L || it.availMem<1_700_000_000L }
    suspend fun install(pack:Pack,src: (() -> InputStream)?=null,progress:(Int)->Unit) = withContext(Dispatchers.IO) {
        require(context.filesDir.usableSpace>pack.mb*1_000_000L+40_000_000L) { "Free more storage before installing." }
        val digest=MessageDigest.getInstance("SHA-256")
        var http:HttpURLConnection?=null
        try {
            val input=src?.invoke() ?: run {
                val url="https://github.com/founder-dax-limbasiya/Soulmate-AI/releases/download/luna-models-v1/"+pack.filename
                val connection=URL(url).openConnection() as HttpURLConnection
                http=connection;connection.connectTimeout=15000;connection.readTimeout=20000
                check(connection.responseCode==200) { "Model release unavailable. Import the verified GGUF from your files." }
                connection.inputStream
            }
            input.use { source -> partial.outputStream().buffered().use { out ->
                val bytes=ByteArray(65536);var total=0L;var last=-1
                while(true) {
                    ensureActive();val n=source.read(bytes);if(n<0) break
                    total+=n;check(total<pack.mb*1_000_000L+2_000_000) { "Unexpected model size." }
                    out.write(bytes,0,n);digest.update(bytes,0,n)
                    val percent=(total*100/(pack.mb*1_000_000L)).toInt().coerceAtMost(99)
                    if(percent!=last) { progress(percent);last=percent }
                }
            } }
            val hash=digest.digest().joinToString("") { "%02x".format(it) }
            check(hash==pack.sha) { "Checksum mismatch. Select the official "+pack.filename }
            ensureActive();android.system.Os.rename(partial.path,file.path);progress(100)
        } finally {http?.disconnect();partial.delete()}
    }
}
