package dev.maryam.ReachabilityAnalysis.CallGraph;

public interface CallGraphFilter {
    boolean isValidEdge(soot.jimple.toolkits.callgraph.Edge edge);
}