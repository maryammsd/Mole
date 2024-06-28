package dev.maryam.ReachabilityAnalysis.Analysis;

import soot.SootField;
import soot.SootFieldRef;

public class GlobalVariable implements Variable{
    private int varType = isGlobal; // Global Identified
    private SootFieldRef global ;
    private SootField gfield;
    private int id;

    public GlobalVariable(SootFieldRef global, SootField field){
        this.global = global;
        this.gfield = field;
    }

    public SootFieldRef getGlobal() {
        return global;
    }

    public SootField getGfield() {
        return gfield;
    }

    public int getValue() {
        return id;
    }
    @Override
    public int getVariableType() {
       return varType;
    }

    @Override
    public void setValue(int value) {
            id = value;
    }

}
