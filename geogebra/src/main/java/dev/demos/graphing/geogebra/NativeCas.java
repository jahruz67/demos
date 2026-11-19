package dev.demos.graphing.geogebra;

import java.util.List;

/** Evaluates GeoGebra's unmodified Giac protocol in Demos' isolated native worker. */
@FunctionalInterface
public interface NativeCas {
    String evaluate(String expression, List<String> initialization) throws Exception;
}
