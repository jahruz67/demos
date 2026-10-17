package dev.demos.graphing.math

import kotlin.math.*

/** A small, independent real-valued evaluator used by plotting. CAS is evaluated separately. */
object NumericCompiler {
    private val functionPrefixRegex = Regex("\\\\(sin|cos|tan|asin|acos|atan|sinh|cosh|tanh|ln|log|exp|sqrt|abs)")

    fun compile(source: String, radians: Boolean = true): CompiledExpression {
        val parser = Parser(normalize(source))
        return CompiledExpression(parser.parse(), radians)
    }

    fun normalize(source: String): String {
        var s = source.trim().replace("−", "-").replace("–", "-").replace("×", "*")
            .replace("·", "*").replace("÷", "/").replace("π", "pi").replace("θ", "theta")
            .replace("∞", "Infinity").replace("\\left", "").replace("\\right", "")
            .replace("\\cdot", "*").replace("\\times", "*")
        s = s.replace(functionPrefixRegex, "$1")
        val superscripts = mapOf('⁰' to '0','¹' to '1','²' to '2','³' to '3','⁴' to '4','⁵' to '5','⁶' to '6','⁷' to '7','⁸' to '8','⁹' to '9','⁻' to '-')
        val out = StringBuilder()
        var inPower = false
        for (c in s) {
            if (c in superscripts) {
                if (!inPower) { out.append("^("); inPower = true }
                out.append(superscripts.getValue(c))
            } else { if (inPower) { out.append(')'); inPower = false }; out.append(c) }
        }
        if (inPower) out.append(')')
        return out.toString()
    }
}

class CompiledExpression internal constructor(internal val root: Node, val radians: Boolean) {
    val variables: Set<String> get() = root.variables()
    fun evaluate(variables: Map<String, Double> = emptyMap()): Double = evaluate(EvaluationContext(variables, radians = radians))
    fun evaluate(context: EvaluationContext): Double = try { root.eval(context) } catch (_: ArithmeticException) { Double.NaN }
}

/** Reuse one context throughout a sampling pass to avoid allocations per vertex. */
class EvaluationContext(
    val constants: Map<String, Double> = emptyMap(),
    val functions: Map<String, NumericFunction> = emptyMap(),
    val radians: Boolean = true,
) {
    var x = 0.0; var y = 0.0; var z = 0.0; var t = 0.0
    internal val locals = HashMap<String, Double>()
    internal var depth = 0
    fun variable(name: String): Double = locals[name] ?: constants[name] ?: when (name) {
        "x" -> x; "y" -> y; "z" -> z; "t", "theta" -> t
        "pi" -> PI; "e" -> E; "Infinity" -> Double.POSITIVE_INFINITY
        else -> if (name.length > 1 && name.all { it.isLetter() }) name.fold(1.0) { a, c -> a * variable(c.toString()) } else Double.NaN
    }
}

data class NumericFunction(val parameters: List<String>, val expression: CompiledExpression)

internal sealed interface Node {
    fun eval(c: EvaluationContext): Double
    fun variables(): Set<String>
}
private data class Literal(val number: Double): Node {
    override fun eval(c: EvaluationContext) = number
    override fun variables() = emptySet<String>()
}
private data class Variable(val name: String): Node {
    override fun eval(c: EvaluationContext) = c.variable(name)
    override fun variables() = if (name in setOf("pi", "e", "Infinity")) emptySet() else setOf(name)
}
private data class Unary(val op: String, val a: Node): Node {
    override fun eval(c: EvaluationContext): Double = when(op) {
        "-" -> -a.eval(c); "+" -> a.eval(c); "!" -> factorial(a.eval(c)); else -> Double.NaN
    }
    override fun variables() = a.variables()
}
private data class Binary(val op: String, val a: Node, val b: Node): Node {
    override fun eval(c: EvaluationContext): Double {
        val av = a.eval(c)
        if (op == "and" && av == 0.0) return 0.0
        val bv = b.eval(c)
        return when(op) {
            "+" -> av + bv; "-" -> av - bv; "*" -> av * bv; "/" -> av / bv
            "^" -> realPower(av,bv); "%" -> av % bv
            "<" -> if (av < bv) 1.0 else 0.0; ">" -> if (av > bv) 1.0 else 0.0
            "<=" -> if (av <= bv) 1.0 else 0.0; ">=" -> if (av >= bv) 1.0 else 0.0
            "==" -> if (av == bv) 1.0 else 0.0; "and" -> if (bv != 0.0) 1.0 else 0.0
            else -> Double.NaN
        }
    }
    override fun variables() = a.variables() + b.variables()
}
private data class Restricted(val expression: Node, val condition: Node): Node {
    override fun eval(c: EvaluationContext) = if (condition.eval(c) != 0.0) expression.eval(c) else Double.NaN
    override fun variables() = expression.variables() + condition.variables()
}
private data class Call(val name: String, val args: List<Node>): Node {
    override fun variables(): Set<String> {
        val vars = args.flatMap { it.variables() }.toSet()
        return if (name in setOf("integrate", "int", "integral") && args.size == 4) {
            (args[0].variables() - args[1].variables()) + args[2].variables() + args[3].variables()
        } else vars
    }
    override fun eval(c: EvaluationContext): Double {
        if (name in setOf("integrate", "int", "integral") && args.size == 4 && args[1] is Variable) {
            val variable = (args[1] as Variable).name
            val lower = args[2].eval(c); val upper = args[3].eval(c)
            val previous = c.locals[variable]
            return try { integrate(lower, upper) { v -> c.locals[variable] = v; args[0].eval(c) } }
            finally { if (previous == null) c.locals.remove(variable) else c.locals[variable] = previous }
        }
        if (name == "if" && args.size == 3) return if (args[0].eval(c) != 0.0) args[1].eval(c) else args[2].eval(c)
        val values = args.map { it.eval(c) }
        val a = values.firstOrNull() ?: return Double.NaN
        val b = values.getOrNull(1) ?: Double.NaN
        val angle = if (c.radians) a else a * PI / 180
        val inverseScale = if (c.radians) 1.0 else 180 / PI
        return when(name) {
            "sin" -> sin(angle); "cos" -> cos(angle); "tan" -> tan(angle)
            "asin", "arcsin" -> asin(a) * inverseScale; "acos", "arccos" -> acos(a) * inverseScale
            "atan", "arctan" -> (if(values.size == 2) atan2(a,b) else atan(a)) * inverseScale
            "atan2" -> atan2(a,b) * inverseScale
            "sinh" -> sinh(a); "cosh" -> cosh(a); "tanh" -> tanh(a)
            "asinh" -> asinh(a); "acosh" -> acosh(a); "atanh" -> atanh(a)
            "sec" -> 1 / cos(angle); "csc" -> 1 / sin(angle); "cot" -> 1 / tan(angle)
            "sqrt" -> sqrt(a); "cbrt" -> Math.cbrt(a); "surd", "nthroot", "root" -> realPower(a,1/b); "abs" -> abs(a); "exp" -> exp(a)
            "ln" -> ln(a); "log" -> if(values.size == 2) ln(a)/ln(b) else log10(a)
            "log2" -> log2(a); "log10" -> log10(a)
            "floor" -> floor(a); "ceil", "ceiling" -> ceil(a); "round" -> round(a)
            "sign", "signum" -> sign(a); "min" -> values.minOrNull() ?: Double.NaN
            "max" -> values.maxOrNull() ?: Double.NaN; "mod" -> ((a % b) + b) % b
            "pow" -> realPower(a,b); "hypot" -> hypot(a,b); "factorial" -> factorial(a)
            else -> {
                val function = c.functions[name] ?: return Double.NaN
                if (function.parameters.size != values.size || c.depth > 32) return Double.NaN
                val previous = function.parameters.map { c.locals[it] }
                function.parameters.forEachIndexed { i,p -> c.locals[p] = values[i] }
                c.depth++
                try { function.expression.evaluate(c) } finally {
                    c.depth--
                    function.parameters.forEachIndexed { i,p -> if(previous[i] == null) c.locals.remove(p) else c.locals[p] = previous[i]!! }
                }
            }
        }
    }
}

private fun factorial(v: Double): Double {
    if(v < 0 || v > 170 || v != floor(v)) return Double.NaN
    var answer = 1.0
    for(i in 2..v.toInt()) answer *= i
    return answer
}

private fun realPower(base:Double,exponent:Double):Double {
    val ordinary=base.pow(exponent)
    if(!ordinary.isNaN() || base>=0 || !exponent.isFinite()) return ordinary
    // Real rational powers with an odd reduced denominator (e.g. cube roots).
    for(denominator in 3..99 step 2) {
        val numerator=round(exponent*denominator)
        if(abs(exponent-numerator/denominator)<1e-12) {
            val magnitude=(-base).pow(exponent)
            return if(abs(numerator%2)==1.0) -magnitude else magnitude
        }
    }
    return Double.NaN
}

/** Adaptive Simpson quadrature, evaluated within a strict work budget. */
fun integrate(a: Double, b: Double, tolerance: Double = 1e-8, function: (Double) -> Double): Double {
    if (!a.isFinite() || !b.isFinite()) return Double.NaN
    if (a == b) return 0.0
    var evaluations = 0
    fun sample(x: Double): Double { evaluations++; return function(x) }
    val fa = sample(a); val fb = sample(b); val mid = (a+b)/2; val fm = sample(mid)
    if (!fa.isFinite() || !fb.isFinite() || !fm.isFinite()) return Double.NaN
    fun recurse(l:Double,r:Double,fl:Double,fr:Double,fc:Double,whole:Double,epsilon:Double,depth:Int):Double {
        val center=(l+r)/2; val leftCenter=(l+center)/2; val rightCenter=(center+r)/2
        val fLeft=sample(leftCenter); val fRight=sample(rightCenter)
        if(!fLeft.isFinite() || !fRight.isFinite()) return Double.NaN
        val left=(center-l)*(fl+4*fLeft+fc)/6
        val right=(r-center)*(fc+4*fRight+fr)/6
        val delta=left+right-whole
        if(abs(delta) <= 15*epsilon) return left+right+delta/15
        if(depth <= 0 || evaluations > 8192) return Double.NaN
        return recurse(l,center,fl,fc,fLeft,left,epsilon/2,depth-1) + recurse(center,r,fc,fr,fRight,right,epsilon/2,depth-1)
    }
    return recurse(a,b,fa,fb,fm,(b-a)*(fa+4*fm+fb)/6,tolerance*max(1.0,abs(b-a)),18)
}

private data class Token(val text:String, val number:Double? = null)
private class Parser(private val source:String) {
    private val tokens=ArrayList<Token>()
    private var index=0
    init {
        var i=0
        while(i<source.length) {
            val ch=source[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch=='.' -> {
                    val start=i
                    while(i<source.length && (source[i].isDigit() || source[i]=='.')) i++
                    if(i<source.length && source[i] in "eE" && i+1<source.length && (source[i+1].isDigit() || source[i+1] in "+-")) {
                        i++; if(i<source.length && source[i] in "+-") i++
                        while(i<source.length && source[i].isDigit()) i++
                    }
                    val text=source.substring(start,i)
                    tokens.add(Token(text,text.toDoubleOrNull() ?: error("Invalid number: $text")))
                }
                ch.isLetter() || ch=='_' -> {
                    val start=i++
                    while(i<source.length && (source[i].isLetterOrDigit() || source[i]=='_')) i++
                    tokens.add(Token(source.substring(start,i)))
                }
                ch=='√' -> { tokens.add(Token("sqrt")); i++ }
                ch=='≤' || ch=='≥' -> { tokens.add(Token(if(ch=='≤') "<=" else ">=")); i++ }
                else -> {
                    val two=source.substring(i,min(i+2,source.length))
                    if(two in setOf("<=",">=","==","**")) {tokens.add(Token(if(two=="**") "^" else two));i+=2}
                    else {tokens.add(Token(ch.toString()));i++}
                }
            }
        }
        tokens.add(Token("END"))
    }
    private fun peek()=tokens[index].text
    private fun take()=tokens[index++]
    private fun expect(text:String) { require(take().text==text) {"Expected $text"} }
    fun parse():Node {
        require(source.isNotBlank()) {"Enter an expression"}
        val expression=expression(0)
        require(peek()=="END") {"Unexpected '${peek()}'"}
        return expression
    }
    private fun expression(minBinding:Int):Node {
        val token=take()
        var lhs:Node=when {
            token.number!=null -> Literal(token.number)
            token.text in setOf("+","-") -> Unary(token.text,expression(25))
            token.text=="(" || token.text=="[" -> {
                val inner=expression(0);expect(if(token.text=="(") ")" else "]");inner
            }
            token.text=="|" -> {val inner=expression(0);expect("|");Call("abs",listOf(inner))}
            token.text.firstOrNull()?.let {it.isLetter() || it=='_'}==true && token.text!="END" -> {
                if(peek()=="(" && (token.text in builtins || token.text.length>1 || token.text !in setOf("x","y","z","t","a","b","c","r","e"))) {
                    take(); val arguments=ArrayList<Node>()
                    if(peek()!=")") {arguments.add(expression(0));while(peek()==",") {take();arguments.add(expression(0))}}
                    expect(")");Call(token.text,arguments)
                } else if(token.text in builtins && peek() !in setOf("END", ")", ",")) Call(token.text,listOf(expression(25)))
                else Variable(token.text)
            }
            else -> throw IllegalArgumentException("Expected a number, variable, or function")
        }
        while(true) {
            val op=peek()
            if(op=="!") {if(40<minBinding) break;take();lhs=Unary("!",lhs);continue}
            if(op=="{") {
                if(2<minBinding) break
                take(); var condition=expression(0)
                while(peek()==",") {take();condition=Binary("and",condition,expression(0))}
                expect("}");lhs=Restricted(lhs,condition);continue
            }
            val binding=when(op) {"<",">","<=",">=","==" -> 5;"+","-" -> 10;"*","/","%" -> 20;"^" -> 30;else -> -1}
            val implicit=binding<0 && startsAtom(tokens[index])
            val power=if(implicit) 20 else binding
            if(power<minBinding || power<0) break
            if(!implicit) take()
            val rhs=expression(if(op=="^" && !implicit) power else power+1)
            lhs=if(!implicit && op in setOf("<",">","<=",">=") && lhs is Binary && lhs.op in setOf("<",">","<=",">=")) {
                Binary("and",lhs,Binary(op,lhs.b,rhs))
            } else Binary(if(implicit) "*" else op,lhs,rhs)
        }
        return lhs
    }
    private fun startsAtom(token:Token)=token.number!=null || token.text=="(" || token.text=="[" || (token.text!="END" && token.text.firstOrNull()?.let {it.isLetter() || it=='_'}==true)
    private val builtins=setOf("sin","cos","tan","asin","acos","atan","arcsin","arccos","arctan","sinh","cosh","tanh","asinh","acosh","atanh","sec","csc","cot","sqrt","cbrt","surd","nthroot","root","abs","exp","ln","log","log2","log10","floor","ceil","ceiling","round","sign","signum","min","max","mod","pow","hypot","factorial","atan2","integrate","integral","int","if")
}
