package dev.demos.graphing.plot

/** Direct native port of desmosTranslator.ts preprocessLatex and its balanced readers. */
internal object GeoGebraLatex {
    private data class Group(val content: String,val end: Int)
    private fun group(text: String,start: Int,open: Char='{',close: Char='}'): Group? {
        var at=start
        while(text.getOrNull(at)==' ')at++
        if(text.getOrNull(at)!=open)return null
        val begin=++at;var depth=1
        while(at<text.length){
            if(text[at]==open)depth++
            if(text[at]==close){depth--;if(depth==0)return Group(text.substring(begin,at),at+1)}
            at++
        }
        return null
    }

    fun preprocess(raw: String): String {
        var s=raw.trim()
        for((from,to)in listOf("π" to "pi","θ" to "theta","≤" to "<=","≥" to ">=","≠" to "!=","·" to "*","×" to "*","÷" to "/","−" to "-","²" to "^2","³" to "^3","√" to "sqrt"))s=s.replace(from,to)
        s=s.replace(Regex("\\\\(?:left|right)\\s*\\|"),"|").replace(Regex("\\\\(?:left|right)\\s*"),"")
        for((from,to)in mapOf("cdot" to "*","times" to "*","div" to "/"))s=s.replace("\\$from",to)
        val commands=mapOf("le" to "<=","leq" to "<=","ge" to ">=","geq" to ">=","ne" to "!=","neq" to "!=","pi" to "pi","theta" to "theta")
        for((from,to)in commands)s=s.replace(Regex("\\\\$from\\b"),to)
        for(name in "arcsin arccos arctan sinh cosh tanh sin cos tan csc sec cot ln log exp".split(' '))s=s.replace(Regex("\\\\$name\\b"),name)
        var safety=0
        while("\\frac" in s && safety++<25) {
            val at=s.indexOf("\\frac");val numerator=group(s,at+5)?:break;val denominator=group(s,numerator.end)?:break
            if(numerator.content.trim()=="d" && Regex("d[a-zA-Z]").matches(denominator.content.trim())) {
                val rest=s.substring(denominator.end).trim();val argument=group(rest,0,'(',')')
                if(argument!=null){s=s.substring(0,at)+"derivative(${argument.content}, ${denominator.content.trim()[1]})"+rest.substring(argument.end);continue}
            }
            s=s.substring(0,at)+"((${numerator.content})/(${denominator.content}))"+s.substring(denominator.end)
        }
        safety=0
        while("\\sqrt" in s && safety++<25) {
            val at=s.indexOf("\\sqrt");var cursor=at+5
            while(s.getOrNull(cursor)==' ')cursor++
            var root: String?=null
            if(s.getOrNull(cursor)=='['){val end=s.indexOf(']',cursor+1);if(end>=0){root=s.substring(cursor+1,end);cursor=end+1}}
            val argument=group(s,cursor)?:break
            s=s.substring(0,at)+(if(root==null)"sqrt(${argument.content})" else "((${argument.content})^(1/($root)))")+s.substring(argument.end)
        }
        for(op in listOf("sum","prod")) {
            safety=0
            while("\\$op" in s && safety++<10) {
                val at=s.indexOf("\\$op");var cursor=at+op.length+1
                while(s.getOrNull(cursor)==' ')cursor++
                if(s.getOrNull(cursor)!='_')break
                val lower=group(s,cursor+1)?:break;cursor=lower.end
                while(s.getOrNull(cursor)==' ')cursor++
                if(s.getOrNull(cursor)!='^')break
                val upper=group(s,cursor+1)?:break
                s=s.substring(0,at)+"$op(${lower.content}, ${upper.content}, ${s.substring(upper.end).trim()})"
            }
        }
        safety=0
        while("\\int" in s && safety++<10) {
            val at=s.indexOf("\\int");var cursor=at+4
            while(s.getOrNull(cursor)==' ')cursor++
            var lower="";var upper=""
            if(s.getOrNull(cursor)=='_')group(s,cursor+1)?.let {lower=it.content;cursor=it.end;while(s.getOrNull(cursor)==' ')cursor++}
            if(s.getOrNull(cursor)=='^')group(s,cursor+1)?.let {upper=it.content;cursor=it.end}
            val differential=Regex("^(.*?)\\s*d([a-zA-Z])\\s*$").matchEntire(s.substring(cursor).trim())?:break
            val body=differential.groupValues[1].trim();val variable=differential.groupValues[2]
            s=s.substring(0,at)+"integral($body, $variable${if(lower.isNotEmpty() && upper.isNotEmpty())", $lower, $upper" else ""})"
        }
        if('|' in s) {
            var open=false
            val replaced=buildString {for(c in s)if(c=='|'){append(if(open)")" else "abs(");open=!open}else append(c)}
            if(!open)s=replaced
        }
        return s.trim()
    }
}
