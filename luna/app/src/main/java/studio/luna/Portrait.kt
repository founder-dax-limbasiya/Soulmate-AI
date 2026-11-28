package studio.luna

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.io.File

fun savePortrait(context:Context, uri:Uri) {
    val source=ImageDecoder.createSource(context.contentResolver,uri)
    val bitmap=ImageDecoder.decodeBitmap(source){decoder,info,_ ->
        require(info.size.width>0 && info.size.height>0) { "Invalid image." }
        val ratio=minOf(1.0,640.0/maxOf(info.size.width,info.size.height))
        decoder.setTargetSize(maxOf(1,(info.size.width*ratio).toInt()),maxOf(1,(info.size.height*ratio).toInt()))
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val tmp=File(context.filesDir,"portrait.part")
    try {
        tmp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)) }
        android.system.Os.rename(tmp.path,File(context.filesDir,"portrait.jpg").path)
    } finally {tmp.delete();bitmap.recycle()}
}
@Composable fun Portrait(file:File,version:Int) {
    val image=remember(version){if(file.exists())BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null}
    val size=Modifier.fillMaxWidth().height(140.dp)
    if(image==null)Image(painterResource(R.drawable.evening),"Companion portrait",size,contentScale=ContentScale.Crop)
    else Image(image,"Custom companion portrait",size,contentScale=ContentScale.Crop)
}
