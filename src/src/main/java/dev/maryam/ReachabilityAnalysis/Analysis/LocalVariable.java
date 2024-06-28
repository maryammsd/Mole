package dev.maryam.ReachabilityAnalysis.Analysis;

import soot.Local;
import soot.SootField;
import soot.SootFieldRef;

public class LocalVariable implements Variable{

    private Local local;
    private boolean isInstance;
    private SootField sootField;
    private SootFieldRef sootFieldRef;
    private int id;

    public LocalVariable(Local local){
        this.local = local;
        this.isInstance = false;
    }

    public LocalVariable(Local local, SootField sootField, SootFieldRef sootFieldRef){
        this.local = local;
        this.isInstance = true;
        this.sootField = sootField;
        this.sootFieldRef = sootFieldRef;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public void setFields(SootField sootField, SootFieldRef sootFieldRef){
        this.sootFieldRef = sootFieldRef;
        this.sootField = sootField;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public void setValue(int value) {
        this.id = value;
    }

    public void setInstance(boolean instance) {
        isInstance = instance;
    }

    public Local getLocal() {
        return local;
    }

    public SootField getSootField() {
        return sootField;
    }

    public SootFieldRef getSootFieldRef() {
        return sootFieldRef;
    }

    public int getId() {
        return id;
    }

    public boolean isInstance(){ return isInstance; }

    public int getValue() {
        return id;
    }

    @Override
    public int getVariableType() {
        // Local Identified
        return isLocal;
    }

}
