package dev.demos.graphing.plot

import dev.demos.graphing.math.MathSyntax
import org.geogebra.common.plugin.GgbAPI
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Native translation of geographer/src/engine/desmosTranslator.ts; GeoGebra evaluates the result. */
internal object GeoGebraExpressions {
    @Serializable internal data class Translation(val command: String, val kind: String, val symbol: String?=null,
        val numeric: Double?=null, val undefined: Set<String> = emptySet())
    private enum class K { Number, Name, Op, Left, Right, Brace, EndBrace, List, EndList, Comma, Colon, Range }
    private data class T(val k: K,val v: String)
    private val builtins=("table sin cos tan csc sec cot asin acos atan arcsin arccos arctan sinh cosh tanh asinh acosh atanh ln log log10 log2 exp sqrt cbrt abs floor ceil round sign sgn mod min max mean median stdev total length distance nCr nPr sum prod integral derivative If Sequence Curve Derivative Integral Sum Product").split(' ').toSet()
    private val reserved=setOf("x","y","z","r","t","theta","pi","e")
    private val comparisons=setOf("<",">","<=",">=","!=")
    private val aliases=mapOf("arcsin" to "asin","arccos" to "acos","arctan" to "atan","log" to "log10","theta" to "θ","pi" to "π","sgn" to "sign")
    private fun tokens(raw: String,symbols: Set<String>,functions: Set<String>): List<T> {
        val rawTokens=ArrayList<T>();var at=0
        val specials=(builtins+listOf("theta","pi")+symbols+functions).sortedByDescending {it.length}
        while(at<raw.length) {
            val c=raw[at]
            if(c.isWhitespace()){at++;continue}
            val two=raw.substring(at,minOf(at+2,raw.length))
            if(two in comparisons || two=="..") {rawTokens+=T(if(two=="..")K.Range else K.Op,two);at+=2;continue}
            if(c.isDigit() || c=='.' && raw.getOrNull(at+1)?.isDigit()==true) {
                val start=at;var dot=false
                while(at<raw.length) {
                    if(raw[at].isDigit())at++
                    else if(raw[at]=='.' && !dot && raw.getOrNull(at+1)!='.'){dot=true;at++}
                    else break
                }
                rawTokens+=T(K.Number,raw.substring(start,at));continue
            }
            if(c in 'a'..'z' || c in 'A'..'Z') {
                val start=at++
                while(at<raw.length && (raw[at].isLetterOrDigit() || raw[at]=='_'))at++
                var word=raw.substring(start,at)
                val diff=Regex("d[a-zA-Z]").matches(word) && rawTokens.lastOrNull()?.v=="/" && rawTokens.getOrNull(rawTokens.size-2)?.v=="d"
                if(!diff && word.length>1 && '_' !in word && word !in builtins && word !in reserved && word !in symbols && word !in functions && word !in setOf("true","false","d")) {
                    while(word.isNotEmpty()) {
                        val match=specials.firstOrNull {it.length>1 && word.startsWith(it)}
                            ?:Regex("^[a-zA-Z][0-9]+").find(word)?.value?:word.take(1)
                        rawTokens+=T(K.Name,match);word=word.drop(match.length)
                    }
                } else rawTokens+=T(K.Name,word)
                continue
            }
            val kind=when(c){'('->K.Left;')'->K.Right;'{'->K.Brace;'}'->K.EndBrace;'['->K.List;']'->K.EndList;','->K.Comma;':'->K.Colon;in "+-*/^=<>'"->K.Op;else->error("Unexpected character $c")}
            rawTokens+=T(kind,c.toString());at++
        }
        val out=ArrayList<T>()
        for(t in rawTokens) {
            val p=out.lastOrNull()
            if(p!=null && (p.k in setOf(K.Number,K.Right,K.EndList,K.Name) || p.v=="'") && t.k in setOf(K.Number,K.Name,K.Left,K.List)) {
                val call=p.k==K.Name && t.k==K.Left && (p.v !in reserved || p.v in builtins || p.v in functions || Regex("d[a-zA-Z]").matches(p.v))
                val prime=p.v=="'" && t.k==K.Left
                val index=p.k==K.Name && t.k==K.List
                if(!call && !prime && !index)out+=T(K.Op,"*")
            }
            out+=t
        }
        return out
    }
    private fun split(ts: List<T>,predicate: (T)->Boolean): List<List<T>> {
        val result=ArrayList<List<T>>();var start=0;var depth=0
        for(i in ts.indices) {
            if(ts[i].k in setOf(K.Left,K.List,K.Brace))depth++
            if(ts[i].k in setOf(K.Right,K.EndList,K.EndBrace))depth--
            if(depth==0 && predicate(ts[i])){result+=ts.subList(start,i);start=i+1}
        }
        result+=ts.subList(start,ts.size);return result
    }
    private fun matching(ts: List<T>,start: Int): Int {
        val left=ts[start].k;val right=when(left){K.Left->K.Right;K.Brace->K.EndBrace;K.List->K.EndList;else->error("Expected bracket")}
        var depth=0
        for(i in start until ts.size){if(ts[i].k==left)depth++;if(ts[i].k==right){depth--;if(depth==0)return i}}
        error("Complete the expression")
    }
    private fun chain(ts: List<T>): List<T> {
        val ops=ArrayList<Int>();var depth=0
        for(i in ts.indices){val t=ts[i];if(t.k in setOf(K.Left,K.List,K.Brace))depth++;if(t.k in setOf(K.Right,K.EndList,K.EndBrace))depth--;if(depth==0 && t.v in comparisons)ops+=i}
        if(ops.size<2)return ts
        val segments=ArrayList<List<T>>();var start=0
        for(i in ops){segments+=ts.subList(start,i);start=i+1};segments+=ts.subList(start,ts.size)
        return buildList {for(i in ops.indices){if(i>0)add(T(K.Name,"&&"));add(T(K.Left,"("));addAll(segments[i]);add(ts[ops[i]]);addAll(segments[i+1]);add(T(K.Right,")"))}}
    }
    private fun compile(ts: List<T>): String=buildString {
        var i=0
        while(i<ts.size) {
            val t=ts[i]
            if(t.k==K.Name && t.v=="d" && ts.getOrNull(i+1)?.v=="/" && Regex("d[a-zA-Z]").matches(ts.getOrNull(i+2)?.v?:"") && ts.getOrNull(i+3)?.k==K.Left) {
                val close=matching(ts,i+3);append("Derivative(${compile(ts.subList(i+4,close))}, ${ts[i+2].v[1]})");i=close+1;continue
            }
            if(t.k==K.Name && t.v in setOf("sum","prod","integral","derivative") && ts.getOrNull(i+1)?.k==K.Left) {
                val close=matching(ts,i+1);val args=split(ts.subList(i+2,close)){it.k==K.Comma}
                val compiled=when(t.v) {
                    "sum","prod" -> {
                        val assignment=split(args[0]){it.v=="="}
                        if(args.size>=3 && assignment.size==2) "${if(t.v=="sum")"Sum" else "Product"}(Sequence(${compile(args.drop(2).flatten())}, ${assignment[0][0].v}, ${compile(assignment[1])}, ${compile(args[1])}))" else null
                    }
                    "integral" -> if(args.size>=4) "Integral(${compile(args[0])}, ${compile(args[2])}, ${compile(args[3])})" else "Integral(${compile(args[0])})"
                    else -> "Derivative(${compile(args[0])}, ${args.getOrNull(1)?.let(::compile)?:"x"})"
                }
                if(compiled!=null){append(compiled);i=close+1;continue}
            }
            if(t.k==K.Brace) {
                val close=matching(ts,i);var acc=""
                for(branch in split(ts.subList(i+1,close)){it.k==K.Comma}.asReversed()) {
                    val pair=split(branch){it.k==K.Colon}
                    acc=if(pair.size==1)compile(pair[0]) else "If(${compile(chain(pair[0]))}, ${compile(pair[1])}${if(acc.isEmpty())"" else ", $acc"})"
                }
                append(acc.ifEmpty {"0"});i=close+1;continue
            }
            if(t.k==K.List) {
                val close=matching(ts,i);val inside=ts.subList(i+1,close);val range=split(inside){it.k==K.Range}
                if(range.size==2)append("Sequence(_k, _k, ${compile(range[0])}, ${compile(range[1])})")
                else append(split(inside){it.k==K.Comma}.joinToString(", ","{","}",transform=::compile))
                i=close+1;continue
            }
            append(when {t.v=="&&"->" && ";t.k==K.Name->aliases[t.v]?:t.v;t.v in comparisons->" ${t.v} ";else->t.v});i++
        }
    }
    fun translate(raw: String,label: String,symbols: Set<String>,functions: Set<String>,settings: PlotSettings): Translation {
        val normalized=GeoGebraLatex.preprocess(raw).replace(Regex("\\bdiff\\("),"derivative(")
        if(normalized.isBlank())return Translation("","empty")
        val ts=tokens(normalized,symbols,functions)
        for((left,right)in listOf(K.Left to K.Right,K.List to K.EndList,K.Brace to K.EndBrace)) {
            var depth=0
            for(token in ts){if(token.k==left)depth++;if(token.k==right)depth--;require(depth>=0){"Unmatched closing delimiter"}}
            require(depth==0){"Missing closing ${if(left==K.Left)"parenthesis" else if(left==K.List)"bracket" else "brace"}"}
        }
        // Demos tables expand to individual GeoPoints, matching the reference table adapter.
        val table=when {
            ts.firstOrNull()?.v=="table" && ts.getOrNull(1)?.k==K.Left -> split(ts.subList(2,matching(ts,1))){it.k==K.Comma}
            ts.firstOrNull()?.k==K.Left && ts.any {it.k==K.List} -> split(ts.subList(1,matching(ts,0))){it.k==K.Comma}
            else -> null
        }
        if(table!=null && table.size in 2..3) {
            val columns=table.map {column->if(column.firstOrNull()?.k==K.List)split(column.subList(1,column.lastIndex)){it.k==K.Comma} else listOf(column)}
            val count=columns.maxOf {it.size}
            require(columns.all {it.size==1 || it.size==count}){"Table column lengths differ"}
            return Translation((0 until count).joinToString("\n"){row->"${label}_$row = (${columns.joinToString(", "){compile(it[if(it.size==1)0 else row])}})"},"table")
        }
        val main=ArrayList<T>();val restrictions=ArrayList<List<T>>();var i=0
        while(i<ts.size) {
            if(ts[i].k==K.Brace){val close=matching(ts,i);val inside=ts.subList(i+1,close);if(inside.any {it.k==K.Colon})main+=ts.subList(i,close+1) else restrictions+=inside;i=close+1}
            else main+=ts[i++]
        }
        require(main.isNotEmpty()){"Expression cannot consist solely of a condition"}
        val bound=HashSet<String>();val eq=split(main){it.v=="="};require(eq.size<=2){"Multiple equal signs"}
        val lhs=if(eq.size==2)eq[0] else emptyList();val rhs=if(eq.size==2)eq[1] else main
        val function=lhs.size>=4 && lhs[0].k==K.Name && lhs[1].k==K.Left && lhs.last().k==K.Right
        if(function)bound+=lhs.subList(2,lhs.size-1).filter {it.k==K.Name}.map {it.v}
        for(j in ts.indices) {
            if(ts[j].v in setOf("sum","prod") && ts.getOrNull(j+1)?.k==K.Left)ts.getOrNull(j+2)?.let {bound+=it.v}
            if(ts[j].v=="integral" && ts.getOrNull(j+1)?.k==K.Left){val args=split(ts.subList(j+2,matching(ts,j+1))){it.k==K.Comma};args.getOrNull(1)?.firstOrNull()?.let {bound+=it.v}}
            if(ts[j].v=="d" && ts.getOrNull(j+1)?.v=="/"){bound+="d";ts.getOrNull(j+2)?.let {bound+=it.v}}
        }
        val undefined=ts.withIndex().filter {(j,t)->t.k==K.Name && t.v !in builtins && t.v !in reserved && t.v !in symbols && t.v !in bound && t.v !in setOf("true","false") && ts.getOrNull(j+1)?.k!=K.Left}.map {it.value.v}.toMutableSet()
        val ids=main.filter {it.k==K.Name}.map {it.v}.toSet()
        val restriction=restrictions.joinToString(" && "){compile(chain(it))}
        fun body(value: List<T>)=compile(value).let {if(restriction.isEmpty())it else "If($restriction, $it)"}
        fun result(command: String,kind: String,symbol: String?=null,numeric: Double?=null)=Translation(command,kind,symbol,numeric,undefined)
        fun bounds(polar: Boolean): Pair<String,String> {
            for(r in restrictions) {
                val match=Regex("^(.+?)(<=|<)(t|theta)(<=|<)(.+)$").matchEntire(r.joinToString(""){it.v})?:continue
                return compile(tokens(match.groupValues[1],symbols,functions)) to compile(tokens(match.groupValues[5],symbols,functions))
            }
            fun number(value: Double)=if(value==value.toLong().toDouble())value.toLong().toString() else value.toString()
            return if(polar)"0" to "2*π" else number(settings.tMin) to number(settings.tMax)
        }
        if(split(main){it.v in comparisons}.size>1)return result("$label: ${compile(chain(main))}"+(if(restriction.isEmpty())"" else " && ($restriction)"),if("z" in ids)"inequality_3d" else "inequality_2d")
        if(eq.size==2) {
            require(lhs.isNotEmpty() && rhs.isNotEmpty()){"Complete both sides of the equation"}
            if(function)return result("${lhs[0].v}(${lhs.subList(2,lhs.size-1).filter {it.k==K.Name}.joinToString(", "){it.v}}) = ${body(rhs)}","function_def",lhs[0].v)
            if(lhs.size==1 && lhs[0].v=="r") {
                val (low,high)=bounds(true);val radial=compile(rhs).replace("θ","t_")
                return result("$label = Curve(($radial)*cos(t_), ($radial)*sin(t_), t_, $low, $high)","polar")
            }
            if(lhs.size==1 && lhs[0].v=="y" && rhs.none {it.v=="y"}) {
                if("z" in ids)return result("$label: y = ${compile(rhs)}","implicit_3d")
                return result("$label(x) = ${body(rhs)}","explicit_2d")
            }
            if(lhs.size==1 && lhs[0].v=="z" && rhs.none {it.v=="z"})return result("$label(x, y) = ${body(rhs)}","explicit_3d")
            if(lhs.size==1 && lhs[0].k==K.Name && lhs[0].v !in reserved) {
                val name=lhs[0].v;undefined-=name
                val value=compile(rhs);val number=value.removePrefix("+").toDoubleOrNull()
                val tuple=if(rhs.firstOrNull()?.k==K.Left && matching(rhs,0)==rhs.lastIndex)split(rhs.subList(1,rhs.lastIndex)){it.k==K.Comma}.size else 0
                return result("$name = $value",if(number!=null)"slider" else if(rhs.firstOrNull()?.k==K.List)"list" else if(tuple in 2..3)"point_${tuple}d" else "scalar",name,number)
            }
            return result("$label: ${compile(lhs)} = ${compile(rhs)}",if("z" in ids)"implicit_3d" else "implicit_2d")
        }
        if(main.first().k==K.Left && matching(main,0)==main.lastIndex) {
            val coords=split(main.subList(1,main.lastIndex)){it.k==K.Comma}
            if(coords.size in 2..3) {
                if("t" in ids){val(low,high)=bounds(false);return result("$label = Curve(${coords.joinToString(", ",transform=::compile)}, t, $low, $high)","parametric_${coords.size}d")}
                // Demos' existing u/v surface input is adapted to the same native GeoSurfaceCartesian3D.
                if(coords.size==3 && ids.any {it in setOf("u","v")}){undefined-=setOf("u","v");return result("$label = Surface(${coords.joinToString(", ",transform=::compile)}, u, ${settings.uMin}, ${settings.uMax}, v, ${settings.vMin}, ${settings.vMax})","parametric_surface")}
                return result("$label = (${coords.joinToString(", ",transform=::compile)})","point_${coords.size}d")
            }
        }
        if(main.first().k==K.List)return result("$label = ${compile(main)}","list")
        if("x" in ids && "y" in ids && "z" !in ids)return result("$label(x, y) = ${body(main)}","explicit_3d")
        if(Regex("^Integral\\(.+,.+,.+\\)$").matches(compile(main)))return result("$label = ${compile(main)}","calculus")
        if("x" in ids && "y" !in ids && "z" !in ids)return result("$label(x) = ${body(main)}","explicit_2d")
        if(ids.none {it in setOf("x","y","z","t","theta")})return result("$label = ${compile(main)}","scalar")
        return result("$label: ${compile(main)}",if("z" in ids)"explicit_3d" else "explicit_2d")
    }
}

/** Incremental source adapter: sliders keep their native dependents and camera intact. */
internal class GeoGebraSceneAdapter(private val api: GgbAPI) {
    @Serializable private data class Tracked(val text: String,val color: Int,val style: PlotStyle,val radians: Boolean,val labels: List<String>,val translation: GeoGebraExpressions.Translation)
    @Serializable private data class SavedEntry(val id: Long,val occurrence: Int,val value: Tracked)
    @Serializable private data class SavedScene(val xml: String,val counter: Int,val parameters: Map<String,Double>,val entries: List<SavedEntry>,val domains: List<Double>?=null)
    fun saveState(xml: String): String=Json.encodeToString(SavedScene(xml,counter,parameters,tracked.map {(key,value)->SavedEntry(key.first,key.second,value)},domains))
    fun restoreState(state: String) {
        val saved=Json.decodeFromString<SavedScene>(state)
        api.setXML(saved.xml)
        counter=saved.counter;parameters=saved.parameters;domains=saved.domains;tracked.clear()
        for(entry in saved.entries)tracked[entry.id to entry.occurrence]=entry.value
    }
    private val tracked=linkedMapOf<Pair<Long,Int>,Tracked>()
    private var counter=1
    private var parameters=emptyMap<String,Double>()
    private var domains: List<Double>?=null
    val feedback=linkedMapOf<Long,String>()
    fun sync(expressions: List<PlotExpression>,settings: PlotSettings) {
        feedback.clear()
        val nextDomains=listOf(settings.tMin,settings.tMax,settings.uMin,settings.uMax,settings.vMin,settings.vMax)
        val domainsChanged=domains!=null && domains!=nextDomains
        domains=nextDomains
        for(name in parameters.keys-settings.parameters.keys)if(api.exists(name))api.deleteObject(name)
        for((name,value)in settings.parameters)if(value.isFinite()) {
            if(api.exists(name))api.setValue(name,value) else api.evalCommand("$name = $value")
            api.setVisible(name,false)
        }
        parameters=settings.parameters.toMap()
        val occurrences=HashMap<Long,Int>()
        val keyed=expressions.map {item->(item.id to occurrences.merge(item.id,1,Int::plus)!!) to item}
        val ids=keyed.map {it.first}.toSet()
        for(id in tracked.keys.toList())if(id !in ids){delete(tracked.remove(id)!!.labels)}
        val symbols=MathSyntax.symbols(expressions.map {it.text})
        val variables=symbols.variables+symbols.functions+settings.parameters.keys
        fun rank(e: PlotExpression): Int {
            val text=e.text.trim()
            if(Regex("^[a-zA-Z][a-zA-Z0-9_]*\\s*=\\s*-?\\d*\\.?\\d+$").matches(text))return 0
            if(Regex("^[a-zA-Z][a-zA-Z0-9_]*\\s*=").containsMatchIn(text) && !Regex("^[xyrz]\\s*=").containsMatchIn(text))return 1
            if(Regex("^[a-zA-Z][a-zA-Z0-9_]*\\s*\\([^)]*\\)\\s*=").containsMatchIn(text))return 2
            return 3
        }
        var dependencyDirty=false
        for((key,item) in keyed.sortedBy {rank(it.second)})try {
            val previous=tracked[key]
            if(previous!=null && previous.text==item.text && previous.radians==settings.radians && !dependencyDirty && !domainsChanged && previous.labels.all(api::exists)) {
                if(previous.color!=item.color || previous.style!=item.style){style(previous.labels,item,previous.translation.kind=="slider");tracked[key]=previous.copy(color=item.color,style=item.style)}
                continue
            }
            val translation=GeoGebraExpressions.translate(item.text,"d${counter++}",variables,symbols.functions,settings)
            if(previous?.translation?.kind=="slider" && translation.kind=="slider" && previous.translation.symbol==translation.symbol && api.exists(translation.symbol)) {
                api.setValue(translation.symbol,settings.parameters[translation.symbol]?:translation.numeric!!)
                tracked[key]=previous.copy(text=item.text,color=item.color,style=item.style,radians=settings.radians,translation=translation)
                continue
            }
            previous?.let {delete(it.labels)}
            if(translation.undefined.isNotEmpty()){feedback[item.id]="Undefined: ${translation.undefined.joinToString()}";tracked.remove(key);continue}
            var command=translation.command
            if(!settings.radians)command=Regex("\\b(sin|cos|tan|csc|sec|cot)\\(([^()]+)\\)").replace(command){"${it.groupValues[1]}((${it.groupValues[2]})*°)"}
            var labels=if(command.isBlank())emptyList() else api.evalCommandGetLabels(command)?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            if(labels.isEmpty() && translation.kind=="explicit_2d" && '=' in command) {
                val name=command.substringBefore('=').removeSuffix(" ").removeSuffix("(x)").trim()
                val rhs=command.substringAfter('=').trim().replace(Regex("\\bx\\b"),"t_")
                labels=api.evalCommandGetLabels("$name = Curve(t_, $rhs, 0, t_, ${settings.xMin?:-6.0}, ${settings.xMax?:6.0})")?.split(',').orEmpty()
            }
            if(labels.isEmpty() && command.isNotBlank())feedback[item.id]="Could not evaluate expression in GeoGebra"
            if(translation.kind=="slider" && translation.symbol in settings.parameters)api.setValue(translation.symbol,settings.parameters.getValue(translation.symbol!!))
            style(labels,item,translation.kind=="slider")
            tracked[key]=Tracked(item.text,item.color,item.style,settings.radians,labels,translation)
            if(translation.symbol!=null)dependencyDirty=true
        }catch(failure: Exception){tracked.remove(key);feedback[item.id]=failure.message?:"Invalid expression"}
    }
    private fun style(labels: List<String>,item: PlotExpression,slider: Boolean) {
        val color=item.color;val style=item.style
        for(label in labels)if(api.exists(label)) {
            api.setVisible(label,!slider)
            if(slider)continue
            api.setColor(label,color ushr 16 and 255,color ushr 8 and 255,color and 255)
            api.setLineThickness(label,kotlin.math.round(style.lineWidth*2).toInt().coerceIn(1,13))
            api.setLineStyle(label,when(style.lineStyle){"dashed"->1;"dotted"->3;else->0})
            api.setPointStyle(label,when(style.pointStyle){"open"->2;"cross"->1;else->0})
            api.setPointSize(label,style.pointSize.coerceIn(1,9));api.setLabelVisible(label,style.showLabel)
            if(style.showLabel && style.labelText.isNotEmpty()){api.setCaption(label,style.labelText);api.setLabelStyle(label,3)}
            if(api.getObjectType(label) in setOf("inequality","integral","polygon"))api.setFilling(label,minOf(.45,style.lineOpacity*.35))
        }
    }
    private fun delete(labels: List<String>){for(label in labels)if(api.exists(label))api.deleteObject(label)}
}
