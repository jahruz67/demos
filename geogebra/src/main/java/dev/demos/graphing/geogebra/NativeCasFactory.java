package dev.demos.graphing.geogebra;

import java.util.ArrayList;
import java.util.List;
import org.geogebra.common.cas.CASparser;
import org.geogebra.common.cas.giac.CASgiacB;
import org.geogebra.common.cas.giac.EvalFunction;
import org.geogebra.common.cas.giac.binding.CASGiacBinding;
import org.geogebra.common.cas.giac.binding.Context;
import org.geogebra.common.cas.giac.binding.Gen;
import org.geogebra.common.factories.CASFactory;
import org.geogebra.common.kernel.CASGenericInterface;
import org.geogebra.common.kernel.Kernel;

/** Keeps the upstream CAS translator, custom functions, cache and result parser. */
final class NativeCasFactory extends CASFactory {
    private final NativeCas evaluator;
    NativeCasFactory(NativeCas evaluator){this.evaluator=evaluator;}
    @Override public CASGenericInterface newGiac(CASparser parser,Kernel kernel){
        NativeCas callback=evaluator;
        Binding binding=new Binding(callback);
        return new CASgiacB(parser){
            @Override protected CASGiacBinding createBinding(){return binding;}
            @Override protected void callEvaluateFunction(EvalFunction function){function.run();}
            @Override protected String evaluate(String expression,long timeout) throws Throwable {
                // init() also runs on cache hits. Do not retain an unused batch.
                binding.pending.clear();
                return super.evaluate(expression,timeout);
            }
        };
    }
    private static final class Binding implements CASGiacBinding {
        private final NativeCas evaluator;
        private final List<String> pending=new ArrayList<>();
        Binding(NativeCas evaluator){this.evaluator=evaluator;}
        public Context createContext(){return new Context(){};}
        public Gen createGen(String expression,Context context){return new Gen(){
            public Gen eval(int level,Context ignored){pending.add(expression);return this;}
            public String print(Context ignored){
                List<String> initialization=new ArrayList<>(pending);
                initialization.remove(initialization.size()-1);
                try{return evaluator.evaluate(expression,initialization);}
                catch(Exception failure){throw new IllegalStateException("Native CAS evaluation failed",failure);}
                finally{pending.clear();}
            }
        };}
    }
}
