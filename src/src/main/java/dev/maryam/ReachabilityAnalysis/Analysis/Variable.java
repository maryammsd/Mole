package dev.maryam.ReachabilityAnalysis.Analysis;

public interface Variable {
    int isGlobal = 2, isLocal = 1;
    int id = -1;
    int getVariableType();
    void setValue(int value);
}
