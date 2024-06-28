package dev.maryam.ReachabilityAnalysis.Callback;

import dev.maryam.ReachabilityAnalysis.Analysis.Variable;

public class Callback {
    private Variable variable;
    private int id;
    private String callback;

    Callback(Variable variable, String callback, int id){
        this.callback = callback;
        this.variable = variable;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getCallback() {
        return callback;
    }

    public Variable getVariable() {
        return variable;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }

    public void setVariable(Variable variable) {
        this.variable = variable;
    }
}
