package dev.demos.graphing.plot

import dev.demos.graphing.math.*

data class PlotExpression(val id: Long, val text: String, val color: Int)
data class PlotSettings(
    val showAxes: Boolean = true,
    val showGrid: Boolean = true,
    val radians: Boolean = true,
    val parameters: Map<String, Double> = emptyMap(),
    val xMin:Double?=null,
    val xMax:Double?=null,
    val yMin:Double?=null,
    val yMax:Double?=null,
    val zMin:Double?=null,
    val zMax:Double?=null,
    val tMin:Double=-10.0,
    val tMax:Double=10.0,
    val uMin:Double=-5.0,
    val uMax:Double=5.0,
    val vMin:Double=-5.0,
    val vMax:Double=5.0,
)
data class Viewport(val centerX: Double = 0.0, val centerY: Double = 0.0, val width: Double = 20.0, val height: Double = 16.8) {
    val left get() = centerX-width/2
    val right get() = centerX+width/2
    val bottom get() = centerY-height/2
    val top get() = centerY+height/2
}
enum class PlotKind { Y_FUNCTION, X_FUNCTION, Z_SURFACE, IMPLICIT, POLAR, PARAMETRIC, PARAMETRIC_SURFACE, POINT }
data class PlotRelation(val id:Long, val color:Int, val kind:PlotKind, val expressions:List<CompiledExpression>, val inequality:String?=null)
data class PlotProgram(val relations:List<PlotRelation>,val context:EvaluationContext,val settings:PlotSettings=PlotSettings()) {
    companion object {
        fun compile(expressions:List<PlotExpression>,settings:PlotSettings=PlotSettings()):PlotProgram {
            val constants=HashMap<String,Double>().apply { put("C",0.0);putAll(settings.parameters) }
            val functions=HashMap<String,NumericFunction>()
            val context=EvaluationContext(constants,functions,settings.radians)
            val relations=ArrayList<PlotRelation>()
            val assignments=ArrayList<Pair<String,CompiledExpression>>()
            for(expression in expressions) {
                // Incomplete LaTeX is normal while typing; keep the other plots alive.
                val s=try {NumericCompiler.normalize(expression.text).trim()}
                    catch (_:IllegalArgumentException) {continue}
                    catch (_:IllegalStateException) {continue}
                if(s.isEmpty()) continue
                try {
                    val tableArguments=when {
                        s.startsWith("table(") && s.endsWith(')') -> splitTopLevel(s.substring(6,s.length-1))
                        else -> splitTuple(s)?.takeIf {arguments -> arguments.any {it.trim().startsWith('[')}}
                    }
                    if(tableArguments!=null && tableArguments.size in 2..3) {
                        val columns=tableArguments.map {argument ->
                            val value=argument.trim()
                            if(value.startsWith('[') && value.endsWith(']')) splitTopLevel(value.substring(1,value.length-1)) else listOf(value)
                        }
                        val count=columns.maxOf {it.size}
                        if(columns.all {it.size==1 || it.size==count}) {
                            for(i in 0 until count) {
                                try {
                                    val coordinates=columns.map {column -> NumericCompiler.compile(column[if(column.size==1) 0 else i],settings.radians)}
                                    relations.add(PlotRelation(expression.id,expression.color,PlotKind.POINT,coordinates))
                                } catch (_:IllegalArgumentException) { } catch (_:IllegalStateException) { }
                            }
                        }
                        continue
                    }
                    val functionDefinition=Regex("^([A-Za-z][A-Za-z0-9_]*)\\(([^()]*)\\)\\s*=([^=].*)$").matchEntire(s)
                    if(functionDefinition!=null) {
                        val name=functionDefinition.groupValues[1]
                        val parameters=functionDefinition.groupValues[2].split(',').map {it.trim()}
                        if(parameters.all {it.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))}) {
                            functions[name]=NumericFunction(parameters,NumericCompiler.compile(functionDefinition.groupValues[3],settings.radians))
                            if(parameters==listOf("x")) relations.add(PlotRelation(expression.id,expression.color,PlotKind.Y_FUNCTION,listOf(NumericCompiler.compile("$name(x)",settings.radians))))
                            continue
                        }
                    }
                    val relation=findRelation(s)
                    val lhs=relation?.let {s.substring(0,it.first).trim()}
                    val op=relation?.second
                    val rhs=relation?.let {s.substring(it.first+it.second.length).trim()}
                    if(lhs!=null && rhs!=null && op=="=" && lhs.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) && lhs !in setOf("x","y","z","r")) {
                        if(lhs !in settings.parameters) assignments.add(lhs to NumericCompiler.compile(rhs,settings.radians))
                        continue
                    }
                    fun compiled(v:String)=NumericCompiler.compile(v,settings.radians)
                    val tuple=splitTuple(s)
                    val item=when {
                        tuple!=null -> {
                            val nodes=tuple.map(::compiled)
                            val kind=when {
                                nodes.size==3 && nodes.any {it.variables.any {v -> v in setOf("u","v")}} -> PlotKind.PARAMETRIC_SURFACE
                                nodes.any {it.variables.any {v -> v in setOf("t","theta")}} -> PlotKind.PARAMETRIC
                                else -> PlotKind.POINT
                            }
                            PlotRelation(expression.id,expression.color,kind,nodes)
                        }
                        lhs=="y" && rhs!=null && "y" !in compiled(rhs).variables -> PlotRelation(expression.id,expression.color,PlotKind.Y_FUNCTION,listOf(compiled(rhs)),op?.takeIf {it!="="})
                        lhs=="x" && rhs!=null && "x" !in compiled(rhs).variables -> PlotRelation(expression.id,expression.color,PlotKind.X_FUNCTION,listOf(compiled(rhs)),op?.takeIf {it!="="})
                        lhs=="z" && rhs!=null && "z" !in compiled(rhs).variables -> PlotRelation(expression.id,expression.color,PlotKind.Z_SURFACE,listOf(compiled(rhs)),op?.takeIf {it!="="})
                        lhs=="r" && rhs!=null -> PlotRelation(expression.id,expression.color,PlotKind.POLAR,listOf(compiled(rhs)))
                        lhs!=null && rhs!=null -> PlotRelation(expression.id,expression.color,PlotKind.IMPLICIT,listOf(compiled("($lhs)-($rhs)")),op?.takeIf {it!="="})
                        else -> {
                            val node=compiled(s)
                            if(node.variables.isEmpty() || node.variables.all {it in constants && it !in setOf("x","y","z","t")}) null
                            else PlotRelation(expression.id,expression.color,if("y" in node.variables || "z" in node.variables) PlotKind.Z_SURFACE else PlotKind.Y_FUNCTION,listOf(node))
                        }
                    }
                    if(item!=null) relations.add(item)
                } catch (_:IllegalArgumentException) { /* Incomplete editor rows are not plotted. */ }
                  catch (_:IllegalStateException) { }
            }
            repeat(assignments.size.coerceAtMost(32)+1) {
                for((name,node) in assignments) {
                    val value=node.evaluate(context)
                    if(value.isFinite()) constants[name]=value
                }
            }
            return PlotProgram(relations,context,settings)
        }
        private fun findRelation(source:String):Pair<Int,String>? {
            var depth=0
            for(i in source.indices) {
                when(source[i]) {'(','[','{' -> depth++;')',']','}' -> depth--}
                if(depth==0 && source[i] in "=<>≤≥") {
                    val op=if(i+1<source.length && source[i+1]=='=') source.substring(i,i+2) else source[i].toString()
                    return i to op
                }
            }
            return null
        }
        private fun splitTuple(s:String):List<String>? {
            if(!s.startsWith('(') || !s.endsWith(')')) return null
            val inner=s.substring(1,s.length-1)
            return splitTopLevel(inner).takeIf {it.size in 2..3}
        }
        private fun splitTopLevel(inner:String):List<String> {
            var depth=0;var start=0;val items=ArrayList<String>()
            for(i in inner.indices) {
                when(inner[i]) {'(','[','{' -> depth++;')',']','}' -> depth--}
                if(inner[i]==',' && depth==0) {items.add(inner.substring(start,i));start=i+1}
            }
            items.add(inner.substring(start))
            return items
        }
    }
}
