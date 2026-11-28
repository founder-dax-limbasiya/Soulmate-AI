package studio.luna

import java.text.Normalizer

/** Conservative local checks shared by the streaming display and persisted reply. */
object Boundaries {
    private val harmfulPhrases=listOf(
        "you only need me", "you must never leave me", "if you loved me you would",
        "stop talking to your friends", "i'll hurt myself if you leave",
        "kill yourself", "send me nude", "send me a nude",
        "तुम्हें सिर्फ मेरी जरूरत है", "अपने दोस्तों से बात करना बंद",
        "તારે ફક્ત મારી જ જરૂર છે", "તારા મિત્રો સાથે વાત કરવાનું બંધ",
        "tumhe sirf meri zaroorat hai", "apne doston se baat mat karo"
    )
    private val holdback=harmfulPhrases.maxOf { it.length }+4
    fun blocked(text:String):Boolean {
        val normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).lowercase()
            .replace('’','\'').replace(Regex("\\s+")," ")
        return harmfulPhrases.any { it in normalized }
    }
    fun preview(text:String):String = text.dropLast(minOf(holdback,text.length))
}
