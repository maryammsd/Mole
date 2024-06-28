package dev.maryam.ReachabilityAnalysis.Analysis;

import dev.maryam.ReachabilityAnalysis.Attribute.States;

public class Constraint {


    final boolean TRUE = true, FALSE = false;
    int AND = 1; int OR = -1;

    // Store list of local variables and states on them required to trigger the bug
    private States states;

    // Is this unit target unit?
    private boolean isTarget;
    private boolean reachable;


    // Store other merged constraint with this constraint
    private Constraint constraint;

    // Store final evaluation of the constraint
    private boolean eval = false;
    private int operator = 1;


    public Constraint() {
        eval = FALSE;
        states = new States();
    }

    public Constraint(Constraint constraint, boolean isReachable) {
        this.constraint = constraint;
        this.reachable = isReachable;
        if(isReachable)
            eval = TRUE;
    }

    public Constraint and(Constraint other) {

        // In this case, we can have two different cases where
        Constraint firstConstraint = new Constraint();
        Constraint secondConstraint = new Constraint();
        firstConstraint.setConstraint(other);
        secondConstraint.setConstraint(other);

        //     1) other.states != null 2) other.constraint != null 3) other.operator = OR
                if(other.getConstraint() != null && other.getOperator() == OR){
                   // for(States states: other.getConstraint().getStates()){

                   // }
                }
        //     2) other.states != null 2) other.constraint == null 3) other.operator = AND

        // (1) Check if there is a similar state / or negation of it

        if(other.getConstraint() != null){

        }else{

        }
        if(states.getStateMap().size() > 0){
            
        }else{
        // (2) Just add the states to current state

        }
        return firstConstraint;
    }

    public Constraint or(Constraint other) {

        // (1) Check if there exists any OR operation now
        if(operator != OR) {
            // (1) Assign operator to OR
            operator = OR;
            // (2) add the other constraint to constraint
            constraint = other;
            return this;
        }else{
            if(constraint == null){
                constraint = other;
                return this;
            }else{
                Constraint orConstraint =  new Constraint();

            }
        }
        return constraint;
    }

    public void negate(boolean negation){
        if(negation) {
            // -- negate the constraint and states both
            // (1) negate the states
            // (2) negate constraint
            // (3) add the constraint to states

        }
    }


    public void setEval(boolean eval) {
        this.eval = eval;
    }

    public void setStates(States states) {
        this.states = states;
    }

    public void setConstraint(Constraint constraint) {
        this.constraint = constraint;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public void setTarget(boolean target) {
        isTarget = target;
    }

    public void setOperator(int operator) {
        this.operator = operator;
    }

    public boolean getEval() {
        return eval;
    }

    public States getStates() {
        return states;
    }


    public Constraint getConstraint() {
        return constraint;
    }

    public boolean getReachable(){
        return reachable;
    }

    public boolean isTargetUnit(){
        return isTarget;
    }

    public int getOperator() {
        return operator;
    }
}
