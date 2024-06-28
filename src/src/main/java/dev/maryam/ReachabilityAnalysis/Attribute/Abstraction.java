package dev.maryam.ReachabilityAnalysis.Attribute;

import dev.maryam.ReachabilityAnalysis.Analysis.Variable;

public class Abstraction {

    private Variable variable;
    private State state;
    private  int value;

    public Abstraction(Variable variable, State state, int value){
        this.variable = variable;
        this.state = state;
        this.value = value;
    }

    public Variable getVariable() {
        return variable;
    }

    public State getState() {
        return state;
    }

    public int getValue() { return value; }


    public void setVariable(Variable variable) {
        this.variable = variable;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setValue(int value) {
        this.value = value;
    }

}
