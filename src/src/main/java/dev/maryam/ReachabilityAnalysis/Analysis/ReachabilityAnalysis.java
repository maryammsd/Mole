package dev.maryam.ReachabilityAnalysis.Analysis;

import dev.maryam.ReachabilityAnalysis.Attribute.Abstraction;
import dev.maryam.ReachabilityAnalysis.Attribute.State;
import dev.maryam.ReachabilityAnalysis.Attribute.StateSet;
import dev.maryam.ReachabilityAnalysis.Attribute.States;
import dev.maryam.ReachabilityAnalysis.Core.BackwardAnalysis;
import dev.maryam.ReachabilityAnalysis.Core.InterProceduralCFGRepresentation;
import dev.maryam.ReachabilityAnalysis.Core.ProgramRepresentation;
import soot.*;
import soot.JastAddJ.NEExpr;
import soot.jimple.*;
import soot.jimple.internal.AbstractJimpleIntBinopExpr;
import soot.jimple.internal.JEqExpr;
import soot.jimple.internal.JNeExpr;
import soot.jimple.internal.StmtBox;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.UnitValueBoxPair;
import vasco.Context;

import java.util.*;

/***
 * In this class, it is attempted to
 * 1) perform inter-procedural analysis
 * 2) precondition extraction
 * 3) boundary and interval analysis
 *
 * We extract the preconditions for local variables in each context from the target Statement in a backward manner.
 * the list of
 * - preconditions,
 * - local variables that the precondition is defined over it,
 * - statement where the precondition is extracted from
 *
 *
 */

public class ReachabilityAnalysis extends BackwardAnalysis<SootMethod, Unit, Map<Unit, States>> {


    // Inter-procedural Control Flow Graph
    private InterProceduralCFGRepresentation icfg;

    // The information about crash point
    private String targetClassN, targetMethodN, targetPackageName;
    private int line = 0;
    private boolean targetVisited = false;

    // List of successors of the current unit
    private List<Unit> succesUnit = new ArrayList<Unit>();

    // List of API functions used as state
    private StateSet stateSet;

    // List of fragments that are used as casting in the source code
    private ArrayList<String> fragmentList = new ArrayList<>();

    // Initialize the target informaiton such as the class, method and line where it was triggered.
    public ReachabilityAnalysis(String targetClassN, String targetMethodN, String targetPackageName, int line, InterProceduralCFGRepresentation icfg, StateSet stateSet) {
        this.targetClassN = targetClassN;
        this.targetMethodN = targetMethodN;
        this.line = line;
        this.icfg = icfg;
        this.stateSet = stateSet;
        this.targetPackageName = targetPackageName;
    }

    // Initialize the context Map<Unit,States> for a method when there is a call to it
    @Override
    protected Context<SootMethod, Unit, Map<Unit, States>> initContext(SootMethod method, Map<Unit, States> exitValue) {

        if (icfg.isAnalyzable(method) && !icfg.isExcludedMethod(method)) {
            return super.initContext(method, exitValue);
        } else {
            return super.initContextForPhantomMethod(method, exitValue);
        }
    }

    /***
     * In this function, the normal flow is checked to propagate the reachability and update the state and target sets
     *      if @unit's succrsor are reachable,
     *          make it reachable
     *      else
     *          skip it.
     *      if @unit is target,
     *          add it to target set
     *      else
     *          skip and pass existing @target set to the predecessor
     *
     * @param context :  a set of methods, its units and mapping of <unit, State>
     * @param unit: is the current unit we are calculating the preconditions for
     * @param outValue: is the list of reachable local variables and the constraint reached to this point.
     * @return list of preConditions for the visited units up to now
     *
     *
     *
     */
    @Override
    public Map<Unit, States> normalFlowFunction(Context<SootMethod, Unit, Map<Unit, States>> context, Unit unit, Map<Unit, States> outValue) {

        // The @inValue of the current unit
        Map<Unit, States> invalue = new HashMap<>();
        States currentState = new States();
        boolean target = false;

        // Is it a if or else branch
        boolean isIf = false, isElse = false;

        // (1) Initialize the results to @inValue and get the successor of @inValue to continue
        invalue = copy(outValue);
        //System.out.println("here we are "+ unit.toString());

        // (2)  Check if the current unit is the target unit, if yes, set @target =  true
        if (context.getMethod().getSignature().equals(targetMethodN) && context.getMethod().getDeclaringClass().getName().equals(targetClassN)) {
            int currentLine = unit.getJavaSourceStartLineNumber();
            System.out.println("[Target] method is found at line " + currentLine);
            System.out.println("[Target] unit is " + unit);
            System.out.println("[Target] line should be " + line);
            System.out.println("[Outvalue] " + outValue.size());

            if (line == currentLine) {
                target = true;
                System.out.println("[Target] is found");
                currentState.setReachable(true);
                currentState.setTarget(unit);
            }
        }

        // (3) get successors of the current unit @unit
        succesUnit = context.getControlFlowGraph().getSuccsOf(unit);

        // (4) check for reachability of the @unit

        //  if the @target is true, @unit is reachable.
        if (outValue.size() > 0) {

            // (4-1) [Return Statement] for @return values that occur at the exit point of the function
            // if @return is reachable, it will be added to the outValue at callExitFlowFunction
            if (outValue.containsKey(unit)) {
                if (outValue.get(unit).isReachable()) {
                    target = true;
                }
            }

            // [GoTo Statement] if the successor of a unit is Goto statement,
            //       make the @unit as reachable if it points to a reachable @unit
            if (unit instanceof GotoStmt) {
                for (UnitBox unitBox : unit.getUnitBoxes()) {
                    StmtBox stmtBox = (StmtBox) unitBox;
                    if (outValue.containsKey(stmtBox.getUnit())) {
                        if (outValue.get(stmtBox.getUnit()).isReachable()) {
                            target = true;
                            break;
                        }
                    }
                }
            }

            // [If Statement / Else Statement] if either of the branches of an if condition  is reachable,
            //       make the @unit as reachable
            if (unit instanceof IfStmt) {
                // First check if the 'else' part of the conditional statement is reachable
                for (UnitBox unitBox : unit.getUnitBoxes()) {
                    StmtBox stmtBox = (StmtBox) unitBox;
                    if (outValue.containsKey(stmtBox.getUnit())) {
                        if (outValue.get(stmtBox.getUnit()).isReachable()) {
                            target = true;
                            isElse = true;
                            break;
                        }
                    }
                }

                // Second check if the 'if' part of the conditional statement is reachable
                for (Unit successorUnit : succesUnit) {
                    if (outValue.containsKey(successorUnit)) {
                        if (outValue.get(successorUnit).isReachable()) {
                            target = true;
                            isIf = true;
                            break;
                        }
                    }
                }
            }

            // [Assignment Statement, etc. ] if the successor of an Assingment or Invocation statement is reachable,
            //       make the @unit as reachable
            if (unit instanceof AssignStmt || unit instanceof InvokeStmt || unit instanceof IdentityStmt || unit instanceof BreakpointStmt || unit instanceof NopStmt || unit instanceof MonitorStmt || unit instanceof SwitchStmt) {
                for (Unit successorUnit : succesUnit) {
                    if (outValue.containsKey(successorUnit)) {
                        if (outValue.get(successorUnit).isReachable()) {
                            target = true;
                        }
                    }
                }

            }

        } else {
            if (target) {
                System.out.println("[Warning] there must be an error since outvalue size is more than zero and target is false!");
                System.out.println("[Warning] the current unit is " + unit.toString());
            }

        }

        // (5) check unit type and update the Constraint @reachableUnits accordingly
        if (target) {

            // Add @currentState to the mapping for @unit in @invalue
            currentState.setReachable(true);

            // If there is rule #8, A = B where A is in @varset, add B to @varset too.
            if (unit instanceof AssignStmt) {

                // Update @VarSet, @stateMap and @targets @currentState for this @unit
                for (Unit successor : succesUnit) {
                    if (outValue.containsKey(successor)) {
                        currentState.setStateMap(outValue.get(successor).getStateMap());
                        currentState.setVarSet(outValue.get(successor).getVarSet());
                        currentState.updateTargetSet(outValue.get(successor).getTargets());
                        currentState.setIds(outValue.get(successor).getIds());
                    }
                }

                Value lhs = ((AssignStmt) unit).getLeftOp();
                Value rhs = ((AssignStmt) unit).getRightOp();

                // Is it a fragment ?
                if (rhs instanceof CastExpr) {
                    Type castType = ((CastExpr) rhs).getCastType();
                    SootClass fragmentClass = Scene.v().getSootClass(castType.toString());
                    // get parent of fragmentClass : if fragment, then add it to the @fragmentList
                    if (fragmentClass != null) {
                        if (fragmentClass.getPackageName().contains(targetPackageName)) {

                            String superClass = icfg.getSuperClass(fragmentClass);
                            if (icfg.isFragment(superClass)) {
                                System.out.println("[fragment] " + fragmentClass.getName());
                                fragmentList.add(fragmentClass.getName());
                            }
                        }
                    }
                }
                Variable lhsVar = getVariable(lhs);

                if (lhsVar != null) {
                    if (currentState.hasVar(lhsVar)) {
                        System.out.println("[assign-stmt] " + unit.toString());
                        Variable rhsvar = getVariable(rhs);
                        if (rhsvar != null) {
                            if (rhs instanceof Constant) {
                                System.out.println(" --- [constant]");
                            } else if (rhs instanceof Local) {
                                System.out.println(" --- [local] ");
                                currentState.setVar((LocalVariable) rhsvar);
                            } else if (rhs instanceof CastExpr) {

                                System.out.println(" --- [cast] ");
                                Value rhsCast = ((CastExpr) rhs).getOp();
                                if (getVariable(rhsCast) != null) currentState.setVar(getVariable(rhsCast));
                            } else if (rhs instanceof StaticFieldRef) {
                                System.out.println(" --- [static fieldRef] ");
                                currentState.setVar((GlobalVariable) rhsvar);
                            } else if (rhs instanceof InstanceFieldRef) {
                                System.out.println(" --- [instance fieldRef] ");
                                currentState.setVar((LocalVariable) rhsvar);
                            } else if (rhs instanceof NewExpr) {
                                System.out.println(" --- [new]");
                            } else {
                                System.out.println(" [rhs] " + rhs.getType().toString());
                            }
                        }
                    }
                }
                // If there is any ifStmt, let's merge the states accordingly
            } else if (unit instanceof IfStmt) {
                if (succesUnit.size() == 2) {
                    Unit successor1 = succesUnit.get(0);
                    Unit successor2 = succesUnit.get(1);
                    if (outValue.containsKey(successor1)) {
                        currentState.setStateMap(outValue.get(successor1).getStateMap());
                        currentState.setVarSet(outValue.get(successor1).getVarSet());
                        currentState.updateTargetSet(outValue.get(successor1).getTargets());
                        currentState.setIds(outValue.get(successor1).getIds());
                    }
                    if (outValue.containsKey(successor2)) {
                        currentState.addStates(outValue.get(successor2).getStateMap());
                        for (Variable variable : outValue.get(successor2).getVarSet())
                            currentState.setVar(variable);
                        currentState.updateTargetSet(outValue.get(successor2).getTargets());
                        currentState.setIds(outValue.get(successor2).getIds());
                    }
                    if (currentState.getTargets().contains(successor1) || currentState.getTargets().contains(successor2)) {
                        currentState.setTarget(unit);
                    }
                } else if (succesUnit.size() == 1) {
                    Unit successor1 = succesUnit.get(0);
                    if (outValue.containsKey(successor1)) {
                        currentState.setStateMap(outValue.get(successor1).getStateMap());
                        currentState.setVarSet(outValue.get(successor1).getVarSet());
                        currentState.updateTargetSet(outValue.get(successor1).getTargets());
                        currentState.setIds(outValue.get(successor1).getIds());
                    }
                    if (currentState.getTargets().contains(successor1)) {
                        currentState.setTarget(unit);
                    }
                }
            } else if (unit instanceof ReturnStmt || unit instanceof ReturnVoidStmt || unit instanceof RetStmt) {
                if (invalue.containsKey(unit)) {
                    currentState.setStateMap(outValue.get(unit).getStateMap());
                    currentState.setVarSet(outValue.get(unit).getVarSet());
                    currentState.updateTargetSet(outValue.get(unit).getTargets());
                    currentState.setIds(outValue.get(unit).getIds());
                }

            } else if (unit instanceof GotoStmt) {
                // Update @VarSet, @stateMap and @targets @currentState for this @unit
                Unit targetStmt = ((GotoStmt) unit).getTarget();
                //System.out.println("[goto-stmt] "+ unit.toString());
                if (outValue.get(targetStmt) != null) {
                    //System.out.println(" --- [target-stmt] "+ targetStmt.toString());
                    currentState.setStateMap(outValue.get(targetStmt).getStateMap());
                    currentState.setVarSet(outValue.get(targetStmt).getVarSet());
                    currentState.updateTargetSet(outValue.get(targetStmt).getTargets());
                    currentState.setIds(outValue.get(targetStmt).getIds());
                }
            } else if (unit instanceof LookupSwitchStmt) {
                List<IntConstant> lookupValues = ((LookupSwitchStmt) unit).getLookupValues();
                List<Unit> targets = ((LookupSwitchStmt) unit).getTargets();
                Unit defaultTarget = ((LookupSwitchStmt) unit).getDefaultTarget();
                for (int i = 0; i < lookupValues.size(); i++) {
                    if (outValue.containsKey(targets.get(i))) {
                        currentState.setStateMap(outValue.get(targets.get(i)).getStateMap());
                        currentState.setVarSet(outValue.get(targets.get(i)).getVarSet());
                        currentState.updateTargetSet(outValue.get(targets.get(i)).getTargets());
                        currentState.setIds(outValue.get(targets.get(i)).getIds());
                    }
                }
                if (outValue.containsKey(defaultTarget)) {
                    currentState.setStateMap(outValue.get(defaultTarget).getStateMap());
                    currentState.setVarSet(outValue.get(defaultTarget).getVarSet());
                    currentState.updateTargetSet(outValue.get(defaultTarget).getTargets());
                    currentState.setIds(outValue.get(defaultTarget).getIds());
                }

            } else if (unit instanceof IdentityStmt || unit instanceof InvokeStmt || unit instanceof BreakpointStmt || unit instanceof NopStmt || unit instanceof MonitorStmt) {

                // Update @VarSet, @stateMap and @targets @currentState for this @unit
                for (Unit successor : succesUnit) {
                    if (outValue.containsKey(successor)) {
                        currentState.setStateMap(outValue.get(successor).getStateMap());
                        currentState.setVarSet(outValue.get(successor).getVarSet());
                        currentState.updateTargetSet(outValue.get(successor).getTargets());
                        currentState.setIds(outValue.get(successor).getIds());
                    }
                }

            } else if (unit instanceof ThrowStmt) {
                // Is throw statement a tail stmt in a function?
                if (invalue.containsKey(unit)) {
                    currentState.setStateMap(outValue.get(unit).getStateMap());
                    currentState.setVarSet(outValue.get(unit).getVarSet());
                    currentState.updateTargetSet(outValue.get(unit).getTargets());
                    currentState.setIds(outValue.get(unit).getIds());
                } else {
                    // If not, then try to just get its successors and pass them backward
                    for (Unit successor : succesUnit) {
                        if (outValue.containsKey(successor)) {
                            currentState.setStateMap(outValue.get(successor).getStateMap());
                            currentState.setVarSet(outValue.get(successor).getVarSet());
                            currentState.updateTargetSet(outValue.get(successor).getTargets());
                            currentState.setIds(outValue.get(successor).getIds());
                        }
                    }
                }
            }

            // Add the @currentState for this unit
            if (invalue.containsKey(unit)) {
                invalue.remove(unit);
            }
            invalue.put(unit, currentState);
        }

        return invalue;
    }

    @Override
    public Map<Unit, States> boundaryValue(SootMethod sootMethod) {
        return topValue();
    }

    @Override
    public Map<Unit, States> topValue() {
        return new HashMap<>();
    }

    /**
     * In this function, we copy the value of
     *
     * @param outValue : results of successors of current unit
     * @return : aggregation of results of successors with current unit
     */
    @Override
    public Map<Unit, States> copy(Map<Unit, States> outValue) {
        Map<Unit, States> copyValue = topValue();
        for (Unit unit : outValue.keySet()) {
            copyValue.put(unit, outValue.get(unit));
        }
        return copyValue;
    }

    /**
     * In this function, we just meet the states of units reaching to a point together
     * If one unit is found in both paths, it is necessary to consider the meet of it:
     * meet of stateMaps,
     * the @VarSet,
     * reachability and
     * target set
     * If one unit is not found in either of paths, it will be added to the final output
     *
     * @param outvalue1
     * @param outvalue2
     * @return
     */
    @Override
    public Map<Unit, States> meet(Map<Unit, States> outvalue1, Map<Unit, States> outvalue2) {
        Map<Unit, States> meetValue = new HashMap<>();
        if (!outvalue1.isEmpty() && !outvalue2.isEmpty()) {
            // For each unit in @outvalue1 and @outvalue2
            //      (1) check if the statemaps are equal or not
            //              if yes, just add it in the meet one
            //                 else, merge them accordingly.
            for (Unit unit1 : outvalue1.keySet()) {
                if (outvalue2.containsKey(unit1)) {

                    boolean eqTargets = false, eqVarSet = false, eqStateMap = false;
                    if (outvalue1.get(unit1).getTargets().equals(outvalue2.get(unit1).getTargets())) eqTargets = true;
                    if (outvalue1.get(unit1).equalStateMap(outvalue1.get(unit1).getStateMap(), outvalue2.get(unit1).getStateMap()))
                        eqStateMap = true;
                    if (outvalue1.get(unit1).equalVarSets(outvalue1.get(unit1).getVarSet(), outvalue2.get(unit1).getVarSet()))
                        eqVarSet = true;
                    if (eqTargets && eqStateMap && eqVarSet) {
                        meetValue.put(unit1, outvalue1.get(unit1));
                    } else {
                        States mergedState = new States();
                        // merge StateMap
                        mergedState.setStateMap(outvalue1.get(unit1).getStateMap());
                        mergedState.addStates(outvalue2.get(unit1).getStateMap());
                        // merge VarSet
                        mergedState.setVarSet(outvalue1.get(unit1).getVarSet());
                        for (Variable variable : outvalue2.get(unit1).getVarSet()) {
                            if (!mergedState.hasVar(variable)) mergedState.setVar(variable);
                        }
                        // merge targets
                        mergedState.updateTargetSet(outvalue1.get(unit1).getTargets());
                        mergedState.updateTargetSet(outvalue2.get(unit1).getTargets());
                        mergedState.setIds(outvalue1.get(unit1).getIds());
                        mergedState.setIds(outvalue2.get(unit1).getIds());

                        // set reachable
                        mergedState.setReachable(outvalue1.get(unit1).isReachable() || outvalue2.get(unit1).isReachable());

                        meetValue.put(unit1, mergedState);
                    }
                } else meetValue.put(unit1, outvalue1.get(unit1));
            }
            for (Unit unit1 : outvalue2.keySet()) {
                if (!outvalue1.containsKey(unit1)) {

                    meetValue.put(unit1, outvalue2.get(unit1));
                }
            }
            return meetValue;

        } else if (!outvalue1.isEmpty()) {
            meetValue = copy(outvalue1);
            return meetValue;
        } else if (!outvalue2.isEmpty()) {
            meetValue = copy(outvalue2);
            return meetValue;
        }

        return topValue();
    }

    @Override
    public Map<Unit, States> callEntryFlowFunction(Context<SootMethod, Unit, Map<Unit, States>> context, SootMethod sootMethod, Unit unit, Map<Unit, States> entryValue) {

        // Check reachability
        boolean reachable = false;
        States currentState = new States();

        // entrySet after a call is invoked
        Map<Unit, States> beforeCall = topValue();

        // Successors of this @unit: let's update current state at the entry point of this call
        //      with its successor states
        List<Unit> successors = context.getControlFlowGraph().getSuccsOf(unit);
        States successorState = new States();


        // let's add the successors outvalue to the current unit
        for (Unit unit1 : successors) {
            if (entryValue.containsKey(unit1)) {
                if (entryValue.get(unit1).isReachable()) {
                    reachable = true;
                    successorState.setReachable(true);
                }
                successorState.setStateMap(entryValue.get(unit1).getStateMap());
                successorState.setVarSet(entryValue.get(unit1).getVarSet());
                successorState.updateTargetSet(entryValue.get(unit1).getTargets());
                successorState.setIds(entryValue.get(unit1).getIds());
            }
        }


        // (0) Check if entryValue has reachable points, set this @unit reachability as true :)
        if (entryValue.containsKey(unit)) {
            if (entryValue.get(unit).isReachable()) reachable = true;
            if (entryValue.get(unit).getIds().size() > 0) currentState.setIds(entryValue.get(unit).getIds());
            if (!entryValue.get(unit).getTargets().isEmpty()) currentState.setTarget(unit);
        }

        // (1) Pass whatever received from successor to it
        if (successorState.isReachable()) reachable = true;

        if (successorState.getTargets().size() > 0) {
            // Update this to unit rather than successorState
            currentState.updateTargetSet(successorState.getTargets());
        }

        if (successorState.getStateMap().size() > 0) {
            currentState.setStateMap(successorState.getStateMap());
        }

        if (successorState.getIds().size() > 0) currentState.setIds(successorState.getIds());

        // Get invocation statement
        InvokeExpr invokeExpr = ((Stmt) unit).getInvokeExpr();

        // (2) Is it a call to an app function or library function?
        if (icfg.isAnalyzable(sootMethod) && !icfg.isExcludedMethod(sootMethod)) {
            List<Unit> headUnits = icfg.getControlFlowGraph(sootMethod).getHeads();
            // System.out.println("CallEntry: An app function " + sootMethod.getSignature());
            // System.out.println(" --- entryValue " + entryValue.size());
            // (3) Is it reachable at all? (*) Add the variables to @VarSet and merge the states , (**) Is it target?

            if (sootMethod.hasActiveBody()) {
                //System.out.println("[entry of method] " + sootMethod.getSignature());

                // Map the internal state to the outer state
                for (Unit firstUnit : headUnits) {
                    States firstState = entryValue.get(firstUnit);
                    // System.out.println("[head unit] " + firstUnit.toString());
                    // (1) Reachability Checking again
                    if (firstState != null) {
                        if (firstState.isReachable()) {
                            currentState.setReachable(true);
                            reachable = true;
                            currentState.setIds(firstState.getIds());
                        }
                        // If there are more than one targets,
                        //      then there is a statement that has accessed or modified the state of a local or global variable
                        //          we definitely put it as target
                        //      then the target statement lies within this call
                        if (firstState.getTargets() != null) {
                            if (firstState.getTargets().size() > 0) {
                                // (2) Update varSet by checking if the parameters and arguments are involved in propagating the states.
                                if (invokeExpr.getMethod().getSignature().equals(sootMethod.getSignature())) {
                                    // (2-1) Parameters first
                                    if (invokeExpr.getArgCount() > 0) {

                                        for (int i = 0; i < invokeExpr.getArgCount(); i++) {
                                            Value arg = invokeExpr.getArg(i);
                                            Local param = sootMethod.getActiveBody().getParameterLocal(i);

                                            // (3) if @param is equal to any of the variables within @VarSet,
                                            //          Add the variable @arg to @currentState @VarSet and its state, too :)

                                            if (arg != null && param != null) {

                                                // (4) Check if there is any local parameters defined by arguments
                                                if (firstState.hasVar((new LocalVariable(param)))) {
                                                    Variable argVar = null;
                                                    //System.out.println("[Method] " + unit.toString());
                                                    //System.out.println("[param] " + i + " :" + param.getName());
                                                    //System.out.println("[arg] " + i + " :" + arg.toString());
                                                    //  if (!arg.toString().equals("null"))
                                                    //    System.out.println("arg is not null");

                                                    if (arg instanceof Local) {
                                                        argVar = new LocalVariable((Local) arg);
                                                    } else if (arg instanceof InstanceFieldRef) {
                                                        argVar = new LocalVariable((Local) ((InstanceFieldRef) arg).getBase(), ((InstanceFieldRef) arg).getField(), ((InstanceFieldRef) arg).getFieldRef());
                                                        System.out.println(" -- [entry arg]: instance var " + arg.toString());
                                                    } else if (arg instanceof StaticFieldRef) {
                                                        argVar = new GlobalVariable(((StaticFieldRef) arg).getFieldRef(), ((StaticFieldRef) arg).getField());
                                                        System.out.println(" -- [entry arg]: static var " + arg);
                                                    }

                                                    if (argVar != null) {

                                                        // do we have argVar in the successor states?
                                                        // yes, lets update the statemap
                                                        if (currentState.hasVar(argVar)) {
                                                            // if the argument exists in the states reached from the successors of the call-site
                                                            // update the currentState accordingly
                                                            currentState.addStates(mergeStates(currentState, firstState, argVar, (new LocalVariable(param))));
                                                        } else {
                                                            // if the argument does not exist in the states reached from the successors of the call-site
                                                            // add the argument and update the currentState accordingly
                                                            currentState.setVar(argVar);
                                                            currentState.addStates(mergeStates(currentState, firstState, argVar, new LocalVariable(param)));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // (2-2) Check if there is any global variable state being modified or accessed in the body of the
                                    //      function
                                    for (Variable globalVar : firstState.getVarSet()) {
                                        if (globalVar instanceof GlobalVariable) {
                                            if (!currentState.hasVar(globalVar)) {
                                                currentState.setVar(globalVar);
                                            }
                                            currentState.addStates(firstState.getListofAbstraction(globalVar));
                                        }
                                    }
                                    // (2-2) Set @VarSet with lhop of assighment operation in @unit
                                    //         if invocation is assignment type
                                    //          and
                                    //         return value is among @VarSet
                                    if (unit instanceof AssignStmt) {
                                        Value lhop = ((AssignStmt) unit).getLeftOp();
                                        for (Unit unit1 : icfg.getControlFlowGraph(sootMethod).getTails()) {
                                            if (unit1 instanceof ReturnStmt) {
                                                // Get the corresponding variable at return-site
                                                Value returnVar = ((ReturnStmt) unit1).getOp();
                                                Variable retVar = null;
                                                Variable lhVar = null;

                                                // Is the lhop Local? Is it Global?
                                                if (lhop instanceof Local) {
                                                    lhVar = new LocalVariable((Local) lhop);
                                                    //System.out.println("entry: local lhop return " + lhop.toString());
                                                } else if (lhop instanceof InstanceFieldRef) {
                                                    lhVar = new LocalVariable((Local) ((InstanceFieldRef) lhop).getBase(), ((InstanceFieldRef) lhop).getField(), ((InstanceFieldRef) lhop).getFieldRef());
                                                    System.out.println("entry: instance lhop return " + lhop.toString());
                                                } else if (lhop instanceof StaticFieldRef) {
                                                    lhVar = new GlobalVariable(((StaticFieldRef) lhop).getFieldRef(), ((StaticFieldRef) lhop).getField());
                                                    System.out.println("entry: global lhop return " + lhop.toString());
                                                }

                                                // Is it Local Variable ? Is it Global Variable?
                                                if (returnVar instanceof Local) {
                                                    retVar = new LocalVariable((Local) returnVar);
                                                    //System.out.println("entry: local return " + returnVar.toString());
                                                } else if (returnVar instanceof InstanceFieldRef) {
                                                    retVar = new LocalVariable((Local) ((InstanceFieldRef) returnVar).getBase(), ((InstanceFieldRef) returnVar).getField(), ((InstanceFieldRef) returnVar).getFieldRef());
                                                    System.out.println("entry: instance return " + returnVar.toString());
                                                }

                                                // Merge @stateMaps if there is any @states for operand at return-site
                                                if (lhVar != null && retVar != null) {
                                                    if (firstState.hasVar(retVar)) {
                                                        if (!currentState.hasVar(lhVar)) {
                                                            currentState.setVar(lhVar);
                                                        }
                                                        if (retVar instanceof LocalVariable) {
                                                            currentState.addStates(mergeStates(currentState, firstState, lhVar, (LocalVariable) retVar));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }


                                }

                                // (3) Add remaining states to @currentState

                                // ( + )  this seems to be extra since we already added successorState to currentState before
                                //for (Abstraction abstraction : successorState.getStateMap())
                                //    currentState.addState(abstraction);

                                // (4) If there is a target within its context, mark this @unit as target and add it to target set
                                currentState.setTarget(unit);
                            }
                        }
                    }
                }

                // ( + ) Map the return value to the lhop if there is a Assignment and global variable being passed backward
                if (unit instanceof AssignStmt) {
                    Value lhop = ((AssignStmt) unit).getLeftOp();
                    for (Unit unit1 : icfg.getControlFlowGraph(sootMethod).getTails()) {
                        if (unit1 instanceof ReturnStmt) {
                            // Get the corresponding variable at return-site
                            Value returnVar = ((ReturnStmt) unit1).getOp();
                            Variable retVar = null;
                            Variable lhVar = null;

                            // Is the lhop Local? Is it Global?
                            if (lhop instanceof Local) {
                                lhVar = new LocalVariable((Local) lhop);
                                //System.out.println("entry: local lhop return " + lhop.toString());
                            } else if (lhop instanceof InstanceFieldRef) {
                                lhVar = new LocalVariable((Local) ((InstanceFieldRef) lhop).getBase(), ((InstanceFieldRef) lhop).getField(), ((InstanceFieldRef) lhop).getFieldRef());
                                System.out.println("entry: instance lhop return " + lhop.toString());
                            } else if (lhop instanceof StaticFieldRef) {
                                lhVar = new GlobalVariable(((StaticFieldRef) lhop).getFieldRef(), ((StaticFieldRef) lhop).getField());
                                System.out.println("entry: global lhop return " + lhop.toString());
                            }

                            // Is it constant value ? Is it Global Variable?
                            if (returnVar instanceof IntConstant) {
                                //retVar = new LocalVariable((Local) returnVar);
                                //System.out.println("[Warning] entry: constant return " + returnVar.toString() + " at method " + sootMethod.getSignature());

                            } else if (returnVar instanceof StaticFieldRef) {
                                retVar = new GlobalVariable(((StaticFieldRef) returnVar).getFieldRef(), ((StaticFieldRef) returnVar).getField());
                                System.out.println("entry: global return " + returnVar.toString());
                                // for all lhVars, just pass the return Variable to it and adjust it
                                if (currentState.hasVar(retVar) && currentState.hasVar(lhVar)) {
                                    for (Abstraction abstraction : currentState.getStateMap()) {
                                        if ((abstraction.getVariable() instanceof LocalVariable &&
                                                lhVar instanceof LocalVariable)) {
                                            if (currentState.equalLocalVariables((LocalVariable) abstraction.getVariable(), (LocalVariable) lhVar)) {
                                                abstraction.setVariable(retVar);
                                            }
                                        } else if (abstraction.getVariable() instanceof GlobalVariable && lhVar instanceof GlobalVariable) {
                                            if (currentState.equalLocalVariables((LocalVariable) abstraction.getVariable(), (LocalVariable) lhVar)) {
                                                abstraction.setVariable(retVar);
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                }


            }
        } else {
            // (6) Is it a function call of libraries? ( I am not sure if I should add it here or LocalFlow :)
            //       Is it a def-site ?
            //          Add it to target set
            //       Is it a use-site ?
            //          Get its value and add the state to statemap and update it accordingly
            //  System.out.println("CallEntry: A library function " + sootMethod.getSignature());
            //  System.out.println(" --- entryValue " + entryValue.size());
            // (6-1) It is a state call, let's see if it is def or use on the target statement

            // (6-2) Not a state-related call : pass the states of successor to it as inValue: done before :)

        }


        // (7) Set Reachability as true for this @unit
        // ( + ) checking the line number as well
        if (isTarget(unit, context.getMethod())) {
            currentState.setReachable(true);
            reachable = true;
            currentState.setTarget(unit);
        }
        // set Visited as true for further reachable points in loops
        if (reachable) beforeCall.put(unit, currentState);

        // if(unit.toString().contains("<dummyMainClass: org.mozilla.focus.settings.SettingsFragment dummyMainMethod_org_mozilla_focus_se"))
        //    System.out.println("there is something wrong");
        //System.out.println("Entry : outvalue  " + entryValue.size() + " beforeCall " + beforeCall.size() + " on " + unit.toString());

        return beforeCall;
    }


    @Override
    public Map<Unit, States> callExitFlowFunction(Context<SootMethod, Unit, Map<Unit, States>> context, SootMethod sootMethod, Unit unit, Map<Unit, States> outValue) {

        States currentState = new States();

        // InValue after invocation is done for inner body of the method invoked
        Map<Unit, States> afterCall = topValue();

        // Successors of this @unit: it should be only one successor
        List<Unit> successors = context.getControlFlowGraph().getSuccsOf(unit);

        // *** Reachability
        // (1) If there is any unit within @outvalue that is successor and reachable,
        //      set reachability true
        //(2) Get reachable state of outValue ( + ) modified the logic and simplified it a little and add it under (4)
        // It can be more than one successor for cases where there is catch and try, and monitor expressions.

        // (3-1) Get invocation statement
        InvokeExpr invokeExpr = ((Stmt) unit).getInvokeExpr();


        // (3-2) If @unit is target, mark it as reachable
        if (isTarget(unit, context.getMethod())) {
            System.out.println("[Target] found at " + unit.toString());
            currentState.setReachable(true);
            currentState.setTarget(unit);
        }

        // (4) Is the call-site reachable?
        //if (currentState.isReachable()) {
        // (4-1) If the function is analyzable and not among library funtions or stub functions
        if (icfg.isAnalyzable(sootMethod) && !icfg.isExcludedMethod(sootMethod)) {

            // (5) Get state reaching this point and update the state-related variables
            for (Unit successorUnit : successors) {
                // (5-1) Set @VarSet with parameters and variables at return-site if the return value is among @VarSet
                // (5-1-1) Parameters first
                if (outValue.containsKey(successorUnit)) {

                    if (outValue.get(successorUnit).isReachable()) {

                        // make the state reachable
                        currentState.setReachable(true);

                        // pass the IDS
                        currentState.setIds(outValue.get(successorUnit).getIds());

                        // *** Constraints
                        // ( + ) how about passing the global variables state mapping ?
                        // (3-1) Add the global variables to the @VarSet to pass them
                        if (outValue.get(successorUnit).getStateMap().size() > 0) {
                            for (Abstraction abstraction : outValue.get(successorUnit).getStateMap()) {
                                Variable temp = abstraction.getVariable();
                                if (temp instanceof GlobalVariable) {
                                    System.out.println("[exit] passing global variable inter-procedurally");
                                    currentState.setVar(temp);
                                    //currentState.addState(abstraction);
                                }
                            }
                        }

                        // ( + ) map the variables and pass the abstraction to the inner side of the method
                        if (invokeExpr.getArgCount() > 0) {
                            if (invokeExpr.getMethod().getSignature().equals(sootMethod.getSignature())) {

                                for (int i = 0; i < invokeExpr.getArgCount(); i++) {
                                    Value arg = invokeExpr.getArg(i);
                                    Local param = sootMethod.getActiveBody().getParameterLocal(i);

                                    // if @param is equal to any of the variables within @VarSet,
                                    //          Just add the variable @param to @currentState @VarSet

                                    if (arg instanceof Local) {
                                        if (outValue.get(successorUnit).hasVar(new LocalVariable((Local) arg))) {
                                            currentState.setVar(new LocalVariable(param));
                                            System.out.println("exit: local var " + arg.toString());
                                        }
                                    } else if (arg instanceof InstanceFieldRef) {
                                        // it should be parameter not arg !
                                        if (outValue.get(successorUnit).hasVar(new LocalVariable((Local) ((InstanceFieldRef) arg).getBase(), ((InstanceFieldRef) arg).getField(), ((InstanceFieldRef) arg).getFieldRef()))) {
                                            currentState.setVar(new LocalVariable(param));
                                            System.out.println("exit: instance var " + arg.toString());
                                        }
                                    } else if (arg instanceof StaticFieldRef) { // How should I add this
                                        if (outValue.get(successorUnit).hasVar(new GlobalVariable(((StaticFieldRef) arg).getFieldRef(), ((StaticFieldRef) arg).getField()))) {
                                            System.out.println("[global var] exit point " + arg.toString());
                                            System.out.println("[param] exit point " + param.toString());
                                            currentState.setVar(new LocalVariable(param));
                                        }
                                    }
                                }
                            }

                            // (5-1-1) Return Variable next ( + )  will remove it from here cuz it is handled at the localEntry part to pass the variables
                            // and abstractions to the currentState after the whole function call is handled.
                        }

                    }
                }

            }

            // If no, mark the returnValue or last statement as reachable to perform intra-procedural reachability analysis
            boolean last = false;

            // Get return-sites and mark them as reachable!
            // Get the declration of GUI elements by findViewById and the aliases and
            //          add the variables to !varSet
            //          add the units to target unit sets
            List<Unit> tails = icfg.getControlFlowGraph(sootMethod).getTails();
            for (Unit unit1 : tails) {
                if (isTarget(unit, context.getMethod())) {
                    currentState.setTarget(unit1);
                }
                afterCall.put(unit1, currentState);
                last = true;
            }

            // if no return-site, set the last unit as reachable
            if (!last) {
                if (isTarget(unit, context.getMethod())) {
                    currentState.setTarget(sootMethod.getActiveBody().getUnits().getLast());
                    afterCall.put(sootMethod.getActiveBody().getUnits().getLast(), currentState);
                }
            }

        } else {

            // ( - ) How about variables and stateMaps???? -> In callLocalFlowFunction, all are updated :)
            // (5) Get state reaching this point and update the state-related variables
            for (Unit successorUnit : successors) {
                // (5-1) Set @VarSet with parameters and variables at return-site if the return value is among @VarSet
                // (5-1-1) Parameters first
                if (outValue.containsKey(successorUnit)) {

                    if (outValue.get(successorUnit).isReachable()) {
                        // make the state reachable
                        currentState.setReachable(true);
                    }
                }
            }
            //
            //
        }

        if (currentState.isReachable()) afterCall.put(unit, currentState);
        //System.out.println("Exit : outvalue  " + outValue.size() + " afterCall " + afterCall.size() + " on " + unit.toString());


        return afterCall;

    }


    @Override
    public Map<Unit, States> callLocalFlowFunction(Context<SootMethod, Unit, Map<Unit, States>> context, Unit unit, Map<Unit, States> outValue) {

        // Create a new sets of @states for collecting states for current invocation
        States currentState = new States();


        // Is current unit a target statement ?
        if (isTarget(unit, context.getMethod())) {
            System.out.println("[Target] found at Local " + unit.toString());
            currentState.setReachable(true);
            currentState.setTarget(unit);
        }

        // (1) Does current  @unit have any states ?
        if (outValue.containsKey(unit)) {
            if (outValue.get(unit) != null) {
                currentState = outValue.get(unit);
            }
        }


        // (2) Copy @outvalue to a set of values for @afterCall
        Map<Unit, States> afterCall = copy(outValue);

        // (3) Let's see what can be the set of @states for this @unit
        List<Unit> successors = context.getControlFlowGraph().getSuccsOf(unit);

        // (4) merge the successor's states, varset and targets accordingly
        // ( + )  why do we need this part?
        for (Unit temp : successors) {
            if (outValue.containsKey(temp)) {
                // (5) Is the successor reachable?
                if (outValue.get(temp).isReachable()) {
                    currentState.setReachable(true);
                    currentState.setIds(outValue.get(temp).getIds());
                }
                // (6) Add successor's targets to current @unit's target sets
                currentState.updateTargetSet(outValue.get(temp).getTargets());
                // (7) Add successor's states to current @unit's state sets
                currentState.addStates(outValue.get(temp).getStateMap());
                // (8) Add successor's variables to current @unit's variable sets
                for (Variable variable : outValue.get(temp).getVarSet())
                    currentState.setVar(variable);
            }
        }


        // Get invocation statement to further check
        //      if it is
        //      (1) a def call-site ?
        //      (2) use call-site ?
        InvokeExpr invokeExpr = ((Stmt) unit).getInvokeExpr();

        // Is this a app method call or a
        //if (states.isReachable()) {

        if (icfg.isAnalyzable(invokeExpr.getMethod()) && !icfg.isExcludedMethod(invokeExpr.getMethod())) {

            // (1) Is there any of the return-sites in the @outvalue??
            List<Unit> tail_stms = new ArrayList<>();
            for (Unit unit1 : icfg.getControlFlowGraph(invokeExpr.getMethod()).getTails()) {
                // Is the return stmt among @outvalues ?
                if (outValue.containsKey(unit1)) {
                    tail_stms.add(unit1);
                }
            }

            for (Unit unit1 : invokeExpr.getMethod().getActiveBody().getUnits()) {
                // Is it findValueById within the body of this method?  If yes, find the use-sites and add the lhs variables to the varSet
                // Is it an assignment x: a.b where b is a global variable?? If yes, then you should add the corresponding variables
                if (unit1 instanceof AssignStmt) {
                    // Is it a state declaration function?
                    Value rhsop = ((AssignStmt) unit1).getRightOp();
                    Value lhsop = ((AssignStmt) unit1).getLeftOp();
                    if (rhsop instanceof InstanceInvokeExpr) {
                        int stateNumber = stateSet.getState(((InstanceInvokeExpr) rhsop).getMethod().getSubSignature());
                        /*
                        There are two different ways to access View elements of the GUI: findViewById or view binding
                        Former: in the former, the element exist in the layouts and accessed here, the lhs will be a view object
                        Later: in the latter, the element is created at the code and binds to current layout accordingly
                                this element can be a menu or other widgets
                                If it is a menu, then, MenuInflator is used and the input  is menu's id and UI element and there is no lhs
                                    findItem, add, addSubMenu or getItem is also used to add items regarding their id
                                o.w. , then LayoutInflator is used and the lhs is a view and it is necessary to keep track of its lhs
                                        addView or removeView are used to add a view to a bigger layout
                         */
                        ArrayList<Variable> newVars = new ArrayList<>();
                        List<Value> args;
                        boolean isAdded = false;
                        int id = 0;
                        //if(stateNumber != -1)
                        // System.out.println(" [found] def-site "+ unit1.toString());
                        switch (stateNumber) {
                            case 15:
                                // findViewById: view <- id
                                // (1) Get the parameter passed
                                //      if id -> add it to the idSet
                                args = ((InstanceInvokeExpr) rhsop).getArgs();
                                id = 0;
                                if (args.size() == 1) {
                                    if (args.get(0) instanceof IntConstant) {
                                        id = ((IntConstant) args.get(0)).value;
                                    }
                                }
                                //      if view -> add it to the varSet and find all the aliases in the current context
                                // (2) Add the variable in the @lhs
                                if (id != 0 && currentState.hasId(id)) {
                                    isAdded = true;
                                    newVars = getNewVar(context.getMethod().getActiveBody(), currentState, unit1, lhsop);
                                    for (Variable foundvar : newVars) {
                                        currentState.setVar(foundvar);
                                    }
                                }

                                break;
                            case 150:
                            case 151:
                                // inflate : view <- id , view
                                // (1) get the arguments and check for existence of the id and the variable
                                args = ((InstanceInvokeExpr) rhsop).getArgs();
                                id = 0;
                                if (args.size() > 1) {
                                    if (args.get(0) instanceof IntConstant) {
                                        id = ((IntConstant) args.get(0)).value;
                                    }
                                }
                                // (2) add @view to existing views as well. ( - )

                                // (3) get the return value and check for its use sites
                                if (id != 0 && currentState.hasId(id)) {
                                    isAdded = true;
                                    newVars = getNewVar(context.getMethod().getActiveBody(), currentState, unit1, lhsop);
                                    for (Variable foundvar : newVars) {
                                        currentState.setVar(foundvar);
                                    }
                                }
                                break;
                            case 168:
                            case 169:
                            case 170:
                            case 171:
                            case 172:
                                // add MenuItem <- ...
                                // (1) get the arguments and check for their existence
                                args = ((InstanceInvokeExpr) lhsop).getArgs();
                                if (args.size() >= 1) {
                                    if (args.get(0) instanceof IntConstant) {
                                        id = ((IntConstant) args.get(0)).value;
                                    }
                                }
                                if (id != 0 && currentState.hasId(id)) {
                                    isAdded = true;
                                    newVars = getNewVar(context.getMethod().getActiveBody(), currentState, unit1, lhsop);
                                    for (Variable foundvar : newVars) {
                                        currentState.setVar(foundvar);
                                    }
                                }
                                break;
                        }
                        if (isAdded)
                            System.out.println(" --- [ADDED]");
                        /***
                         else {
                         // Is there any assignment? Let's do the pointer-analysis and find all the possible
                         //     variables that are aliases
                         if (rhsop instanceof StaticFieldRef) {
                         if (currentState.hasVar(new GlobalVariable(((StaticFieldRef) rhsop).getFieldRef(), ((StaticFieldRef) rhsop).getField()))) {
                         if (lhsop instanceof Local)
                         currentState.setVar(new LocalVariable((Local) lhsop));
                         else if (lhsop instanceof StaticFieldRef)
                         currentState.setVar(new GlobalVariable(((FieldRef) lhsop).getFieldRef(), ((FieldRef) lhsop).getField()));
                         else if (lhsop instanceof InstanceFieldRef)
                         currentState.setVar(new LocalVariable((Local) ((InstanceFieldRef) lhsop).getBase(), ((InstanceFieldRef) lhsop).getField(), ((InstanceFieldRef) lhsop).getFieldRef());
                         }
                         } else if (rhsop instanceof InstanceFieldRef) {

                         }
                         }
                         **/
                    }//else if(rhsop instanceof StaticInvokeExpr)
                    //System.out.println("[warning] local not checking static invokeexpr "+ unit1.toString());

                    // ( + )Is there any assignment? Let's do the pointer-analysis and find all the possible
                    //     variables that are aliases
                    /**
                     if(rhsop instanceof Local){
                     if (currentState.hasVar(new LocalVariable((Local) rhsop))) {
                     if (lhsop instanceof Local)
                     currentState.setVar(new LocalVariable((Local) lhsop));
                     else if (lhsop instanceof StaticFieldRef)
                     currentState.setVar(new GlobalVariable(((FieldRef) lhsop).getFieldRef(), ((FieldRef) lhsop).getField()));
                     else if (lhsop instanceof InstanceFieldRef)
                     currentState.setVar(new LocalVariable((Local) ((InstanceFieldRef) lhsop).getBase(), ((InstanceFieldRef) lhsop).getField(), ((InstanceFieldRef) lhsop).getFieldRef()));
                     }
                     }if (rhsop instanceof StaticFieldRef) {
                     if (currentState.hasVar(new GlobalVariable(((StaticFieldRef) rhsop).getFieldRef(), ((StaticFieldRef) rhsop).getField()))) {
                     if (lhsop instanceof Local)
                     currentState.setVar(new LocalVariable((Local) lhsop));
                     else if (lhsop instanceof StaticFieldRef)
                     currentState.setVar(new GlobalVariable(((FieldRef) lhsop).getFieldRef(), ((FieldRef) lhsop).getField()));
                     else if (lhsop instanceof InstanceFieldRef)
                     currentState.setVar(new LocalVariable((Local) ((InstanceFieldRef) lhsop).getBase(), ((InstanceFieldRef) lhsop).getField(), ((InstanceFieldRef) lhsop).getFieldRef()));
                     }
                     } else if (rhsop instanceof InstanceFieldRef) {
                     if (currentState.hasVar(new LocalVariable((Local)((InstanceFieldRef) rhsop).getBase(), ((StaticFieldRef) rhsop).getField(), ((StaticFieldRef) rhsop).getFieldRef()))) {
                     if (lhsop instanceof Local)
                     currentState.setVar(new LocalVariable((Local) lhsop));
                     else if (lhsop instanceof StaticFieldRef)
                     currentState.setVar(new GlobalVariable(((FieldRef) lhsop).getFieldRef(), ((FieldRef) lhsop).getField()));
                     else if (lhsop instanceof InstanceFieldRef)
                     currentState.setVar(new LocalVariable((Local) ((InstanceFieldRef) lhsop).getBase(), ((InstanceFieldRef) lhsop).getField(), ((InstanceFieldRef) lhsop).getFieldRef()));
                     }
                     }
                     **/
                }
            }

            for (Unit unit1 : tail_stms) {
                if (afterCall.containsKey(unit1)) {
                    currentState.setVarSet(afterCall.get(unit1).getVarSet());
                    currentState.updateTargetSet(outValue.get(unit1).getTargets());
                    currentState.setStateMap(outValue.get(unit1).getStateMap());
                    currentState.setIds(outValue.get(unit1).getIds());
                    afterCall.remove(unit1);
                    afterCall.put(unit1, currentState);
                }
            }

        } else {
            if (invokeExpr.getMethod().getSignature().startsWith("<android")) {
                // (6) Is it a function call of libraries?
                //       Is it a def-site ?
                //          Add it to target set
                //       Is it a use-site ?
                //          Get its value and add the state to statemap and update it accordingly

                int stateNumber = stateSet.getState(invokeExpr.getMethod().getSubSignature());

                // (6-1) It is a state call, let's see if it is def or use on the target statement
                if (stateNumber != -1) {

                    int isDef = stateSet.getIsDef(invokeExpr.getMethod().getSubSignature());

                    if (isDef == 1) {
                        // (6-3) Is there any variable in VarSet with variable called on this function? If yes, add it to target set
                        if (invokeExpr instanceof InstanceInvokeExpr) {
                            // Is it an assignment or just an invocation ?
                            if (unit instanceof AssignStmt) {
                                /*
                                If assignment, it can be any form of view <- id / view <- view
                                    If variable is among Varset, add id to id set
                                    If id is among id set, add vars to the VarSet

                                 */
                                System.out.println("[Assignment] " + unit.toString());
                                // x :=  r.method(y)
                                Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
                                System.out.println("[base type] " + base.getType().toString());

                                // x is a view element (findViewById, findViewByTag, inflate, createView)
                                Value lhs = ((AssignStmt) unit).getLeftOp(); // x
                                Variable variable = getVariable(lhs);

                                // ( - ) What if the assignment does not contain any of the
                                if (variable != null) {
                                    if (currentState.hasVar(variable)) {
                                        currentState.setTarget(unit);
                                        List<Value> args = new ArrayList();
                                        switch (stateNumber) {
                                            case 15:
                                                // findViewById: view <- id
                                                // (1) Get the parameter passed
                                                //      if id -> add it to the idSet
                                                args = ((InstanceInvokeExpr) invokeExpr).getArgs();
                                                if (args.size() == 1) {
                                                    if (args.get(0) instanceof IntConstant) {
                                                        currentState.addId(((IntConstant) args.get(0)).value);
                                                    }
                                                }
                                                break;
                                            case 16:
                                            case 159:
                                            case 160:
                                                // 16: findViewByTag: view <- tag
                                                // 159,160: createView: view <- ...

                                                break;
                                            case 150:
                                            case 151:
                                                // inflate : view <- id , view
                                                // (1) get the arguments and check for existence of the id and the variable
                                                args = ((InstanceInvokeExpr) invokeExpr).getArgs();
                                                if (args.size() > 1) {
                                                    if (args.get(0) instanceof IntConstant) {
                                                        currentState.addId(((IntConstant) args.get(0)).value);
                                                    }
                                                }
                                                break;

                                            case 168:
                                            case 169:
                                            case 170:
                                            case 171:
                                            case 172:
                                                // add MenuItem <- ...
                                                // (1) get the arguments and check for their existence
                                                args = ((InstanceInvokeExpr) invokeExpr).getArgs();
                                                if (args.size() >= 1) {
                                                    if (args.get(0) instanceof IntConstant) {
                                                        currentState.addId(((IntConstant) args.get(0)).value);
                                                    }
                                                }
                                                break;
                                        }
                                    }
                                }

                            } else if (unit instanceof InvokeStmt) {
                                // r.method(y)
                                // base variable of current invocation
                                System.out.println("[Invoke] " + unit.toString());
                                if (unit instanceof InstanceInvokeExpr) {
                                    Value base = ((InstanceInvokeExpr) unit).getBase();
                                    // Is there any variable on it within @varset
                                    Variable variable = getVariable(base);
                                    // Check for the set attributes
                                    if (variable != null) {
                                        if (currentState.hasVar(variable)) {
                                            currentState.setTarget(unit);
                                        }
                                    }

                                    // Check for defining a view
                                    List<Value> args = new ArrayList<>();
                                    Value view = null;
                                    int id = 0;
                                    switch (stateNumber) {
                                        case 152:
                                        case 153:
                                        case 154:
                                        case 155:
                                        case 156:
                                        case 161:
                                        case 162:
                                        case 163:
                                        case 164:
                                            // addView: void <- view
                                            // (1) Get first argument which is a view Type
                                            args = ((InstanceInvokeExpr) base).getArgs();
                                            if (args.size() > 1) {
                                                if (args.get(0).getType().toString().equals("android.view.View")) {
                                                    view = args.get(0);
                                                }
                                            }
                                            // (2) add the variable and corresponding variables to the currentState var list
                                            if (currentState.hasVar(new LocalVariable((Local) view)))
                                                currentState.setTarget(unit);
                                            break;
                                        case 165:
                                            // inflate: void <- int, menu

                                            // (1) get the arguments and check for their existence
                                            args = ((InstanceInvokeExpr) base).getArgs();
                                            if (args.size() > 1) {
                                                if (args.get(0) instanceof IntConstant) {
                                                    id = ((IntConstant) args.get(0)).value;
                                                }
                                                if (args.get(1).getType().toString().equals("android.view.Menu")) {
                                                    view = args.get(1);
                                                }
                                            }
                                            //      if view -> add it to the varSet and find all the aliases in the current context
                                            // (2) Add the menu to the variables
                                            if (currentState.hasVar(new LocalVariable((Local) view)) || currentState.hasId(id)) {
                                                currentState.setTarget(unit);
                                            }
                                            break;
                                    }
                                }

                            }
                        } else if (invokeExpr instanceof StaticFieldRef) {
                            System.out.println(" [warning] Def-site in form of global variable " + unit.toString());
                        }
                    } else if (isDef == 0) {
                        // (6-4) Is it a use-site? Does it have any target within its branches ? If yes, just collect it and add it to @stateMap
                        // It should be an Assignment statement

                        if (invokeExpr instanceof InstanceInvokeExpr) {
                            // (*I) Get Target Variable
                            Value base = ((InstanceInvokeExpr) invokeExpr).getBase();

                            // (*II) Is it an Assignment operation?
                            if (unit instanceof AssignStmt) {

                                // (*III) In which units, the left-handside is used?
                                if (context.getMethod().hasActiveBody()) {
                                    ExceptionalUnitGraph graph = new ExceptionalUnitGraph(context.getMethod().getActiveBody());
                                    SimpleLocalDefs localDefs = new SimpleLocalDefs(graph);
                                    SimpleLocalUses localUses = new SimpleLocalUses(graph, localDefs);
                                    List<UnitValueBoxPair> pairs = localUses.getUsesOf(unit);
                                    for (UnitValueBoxPair pair : pairs) {

                                        if (currentState.getTargets().size() > 0) {
                                            // Is it an IfStmt?
                                            if (pair.getUnit() instanceof IfStmt) {

                                                // (*IV) What is target of if statement?
                                                Unit iftarget = ((IfStmt) pair.getUnit()).getTarget();

                                                // Get the condition and the operation on it
                                                Value ifCondition = ((IfStmt) pair.getUnit()).getCondition();

                                                // Is the target within if or else part?
                                                boolean found = false, finish = false, isIfElse = false;

                                                // Is any of the targets within this conditional statement?
                                                Unit temp = getSuccessor((Unit) pair.getUnit(), successors); // get the successor of if rather than Go To statement


                                                // Check if the target is within if-part
                                                while (!finish) {

                                                    // Is current statement a target statement?
                                                    if (currentState.getTargets().contains(temp)) {
                                                        found = true;
                                                        // (*VI) What is the condition? :
                                                        if (!isIfElse) {
                                                            // add the condition
                                                            // check if op1 is a local
                                                            // check if op2 is constant
                                                            // negate the symbol : if == or != , we can add the condition to the state lists
                                                            if (ifCondition instanceof JNeExpr || ifCondition instanceof JEqExpr) {
                                                                Value op1 = ((AbstractJimpleIntBinopExpr) ifCondition).getOp1();
                                                                Value op2 = ((AbstractJimpleIntBinopExpr) ifCondition).getOp2();
                                                                Boolean eq = ifCondition.toString().contains("==");
                                                                if ((op1 instanceof Local || op1 instanceof FieldRef) && op2 instanceof IntConstant) {
                                                                    State newState = new State(invokeExpr.getMethod().getSubSignature(), stateNumber, isDef);
                                                                    int value = ((IntConstant) op2).value;
                                                                    Variable baseVar = getVariable(base);
                                                                    if (eq) {
                                                                        if (value == 0) {
                                                                            value = 1;
                                                                        } else value *= (-1);
                                                                    }
                                                                    Abstraction newAbstraction = new Abstraction(baseVar, newState, value);
                                                                    if (!currentState.checkNegationExist(newAbstraction))
                                                                        currentState.addState(newAbstraction);
                                                                    currentState.setVar(baseVar);
                                                                    // add if as the target
                                                                    currentState.setTarget(pair.getUnit());
                                                                }
                                                            }

                                                        } else {
                                                            // add the condition
                                                            // check if op1 is a local
                                                            // check if op2 is constant
                                                            // check the symbol : if == or != , we can add the condition to the state lists
                                                            if (ifCondition instanceof NEExpr || ifCondition instanceof EqExpr) {
                                                                Value op1 = ((CmpExpr) ifCondition).getOp1();
                                                                Value op2 = ((CmpExpr) ifCondition).getOp2();
                                                                String symbol = ((CmpExpr) ifCondition).getSymbol();

                                                                Variable baseVar = getVariable(base);
                                                                if (op1 instanceof Local && op2 instanceof IntConstant) {
                                                                    State newState = new State(context.getMethod().getSubSignature(), stateNumber, isDef);
                                                                    int value = ((IntConstant) op2).value;
                                                                    if (symbol.equals("!=")) {
                                                                        if (value == 0) {
                                                                            value = 1;
                                                                        } else value *= (-1);
                                                                    }
                                                                    Abstraction newAbstraction = new Abstraction(baseVar, newState, value);
                                                                    if (!currentState.checkNegationExist(newAbstraction))
                                                                        currentState.addState(newAbstraction);
                                                                    currentState.setVar(baseVar);
                                                                    // add the if-target as the target
                                                                    currentState.setTarget(pair.getUnit());
                                                                }
                                                            }
                                                        }
                                                    }
                                                    // Is the previous predecessor Goto or retStmt ?
                                                    //      thus, this is if-then else
                                                    // if iftarget = gototarget where temp is a goto statement : if-else

                                                    if (temp instanceof GotoStmt) {
                                                        Unit gotoTarget = ((GotoStmt) temp).getTarget();
                                                        // we have an if-then else
                                                        if (gotoTarget == iftarget && !isIfElse) {
                                                            isIfElse = true;
                                                        }
                                                    } else if ((temp instanceof RetStmt) || (temp instanceof ReturnVoidStmt)) {
                                                        // we have an ifthen else here too!
                                                        if (!isIfElse) isIfElse = true;
                                                    }

                                                    // if temp = iftarget : if
                                                    if (temp == iftarget) {
                                                        // we just reached the end of the if
                                                        finish = true;
                                                    }

                                                    if (!finish) {
                                                        // point to the successor
                                                        List<Unit> tempSuccessor = context.getControlFlowGraph().getSuccsOf(temp);
                                                        if (tempSuccessor != null) {
                                                            if (tempSuccessor.size() > 0) {
                                                                int currentLine = temp.getJavaSourceStartLineNumber();
                                                                int successorLine = -1;
                                                                System.out.println(" temp is " + temp.toString());
                                                                if (temp instanceof IfStmt) {
                                                                    temp = getSuccessor(temp, tempSuccessor);
                                                                } else {
                                                                    Unit previousUnit = temp;
                                                                    temp = tempSuccessor.get(0);
                                                                    if (previousUnit instanceof GotoStmt) {
                                                                        successorLine = temp.getJavaSourceStartLineNumber();
                                                                        System.out.println("lines are " + successorLine + " " + currentLine);
                                                                        if (currentLine > successorLine && (currentLine != -1) && (successorLine != -1)) {
                                                                            System.out.println(" we have a loop" + temp.toString());
                                                                            temp = context.getMethod().getActiveBody().getUnits().getSuccOf(previousUnit);
                                                                        } else if (currentLine == -1 || successorLine == -1) {
                                                                            boolean isafter = checkLocAtCode(previousUnit, temp, context.getMethod().getActiveBody().getUnits());
                                                                            if (isafter) {
                                                                                System.out.println(" we have a loop" + temp.toString());
                                                                                temp = context.getMethod().getActiveBody().getUnits().getSuccOf(previousUnit);
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                            } else {
                                                                if (temp instanceof ReturnStmt || temp instanceof RetStmt || temp instanceof ReturnVoidStmt)
                                                                    finish = true;
                                                            }
                                                        }
                                                    }

                                                }
                                                // Is current stmt if? If yes, just skip it cause it will be added later on,
                                                //      otherwise, it is else part and you should check it!
                                                if (found)
                                                    System.out.println(" - a conditon with target is found in call local " + unit.toString());
                                                else {
                                                    System.out.println(" - a condition without target " + unit.toString());

                                                    // add the condition
                                                    // check if op1 is a local
                                                    // check if op2 is constant
                                                    // negate the symbol : if == or != , we can add the condition to the state lists
                                                    if (ifCondition instanceof JNeExpr || ifCondition instanceof JEqExpr) {
                                                        Value op1 = ((AbstractJimpleIntBinopExpr) ifCondition).getOp1();
                                                        Value op2 = ((AbstractJimpleIntBinopExpr) ifCondition).getOp2();
                                                        Boolean eq = ifCondition.toString().contains("==");
                                                        Boolean neq = ifCondition.toString().contains("!=");
                                                        if ((op1 instanceof Local || op1 instanceof FieldRef) && op2 instanceof IntConstant) {
                                                            State newState = new State(invokeExpr.getMethod().getSubSignature(), stateNumber, isDef);
                                                            int value = ((IntConstant) op2).value;
                                                            Variable baseVar = getVariable(base);
                                                            if (eq || neq) { // if i == t go to target ; NextStatement
                                                                if (value > 0) {
                                                                    value *= (-1);
                                                                }
                                                            }
                                                            currentState.setVar(new LocalVariable((Local) op1));
                                                            Abstraction newAbstraction = new Abstraction(baseVar, newState, value);
                                                            if (!currentState.checkNegationExist(newAbstraction))
                                                                currentState.addState(newAbstraction);
                                                        }
                                                    }
                                                }


                                            } else if (pair.getUnit() instanceof LookupSwitchStmt) {
                                                // Get the variable x of switch(x)
                                                Value keyValue = ((LookupSwitchStmt) pair.getUnit()).getKey();
                                                // target @unit is an assignment since it is a use-site
                                                //      Is x equal to the lhs of current @unit ??
                                                if (((AssignStmt) unit).getLeftOp().equals(keyValue)) {
                                                    // add the base value as the variable
                                                    Variable variable = getVariable(base);
                                                    currentState.setVar(variable);

                                                    // Let's see if this unit is parent of any target unit ?

                                                    for (int i = 0; i < ((LookupSwitchStmt) pair.getUnit()).getLookupValues().size(); i++) {
                                                        // Get the first unit of the i-th case statement
                                                        Unit caseTarget = ((LookupSwitchStmt) pair.getUnit()).getTarget(i);
                                                        int value = ((LookupSwitchStmt) pair.getUnit()).getLookupValue(i);
                                                        boolean hasTarget = false;


                                                        if (value == 2131099723)
                                                            System.out.println("lets debug to see what is going to be done here");
                                                        // Is the @caseTarget or any other statements within this block until the
                                                        //      next case statement
                                                        //      return statement
                                                        //  a target?

                                                        if (i + 1 < ((LookupSwitchStmt) pair.getUnit()).getLookupValues().size()) {
                                                            List<Unit> caseUnits = new ArrayList<>();
                                                            caseUnits = getCaseLevelUnits(caseTarget, context.getMethod().getActiveBody(), ((LookupSwitchStmt) pair.getUnit()).getTarget(i + 1));
                                                            for (Unit unit1 : caseUnits) {
                                                                if (currentState.getTargets().contains(unit1)) {
                                                                    hasTarget = true;
                                                                    State newState = new State(invokeExpr.getMethod().getSubSignature(), stateNumber, isDef);
                                                                    Abstraction abstraction = new Abstraction(variable, newState, value);
                                                                    if (!currentState.checkNegationExist(abstraction))
                                                                        currentState.addState(abstraction);
                                                                    else {
                                                                        System.out.println(" [negation exist] with target" + currentState.removeState(abstraction.getValue() * (-1), abstraction.getVariable()));
                                                                    }
                                                                    System.out.println("abstract value is " + abstraction.getValue());
                                                                    currentState.setVar(new LocalVariable((Local) keyValue));
                                                                    currentState.setTarget(unit);
                                                                    if (context.getMethod().getSignature().contains("Id"))
                                                                        currentState.addId(((LookupSwitchStmt) pair.getUnit()).getLookupValue(i));
                                                                }
                                                            }

                                                            // if the target doesn't lie within this part, just
                                                            //      disable this item in the list
                                                            if (!hasTarget) {
                                                                State newState = new State(invokeExpr.getMethod().getSubSignature(), stateNumber, isDef);
                                                                Abstraction abstraction = new Abstraction(variable, newState, ((LookupSwitchStmt) pair.getUnit()).getLookupValue(i) * (-1));
                                                                if (!currentState.checkNegationExist(abstraction))
                                                                    currentState.addState(abstraction);
                                                                else
                                                                    System.out.println(" [negation exist] without target" + currentState.removeState(abstraction.getValue(), abstraction.getVariable()));
                                                                currentState.setVar(new LocalVariable((Local) keyValue));
                                                                if (context.getMethod().getSignature().contains("Id"))
                                                                    currentState.addId(((LookupSwitchStmt) pair.getUnit()).getLookupValue(i));
                                                            }
                                                        } else {
                                                            // consider the default values
                                                            System.out.println("[last item is not checked ] " + value);
                                                            System.out.println("[defualt value ] " + ((LookupSwitchStmt) pair.getUnit()).getDefaultTarget().toString());
                                                        }
                                                    }
                                                }
                                            } else if (unit instanceof TableSwitchStmt) {
                                                System.out.println(" -- here is a tableSwitch statement ");
                                            }

                                        }
                                    }
                                }
                            }
                        } else
                            System.out.println("[warning] not an instance invoke expression " + invokeExpr.toString());
                        // I should check for the case whether the value of it is checked in the if condition below it
                    }
                } else {
                    // (6-2) Not a state-related call : pass the states of successor to it as inValue: done before :)
                }
            }
        }
        // }
        // (8) Does @afterCall have state of current unit? Let's update it
        if (afterCall.containsKey(unit)) {
            if (outValue.get(unit) != null) {
                currentState.addStates(afterCall.get(unit).getStateMap());
                currentState.updateTargetSet(afterCall.get(unit).getTargets());
                for (Variable variable : afterCall.get(unit).getVarSet())
                    currentState.setVar(variable);
                currentState.setIds(outValue.get(unit).getIds());
                afterCall.remove(unit);
                afterCall.put(unit, currentState);
            }
        } else {
            if (currentState.isReachable()) afterCall.put(unit, currentState);
        }
        //System.out.println("Method " + context.getMethod().toString());
        //System.out.println("Local : outvalue  " + outValue.size() + " afterCall " + afterCall.size() + " on " + unit.toString());

        return afterCall;
    }

    private ArrayList<Variable> getNewVar(Body activeBody, States currentState, Unit unit1, Value lhsop) {
        ArrayList<Variable> newVars = new ArrayList<>();

        // (1) Add the new Variable to the list
        if (lhsop instanceof Local) {
            if (!currentState.hasVar(new LocalVariable((Local) lhsop))) {
                newVars.add(new LocalVariable((Local) lhsop));
            }

        } else if (lhsop instanceof StaticFieldRef) {
            if (!currentState.hasVar(new GlobalVariable(((StaticFieldRef) lhsop).getFieldRef(), ((StaticFieldRef) lhsop).getField()))) {
                newVars.add(new GlobalVariable(((StaticFieldRef) lhsop).getFieldRef(), ((StaticFieldRef) lhsop).getField()));
            }
        } else if (lhsop instanceof InstanceFieldRef) {
            if (!currentState.hasVar(new LocalVariable((Local) lhsop, ((InstanceFieldRef) lhsop).getField(), ((InstanceFieldRef) lhsop).getFieldRef()))) {
                newVars.add(new LocalVariable((Local) ((InstanceFieldRef) lhsop).getBase(), ((InstanceFieldRef) lhsop).getField(), ((InstanceFieldRef) lhsop).getFieldRef()));
            }

        }

        // (2) Check for aliases in the same method context
        ExceptionalUnitGraph graph = new ExceptionalUnitGraph(activeBody);
        SimpleLocalDefs localDefs = new SimpleLocalDefs(graph);
        SimpleLocalUses localUses = new SimpleLocalUses(graph, localDefs);
        List<UnitValueBoxPair> pairs = localUses.getUsesOf(unit1);

        // ( - ) This can be done recursively to also add other defs and aliases as well.

        for (UnitValueBoxPair pair : pairs) {
            // Is it an Assignemnt ?
            if (pair.getUnit() instanceof DefinitionStmt) {
                //Just add the value to it for later use and add it to VarSet values :)
                // add the variable on the lhs in the varSet
                lhsop = ((DefinitionStmt) pair.getUnit()).getLeftOp();
                if (lhsop instanceof Local) {
                    newVars.add(new LocalVariable((Local) lhsop));
                } else if (lhsop instanceof InstanceFieldRef) {
                    newVars.add(new LocalVariable((Local) ((InstanceFieldRef) lhsop).getBase(), ((InstanceFieldRef) lhsop).getField(), ((InstanceFieldRef) lhsop).getFieldRef()));
                } else if (lhsop instanceof StaticFieldRef) {
                    newVars.add(new GlobalVariable(((InstanceFieldRef) lhsop).getFieldRef(), ((InstanceFieldRef) lhsop).getField()));
                }
            }
        }

        return newVars;
    }

    private boolean checkLocAtCode(Unit previousUnit, Unit temp, UnitPatchingChain units) {
        int labelLoc = 0, gotoLoc = 0;
        boolean yes = units.follows(previousUnit, temp);
        System.out.println(" temp follows previous unit " + yes);
        return yes;
    }

    private int approximateLineNumber(Context<SootMethod, Unit, Map<Unit, States>> context, Unit unit, int currentLine) {
        Unit successOf = null;
        while (currentLine == -1) {
            if (unit instanceof IfStmt) {
                successOf = getSuccessor(unit, context.getControlFlowGraph().getSuccsOf(unit));
                currentLine = successOf.getJavaSourceStartLineNumber();
            } else if (!(unit instanceof ReturnVoidStmt && unit instanceof RetStmt && unit instanceof ReturnStmt)) {
                successOf = context.getControlFlowGraph().getSuccsOf(unit).get(0);
                currentLine = successOf.getJavaSourceStartLineNumber();
            } else
                currentLine = context.getMethod().getJavaSourceStartLineNumber() + context.getMethod().getActiveBody().getUnits().size() + 1;
            unit = successOf;
        }
        return currentLine;
    }

    private List<Unit> getCaseLevelUnits(Unit caseTarget, Body activeBody, Unit nextTarget) {
        List<Unit> blockUnits = new ArrayList<>();
        List<Unit> targets = new ArrayList<>();
        blockUnits.add(caseTarget);
        targets.add(caseTarget);
        boolean isFinished = false;

        while (!isFinished && targets.size() > 0) {
            List<Unit> removedUnits = new ArrayList<>();
            List<Unit> newUnits = new ArrayList<>();
            for (Unit unit1 : targets) {
                List<Unit> successors = icfg.getControlFlowGraph(activeBody.getMethod()).getSuccsOf(unit1);
                for (Unit unit2 : successors)
                    if (!unit2.equals(nextTarget)) {
                        newUnits.add(unit2);
                    } else isFinished = true;
                removedUnits.add(unit1);
            }
            if (removedUnits.size() > 0) targets.removeAll(removedUnits);
            if (newUnits.size() > 0 && !isFinished) {
                for (Unit unit1 : newUnits) {
                    if (!blockUnits.contains(unit1)) {
                        blockUnits.add(unit1);
                        targets.add(unit1);
                    }
                }
            }
        }
        return blockUnits;
    }

    /**
     * this function should be modified regarding the shimple represetation
     *
     * @return
     */
    @Override
    public ProgramRepresentation<SootMethod, Unit> programRepresentation() {
        return (ProgramRepresentation<SootMethod, Unit>) icfg;
    }

    /***
     *
     * @param temp: the successor unit of @unit
     * @param unit :  current unit
     * @return : if the successor unit is in (if) part, return true,
     *           otherwise, return false
     */
    private boolean ifOrElse(Unit temp, Unit unit) {

        Iterator unitIt = unit.getUnitBoxes().iterator();

        while (unitIt.hasNext()) {
            StmtBox stmtBox = (StmtBox) unitIt.next();

            if (unitIt instanceof IfStmt) {
                return ifOrElse(temp, stmtBox.getUnit());

            } else {
                if (stmtBox.getUnit().equals(temp)) {
                    return true;
                } else {
                    return false;
                }
            }
        }

        return false;

    }

    /***
     *
     * @param sootMethod:check if the sootMethod is the target method
     * @return true if method is target
     */
    public boolean isTarget(Unit unit, SootMethod sootMethod) {

        if (sootMethod.getSignature().equals(targetMethodN) && sootMethod.getDeclaringClass().getName().equals(targetClassN)) {
            if (unit.getJavaSourceStartLineNumber() == line) {
                System.out.println("[Target] is found");
                return true;
            } else
                System.out.println("[Target] line is " + unit.getJavaSourceStartLineNumber() + " and should be " + line);
        }
        return false;
    }

    /***
     *
     * @param currentState : state mapping of the successor unit
     * @param innerState : state mapping of the first unit of the function
     * @param arg
     * @param param
     * @return merged state of arg and param
     */

    public Set<Abstraction> mergeStates(States currentState, States innerState, Variable arg, LocalVariable param) {

        Set<Abstraction> mergedStates = new HashSet<>();

        if (currentState != null && innerState != null) {
            // For all states collected in the method
            //      check if there is any abstraction with the variable param
            //      If yes, just pass it to the currentState with the arg variable
            for (Abstraction innerAbstraction : innerState.getStateMap()) {
                Variable innerVar = innerAbstraction.getVariable();
                if (innerVar instanceof LocalVariable) {
                    if (((LocalVariable) innerVar).getLocal().getName().equals(param.getLocal().getName())) {
                        innerAbstraction.setVariable(arg);
                        mergedStates.add(innerAbstraction);
                    }
                }
            }
        }
        return mergedStates;
    }

    public Unit getSuccessor(Unit ifUnit, List<Unit> successors) {
        Unit successor = null;
        for (Unit temp : successors) {
            if (!temp.equals(((IfStmt) ifUnit).getTarget())) {
                successor = temp;
                return successor;
            }
        }
        return successor;
    }

    public Variable getVariable(Value value) {
        if (value instanceof Local) {
            //System.out.println(" --- [local] "+ ((Local) value).getName());
            return new LocalVariable((Local) value);
        } else if (value instanceof StaticFieldRef) {
            //System.out.println(" --- [static field] "+ (((StaticFieldRef) value).getField().getName()));
            //System.out.println(" --- [static fieldRef] "+ ((StaticFieldRef) value).getFieldRef().getSignature());
            //System.out.println("it is a global var "+ value.toString());
            return new GlobalVariable(((StaticFieldRef) value).getFieldRef(), ((StaticFieldRef) value).getField());
        } else if (value instanceof InstanceFieldRef) {
            //System.out.println(" --- [instance field] "+ ((InstanceFieldRef) value).getField().getName());
            //System.out.println(" --- [instance fieldRef] "+ ((InstanceFieldRef) value).getFieldRef().getSignature());
            //System.out.println(" --- [base] " + ((InstanceFieldRef) value).getBase().toString());
            //System.out.println("it is a global var "+ value.toString());
            return new LocalVariable((Local) ((InstanceFieldRef) value).getBase(), ((InstanceFieldRef) value).getField(), ((InstanceFieldRef) value).getFieldRef());
        } else if (value instanceof CastExpr) {
            System.out.println(" --- [cast] " + value.toString());
            Value castValue = ((CastExpr) value).getOp();
            return getVariable(castValue);
        }
        return null;
    }


    @Override
    public Context<SootMethod, Unit, Map<Unit, States>> getContext(SootMethod method, Map<Unit, States> value) {
        // If this method does not have any contexts, then we'll have to return nothing.
        if (!contexts.containsKey(method)) {
            return null;
        }
        boolean equal = false;

        // Backward flow, so check for EXIT FLOWS
        for (Context<SootMethod, Unit, Map<Unit, States>> context : contexts.get(method)) {
            if (value.isEmpty() && context.getExitValue().isEmpty()) return context;
            for (Unit unit : value.keySet()) {
                if (context.getExitValue().containsKey(unit)) {
                    if (value.get(unit).isReachable() == context.getExitValue().get(unit).isReachable() && value.get(unit).equalIdSets(value.get(unit).getIds(), context.getExitValue().get(unit).getIds()) && value.get(unit).equalStateMap(value.get(unit).getStateMap(), context.getExitValue().get(unit).getStateMap()) && value.get(unit).equalVarSets(value.get(unit).getVarSet(), context.getExitValue().get(unit).getVarSet())) {
                        equal = true;
                    }
                } else {
                    equal = false;
                    break;
                }
            }
            if (equal) return context;
        }
        return null;
    }


    // Get the ICFG
    public InterProceduralCFGRepresentation getIcfg() {
        return icfg;
    }

    // Set the ICFG
    public void setIcfg(InterProceduralCFGRepresentation icfg) {
        this.icfg = icfg;
    }

    public ArrayList<String> getFragmentList() {
        return fragmentList;
    }


}
