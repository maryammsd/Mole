package dev.maryam.ReachabilityAnalysis.Instrumentation;

import dev.maryam.ReachabilityAnalysis.Analysis.GlobalVariable;
import dev.maryam.ReachabilityAnalysis.Analysis.LocalVariable;
import dev.maryam.ReachabilityAnalysis.Analysis.Variable;
import dev.maryam.ReachabilityAnalysis.Attribute.Abstraction;
import dev.maryam.ReachabilityAnalysis.Attribute.State;
import dev.maryam.ReachabilityAnalysis.Attribute.StateSet;
import dev.maryam.ReachabilityAnalysis.Attribute.States;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JEqExpr;
import soot.jimple.internal.JNeExpr;
import soot.util.Chain;

import java.util.*;

public class MethodCallInjector extends BodyTransformer {

    private static Map<String, States> UIReachableCallbacks = new HashMap<>(), runMethods = new HashMap<>();
    private static Set<String> UIUnreachableCallbacks = new HashSet<>(), reachableCallbacks, unreachableCallbacks;
    private StateSet stateSet;
    private static int abstractionCount = 0;
    private boolean event_enforce = false, reachable_enforce = false;
    private int threshold = 10;
    private static int neccessary_events = 0, irrelevant_events = 0;
    private static HashMap<String, Integer> foundStates = new HashMap<String, Integer>();
    private static HashMap<String, Integer> RCallbacks = new HashMap<String, Integer>();
    private static HashMap<String, Integer> ECallbacks = new HashMap<String, Integer>();

    public MethodCallInjector(Map<String, States> UIReachable, Set<String> UIUnReachable, Set<String> reachableCallbacks, Set<String> unreachableCallbacks, StateSet stateSet, Map<String, States> runMethods, int analysisType) {
        super();
        UIReachableCallbacks = UIReachable;
        UIUnreachableCallbacks = UIUnReachable;
        this.reachableCallbacks = reachableCallbacks;
        this.unreachableCallbacks = unreachableCallbacks;
        this.unreachableCallbacks = unreachableCallbacks;
        this.runMethods = runMethods;
        if (stateSet != null) this.stateSet = stateSet;
        switch (analysisType) {
            case 0:
                reachable_enforce = false;
                event_enforce = false;
                break;
            case 1:
                event_enforce = false;
                reachable_enforce = true;
                break;
            case 2:
                reachable_enforce = true;
                event_enforce = true;
                break;
        }
    }

    @Override
    protected void internalTransform(Body b, String phaseName, Map<String, String> options) {

        if (b.getMethod().getDeclaringClass().getPackageName().startsWith("java") || b.getMethod().getDeclaringClass().getPackageName().startsWith("android") || b.getMethod().getDeclaringClass().getPackageName().startsWith("androidx") || b.getMethod().getDeclaringClass().getPackageName().startsWith("google") || b.getMethod().getDeclaringClass().getPackageName().startsWith("com.google") || b.getMethod().getDeclaringClass().getPackageName().startsWith("org.spongycastle"))
            return;

        InstrumentUtil instrumentUtil = new InstrumentUtil();

        // Insert Log Statements and assertions for each necessary UI callbacks to
        // enforce the states needed.
        if (b.getMethod().hasActiveBody()) {

            JimpleBody jBody = (JimpleBody) b;
            UnitPatchingChain units = b.getUnits();
            List<Unit> generated = new ArrayList<>();
            Unit logging = null;


            // (1) Is the method necessary?
            // Here, I should check if it is a UI Callback or Fragment and add the reachability there in evey callbcak functions they have
            if (UIReachableCallbacks.containsKey(b.getMethod().getSignature()) || runMethods.containsKey(b.getMethod().getSignature())) {

                RCallbacks.put(b.getMethod().getSignature(), 1);
                neccessary_events++;
                // create a return statement
                List<Unit> rejectLog = new ArrayList<>();
                rejectLog = instrumentUtil.generateLogStmts(jBody, "rejected" + b.getMethod().getName() + " " + b.getMethod().getDeclaringClass());
                Unit rejectStatement = rejectLog.get(0);
                Unit firstNonIdentityStmt = jBody.getFirstNonIdentityStmt();

                // (2) add a statement getting the system time
                // add starting time before first non-identity statment
                // (2) insert log statements for tracking the reachable methods
                generated.addAll(instrumentUtil.getnerateTime(jBody, "start necessary: " + b.getMethod().getName() + " " + b.getMethod().getDeclaringClass()));
                logging = generated.get(generated.size() - 1);
                units.insertBefore(generated, firstNonIdentityStmt);

                LocalGenerator lg1 = Scene.v().createLocalGenerator(jBody);
                Local isRejected = lg1.generateLocal(IntType.v());
                AssignStmt rejectBoolean = Jimple.v().newAssignStmt(isRejected, IntConstant.v(1));
                generated.add(rejectBoolean);

                AssignStmt notrejectBoolean = Jimple.v().newAssignStmt(isRejected, IntConstant.v(0));
                EqExpr eqExprReject = Jimple.v().newEqExpr(isRejected, IntConstant.v(0));
                IfStmt rejectChecking = Jimple.v().newIfStmt(eqExprReject, firstNonIdentityStmt);

                // (3) Get stateMaps and add the corresponding if-conditions here to make sure the
                //      state are added to the beginning of the code and met upon execution of the callback
                States methodStates;
                if (b.getMethod().getSignature().contains("void run()"))
                    methodStates = runMethods.get(b.getMethod().getSignature());
                else methodStates = UIReachableCallbacks.get(b.getMethod().getSignature());

                // (4) add the if condition at the beginning of the code after first non-identity codes to make sure the
                //      required states are enforced at runtime
                //
                // Since
                //      1) there should be a variable X defined before use and
                //      2) can be variable dependency,
                //  it is necessary to shift the define statement as well as the previous dependent variables at the beginning of the function
                //  before the first non-identity statement. Add them one after the other and then add the if-conditions .

                Unit lastStmt = null;
                HashMap<Local, Local> newVars = new HashMap<>();

                // 17) create a label and nop to jump to if the equality is true
                NopStmt nop = Jimple.v().newNopStmt();
                generated = new ArrayList<>();
                abstractionCount += methodStates.getStateMap().size();
                Set<Abstraction> abstractionSet = methodStates.getStateMap();
                abstractionSet = removeDuplicateAbstractions(abstractionSet);
                boolean hasSeveralEvents = false;
                for (Abstraction abstraction : abstractionSet) {
                    State state = abstraction.getState();
                    // get variable to call method on it
                    Variable var = abstraction.getVariable();
                    // get value required to be met at runtime !
                    int value = abstraction.getValue();
                    // get the method signature
                    String methodCall = state.getMethodSignature();

                    boolean isEvent = methodCall.contains("getItemId") || methodCall.contains("getId") || methodCall.contains("getAction") || methodCall.contains("getKeyCode");
                    hasSeveralEvents = isEvent;
                    if (foundStates != null) {
                        if (value > 0) {
                            if (foundStates.get(methodCall + "1") != null) {
                                int count = foundStates.get(methodCall + "1");
                                foundStates.remove(methodCall + "1");
                                count++;
                                foundStates.put(methodCall + "1", count);
                            } else
                                foundStates.put(methodCall + "1", 1);
                            if (isEvent) {
                                neccessary_events++;
                            }
                        } else {
                            if (foundStates.get(methodCall + "-1") != null) {
                                int count = foundStates.get(methodCall + "-1");
                                foundStates.remove(methodCall + "-1");
                                count++;
                                foundStates.put(methodCall + "-1", count);
                            } else
                                foundStates.put(methodCall + "-1", 1);
                            if (isEvent) {
                                irrelevant_events++;
                            }
                        }
                    }

                    if (ECallbacks != null) {
                        if (isEvent) {
                            if (ECallbacks.containsKey(methodCall)) {
                                int count = ECallbacks.get(methodCall);
                                ECallbacks.remove(methodCall);
                                count++;
                                ECallbacks.put(methodCall, count);
                            } else
                                ECallbacks.put(methodCall, 1);
                        }
                    }
                    // (6) find signature of the call

                    Local targetVar = getLocal(b.getLocals(), ((LocalVariable) var).getLocal());

                    Unit targetUnit = null;

                    if (targetVar != null) {
                        // 6) Is there any definition statement for rhs of @var def-site ?
                        //      If yes, find them. add them to the list of units and go to 6)
                        //      If no, done.
                        // (7) which units are target units having use-sites of the state we have ?
                        List<Unit> targetUnits = removeDuplicate(getTargetUnit(units, methodCall, targetVar));

                        // (8) Let's find the def-sites of target @var
                        //      -- A variable can be defined in a unit prior to where it is located
                        //      -- A variable can be defined as a parameter of a function
                        if (targetUnits.size() == 1) {

                            targetUnit = targetUnits.get(0);

                            if (units.contains(targetUnit)) {
                                boolean finish = false;
                                Unit temp = targetUnit;

                                // Mapping of list number to where the def-site should be added for a local variable
                                //          (list of locations in @defs the variable was used before )
                                Map<Local, ArrayList<Integer>> localToUseList = new HashMap<>();
                                // Mapping of number of times a variable is defined
                                Map<Local, Integer> localToDefList = new HashMap<>();

                                List<Local> variables = new ArrayList<>();

                                List<List<Unit>> defs = new ArrayList<>();

                                // (9) get variables of targetUnit
                                variables = getDefVariable(targetUnit);
                                if (targetUnit instanceof AssignStmt) {
                                    Value lhs = ((AssignStmt) targetUnit).getLeftOp();
                                    if (lhs instanceof Local) localToDefList.put((Local) lhs, 1);
                                }
                                // (10) initializes @localToDefList: add the vars to the mapping with pointer to first array in @defs
                                for (Local local : variables) {
                                    ArrayList<Integer> locs = new ArrayList<>();
                                    locs.add(0);
                                    localToUseList.put(local, locs);
                                }
                                // (11) initializes @defs: add @targetUnit in first array of @defs
                                ArrayList<Unit> tempList = new ArrayList();
                                tempList.add(targetUnit);
                                defs.add(tempList);


                                //(12) get the lists of def statements necessary for having the state checking
                                while (!finish) {
                                    // (1) What is the predecessor ?
                                    Unit pred = units.getPredOf(temp);

                                    // (2) Does it have any def-site of variables in @varList
                                    //      yes: add it to the chain , add variables in its rhs to the @varList
                                    //      no: skip it
                                    if (pred instanceof AssignStmt) {

                                        // (3) What is lhs ? Is it Local or Field access  ?
                                        Value lhs = ((AssignStmt) pred).getLeftOp();
                                        Value rhs = ((AssignStmt) pred).getRightOp();

                                        if (lhs instanceof Local) {

                                            // (4) Was @lhs used before ?
                                            if (localToUseList.containsKey(lhs)) {

                                                // (5) get variables at def-site of lhs
                                                //      add rhs variables to @varList
                                                variables = getDefVariable(pred);

                                                // (6) add @pred to list of def-sites
                                                //      where should we add?
                                                //      in locs where there is at least one use-site of it
                                                ArrayList<Integer> whereToAddDef = new ArrayList<>();

                                                whereToAddDef = localToUseList.get((Local) lhs);

                                                int numberOfdefsBefore = (localToDefList.containsKey((Local) lhs)) ? localToDefList.get((Local) lhs) : 0;

                                                if (whereToAddDef != null) {
                                                    if (numberOfdefsBefore == 0) {
                                                        // get ith list in @defs
                                                        for (int i : whereToAddDef) {
                                                            // add the pred to it
                                                            if (!defs.get(i).contains(pred)) defs.get(i).add(pred);
                                                            // add the rhs variables and arguments of invocation to the localToDefs variables
                                                            for (Local local : variables) {
                                                                if (localToUseList.containsKey(local)) {
                                                                    if (!localToUseList.get(local).contains(i))
                                                                        localToUseList.get(local).add(i);
                                                                    else {
                                                                        ArrayList<Integer> locs = new ArrayList<>();
                                                                        locs.add(i);
                                                                        localToUseList.put(local, locs);
                                                                    }
                                                                } else {
                                                                    ArrayList<Integer> locs = new ArrayList<>();
                                                                    locs.add(i);
                                                                    localToUseList.put(local, locs);
                                                                }
                                                            }
                                                        }

                                                    } else if (numberOfdefsBefore > 0) {
                                                        ArrayList<Integer> newLocs = new ArrayList<>();
                                                        for (int i : whereToAddDef) {
                                                            List<Unit> newList = defs.get(i);
                                                            // Is there any def-site of @lhs ?
                                                            Unit beforeDef = getPreviousDef(lhs, newList);
                                                            // Yes: duplicate the list in @defs and replace it with @pred,
                                                            //      add new loc to a temporary list to update @localToUseList for @variables
                                                            if (beforeDef != null) {
                                                                newList.add(pred);
                                                                newList.remove(beforeDef);
                                                                defs.add(newList);
                                                                newLocs.add(defs.size() - 1);
                                                            }
                                                        }
                                                        for (int i : newLocs) {
                                                            for (Local local : variables) {
                                                                if (localToUseList.containsKey(local))
                                                                    if (!localToUseList.get(local).contains(i))
                                                                        localToUseList.get(local).add(i);
                                                                    else {
                                                                        ArrayList<Integer> locs = new ArrayList<>();
                                                                        locs.add(i);
                                                                        localToUseList.put(local, locs);
                                                                    }
                                                            }
                                                        }
                                                    }

                                                }

                                                // (7) Update # of times @lhs is defined
                                                if (localToDefList.containsKey((Local) lhs)) {
                                                    localToDefList.remove((Local) lhs);
                                                    localToDefList.put((Local) lhs, numberOfdefsBefore + 1);
                                                } else localToDefList.put((Local) lhs, 1);
                                            }

                                        } else if (lhs instanceof FieldRef) {
                                            // Get rhs and see if it is an InstanceField

                                            if (lhs instanceof InstanceFieldRef) {
                                                // Is base among @localUseList ?
                                                Value base = ((InstanceFieldRef) lhs).getBase();
                                                if (localToUseList.containsKey((Local) base)) {

                                                    // get variables at def-site of base
                                                    //      add rhs variables to @varList
                                                    variables = getDefVariable(pred);

                                                    // add @pred to list of def-sites
                                                    // where should we add?
                                                    //      in locs where there is at least one use-site of it
                                                    ArrayList<Integer> whereToAddDef = new ArrayList<>();

                                                    whereToAddDef = localToUseList.get((Local) base);

                                                    int numberOfdefsBefore = (localToDefList.containsKey((Local) base)) ? localToDefList.get((Local) base) : 0;

                                                    if (whereToAddDef != null) {
                                                        if (numberOfdefsBefore == 0) {
                                                            // get ith list in @defs
                                                            for (int i : whereToAddDef) {
                                                                // add the pred to it
                                                                if (!defs.get(i).contains(pred))
                                                                    defs.get(i).add(pred);
                                                                // add the rhs variables and arguments of invocation to the localToDefs variables
                                                                for (Local local : variables) {
                                                                    if (localToUseList.containsKey(local)) {
                                                                        if (!localToUseList.get(local).contains(i))
                                                                            localToUseList.get(local).add(i);
                                                                        else {
                                                                            ArrayList<Integer> locs = new ArrayList<>();
                                                                            locs.add(i);
                                                                            localToUseList.put(local, locs);
                                                                        }
                                                                    } else {
                                                                        ArrayList<Integer> locs = new ArrayList<>();
                                                                        locs.add(i);
                                                                        localToUseList.put(local, locs);
                                                                    }
                                                                }
                                                            }

                                                        } else if (numberOfdefsBefore > 0) {
                                                            ArrayList<Integer> newLocs = new ArrayList<>();
                                                            for (int i : whereToAddDef) {
                                                                List<Unit> newList = defs.get(i);
                                                                // Is there any def-site of @base ?
                                                                Unit beforeDef = getPreviousDef(base, newList);
                                                                // Yes: duplicate the list in @defs and replace it with @pred,
                                                                //      add new loc to a temporary list to update @localToUseList for @variables
                                                                if (beforeDef != null) {
                                                                    newList.add(pred);
                                                                    newList.remove(beforeDef);
                                                                    defs.add(newList);
                                                                    newLocs.add(defs.size() - 1);
                                                                }
                                                            }
                                                            for (int i : newLocs) {
                                                                for (Local local : variables) {
                                                                    if (localToUseList.containsKey(local))
                                                                        if (!localToUseList.get(local).contains(i))
                                                                            localToUseList.get(local).add(i);
                                                                        else {
                                                                            ArrayList<Integer> locs = new ArrayList<>();
                                                                            locs.add(i);
                                                                            localToUseList.put(local, locs);
                                                                        }
                                                                }
                                                            }
                                                        }

                                                    }

                                                    // Update # of times @base is defined
                                                    if (localToDefList.containsKey((Local) base)) {
                                                        localToDefList.remove((Local) base);
                                                        localToDefList.put((Local) base, numberOfdefsBefore + 1);
                                                    } else localToDefList.put((Local) base, 1);
                                                }
                                            }

                                        }
                                    }
                                    // (3) let's go to the predecessor of it
                                    temp = pred;

                                    // (4) Is it an IdentityStmt ?
                                    //      Yes: we got to the beginning of the funciton
                                    //      no: we should continue
                                    if (pred instanceof IdentityStmt) {
                                        finish = true;
                                        Value lhs = ((IdentityStmt) pred).getLeftOp();
                                        if (newVars.containsKey((Local) lhs)) newVars.remove((Local) lhs);
                                    }
                                }

                                // (8) Let's add the corresponding statements for instrumentation
                                //      to @generated
                                for (List<Unit> list : defs) {

                                    if (list.size() > 0) {
                                        Unit tempUnit = list.get(0);
                                        if (tempUnit instanceof AssignStmt) {
                                            Value rhs = ((AssignStmt) tempUnit).getRightOp();
                                            if (rhs instanceof InstanceInvokeExpr) {
                                                Value base = ((InstanceInvokeExpr) rhs).getBase();
                                                Type baseType = base.getType();
                                                List<Type> argsCallback = b.getMethod().getParameterTypes();
                                                boolean isArgInvolved = false;
                                                if (argsCallback.size() > 0) {
                                                    for (Type t : argsCallback) {
                                                        if (t.equals(baseType)) {
                                                            isArgInvolved = true;
                                                            break;
                                                        }
                                                    }
                                                    if (!isArgInvolved) continue;
                                                }
                                            }
                                        }
                                    }

                                    for (int i = list.size() - 1; i >= 0; i--) {
                                        Unit currentUnit = list.get(i);

                                        if (currentUnit instanceof AssignStmt) {
                                            Value rhsCurrent = ((AssignStmt) currentUnit).getRightOp();
                                            Value lhsCurrent = ((AssignStmt) currentUnit).getLeftOp();

                                            LocalGenerator lg = Scene.v().createLocalGenerator(jBody);
                                            // (9) Is it first statement unit ?
                                            if (i == 0) {
                                                if (!newVars.containsKey(lhsCurrent)) {
                                                    // 12) Create a local parameter X
                                                    Local condition = lg.generateLocal(IntType.v());
                                                    newVars.put((Local) lhsCurrent, condition);
                                                }

                                                // 13) Call the use-site method on the base variable
                                                Local condition = newVars.get(lhsCurrent);
                                                SootMethod sootMethod;
                                                InvokeExpr invokeExpr = null;
                                                if (rhsCurrent instanceof InvokeExpr) {
                                                    // @get method call of rhsCurrent
                                                    methodCall = ((AssignStmt) currentUnit).getInvokeExpr().getMethod().getSignature();
                                                    sootMethod = Scene.v().getMethod(methodCall);
                                                    Local localVar;
                                                    Local temp1 = getLocal(b.getLocals(), ((LocalVariable) var).getLocal());
                                                    if (newVars.containsKey(temp1)) localVar = newVars.get(temp1);
                                                    else {
                                                        localVar = temp1;
                                                        newVars.put(localVar, temp1);
                                                    }
                                                    if (((InvokeExpr) rhsCurrent).getMethod().isAbstract())
                                                        invokeExpr = Jimple.v().newInterfaceInvokeExpr(localVar, sootMethod.makeRef(), ((InvokeExpr) rhsCurrent).getArgs());
                                                    else if (((InvokeExpr) rhsCurrent).getMethod().isConcrete())
                                                        invokeExpr = Jimple.v().newVirtualInvokeExpr(localVar, sootMethod.makeRef(), ((InvokeExpr) rhsCurrent).getArgs());
                                                    else if (((InvokeExpr) rhsCurrent).getMethod().isStatic())
                                                        invokeExpr = Jimple.v().newStaticInvokeExpr(sootMethod.makeRef(), ((InvokeExpr) rhsCurrent).getArgs());
                                                }

                                                // 14) create the X := call statement
                                                AssignStmt assignStmt = Jimple.v().newAssignStmt(condition, invokeExpr);
                                                generated.add(generated.indexOf(lastStmt) + 1, assignStmt);
                                                lastStmt = assignStmt;


                                                // 15) get the value to be compared with X
                                                // V: Value to be compared with
                                                Value temp2;
                                                boolean isEq = false;
                                                if (value >= 0) {
                                                    temp2 = IntConstant.v(value);
                                                    isEq = true;
                                                } else {
                                                    temp2 = IntConstant.v(value * -1);
                                                }// 16) create an equality expression pf condition=zero

                                                //  X == V
                                                EqExpr eqExpr = Jimple.v().newEqExpr(condition, temp2);

                                                // 18) create the if statement,
                                                // if X != V goto Nop
                                                // ... other if statements or return statement if there is no other call
                                                IfStmt ifStmt;
                                                NopStmt nop1 = Jimple.v().newNopStmt();
                                                if (isEq) ifStmt = Jimple.v().newIfStmt(eqExpr, notrejectBoolean);
                                                else ifStmt = Jimple.v().newIfStmt(eqExpr, rejectStatement);
                                                // Add the ifStatement after it
                                                generated.add(generated.indexOf(lastStmt) + 1, ifStmt);
                                                generated.add(nop1);
                                                lastStmt = nop1;
                                                // Is it other def-sites ?
                                            } else if (i == list.size() - 1) {

                                                Local local = lg.generateLocal(lhsCurrent.getType());
                                                newVars.put((Local) lhsCurrent, local);
                                                Value newRhs = generateValue(newVars, rhsCurrent, lhsCurrent);
                                                if (newRhs != null) {
                                                    AssignStmt assignStmt = Jimple.v().newAssignStmt(local, newRhs);
                                                    if (lastStmt == null) generated.add(assignStmt);
                                                    else generated.add(generated.indexOf(lastStmt) + 1, assignStmt);
                                                    lastStmt = assignStmt;
                                                }
                                                // Is it the @targetUnit ?
                                                //  Let's add the conditions to the list
                                            } else {
                                                // (10) add the def-sites after either the @firstNonIdentity statement or the last @ifStmt added
                                                //       Add the added units containing def-sites after @lastStmt

                                                Local newLocal = lg.generateLocal(lhsCurrent.getType());
                                                Value newRhs = generateValue(newVars, rhsCurrent, lhsCurrent);
                                                if (newRhs != null) {
                                                    AssignStmt assignStmt = Jimple.v().newAssignStmt(newLocal, newRhs);
                                                    generated.add(generated.indexOf(lastStmt) + 1, assignStmt);
                                                    lastStmt = assignStmt;
                                                    newVars.put((Local) lhsCurrent, newLocal);
                                                } else
                                                    System.out.println("Warning! there is an error here. What is it ?");
                                            }
                                        }
                                    }
                                }


                            }
                        }
                    }
                    // Add the instrumentation information to log the constraint satisfiability at runtime
                    List<Unit> same = hasUnit(units.getNonPatchingChain(), targetUnit);
                    if (!same.isEmpty()) {
                        for (Unit temp : same) {
                            Unit successUnit = units.getSuccOf(temp);
                            if (successUnit != null) {
                                if (successUnit instanceof IfStmt) {
                                    // Let's get value of the comparison
                                    // Check if the value of the state is equal to the comparison or not
                                    Value condition = ((IfStmt) successUnit).getCondition();
                                    boolean isIf = false;
                                    if (condition instanceof JNeExpr) {
                                        Value op2 = ((JNeExpr) condition).getOp2();
                                        int constVal = ((IntConstant) op2).value;
                                        if (constVal == 0) {
                                            if (value == 0) {
                                                isIf = true;
                                            } else isIf = false;
                                        } else {
                                            if (value == 0) isIf = true;
                                            else isIf = false;
                                        }
                                    } else if (condition instanceof JEqExpr) {
                                        Value op2 = ((JEqExpr) condition).getOp2();
                                        int constVal = ((IntConstant) op2).value;
                                        if (constVal == 0) {
                                            if (value == 0) {
                                                isIf = false;
                                            } else isIf = true;
                                        } else {
                                            if (value == 0) isIf = false;
                                            else isIf = true;
                                        }
                                    }
                                    String message = "<use,";
                                    // let's add the instrumentation code to the after if or to the target part
                                    if (isIf) {
                                        message += ((LocalVariable) var).getLocal().getName() + "," + state.getMethodSignature() + "," + value + ">";
                                        List<Unit> messageDef = new ArrayList<>();
                                        System.out.println(" message " + message);
                                        messageDef.addAll(instrumentUtil.getnerateTime(jBody, "Beginning of Reachable method " + b.getMethod().getSignature() + " " + message));
                                        units.insertAfter(messageDef, successUnit);
                                    } else {
                                        message += ((LocalVariable) var).getLocal().getName() + "," + state.getMethodSignature() + "," + (value * (-1)) + ">";
                                        List<Unit> messageDef = new ArrayList<>();
                                        messageDef.addAll(instrumentUtil.getnerateTime(jBody, "Beginning of Reachable method " + b.getMethod().getSignature() + " " + message));
                                        if (((IfStmt) successUnit).getTargetBox().getUnit() instanceof GotoStmt) {
                                            Unit pred = units.getPredOf(((IfStmt) successUnit).getTargetBox().getUnit());
                                            ((IfStmt) successUnit).setTarget(messageDef.get(0));
                                            units.insertAfter(messageDef, pred);
                                        } else {
                                            units.insertAfter(messageDef, ((IfStmt) successUnit).getTargetBox().getUnit());
                                        }
                                    }

                                }
                            }
                        }
                    }
                }

                if (hasSeveralEvents)
                    neccessary_events--;
                // Add the instrumentation information to log the definition statements at runtime
                for (Unit targetUnit : methodStates.getTargets()) {
                    List<Unit> same = hasUnit(units.getNonPatchingChain(), targetUnit);
                    if (!same.isEmpty()) {
                        boolean isDef = false;
                        String message = "<def,";
                        if (targetUnit instanceof AssignStmt) {
                            Value rhs = ((AssignStmt) targetUnit).getRightOp();
                            if (rhs instanceof InvokeExpr) {
                                String methodSignature = ((InvokeExpr) rhs).getMethod().getSubSignature();
                                int defState = stateSet.getIsDef(methodSignature);
                                if (defState == 1) {
                                    isDef = true;
                                    message += methodSignature + "," + ((InvokeExpr) rhs).getArgs().toString() + ">";
                                }
                            }
                        } else if (targetUnit instanceof InvokeStmt) {
                            String methodSignature = ((InvokeStmt) targetUnit).getInvokeExpr().getMethod().getSubSignature();
                            int defState = stateSet.getIsDef(methodSignature);
                            if (defState == 1) {
                                isDef = true;
                                message += methodSignature + "," + ((InvokeStmt) targetUnit).getInvokeExpr().getArgs().toString() + ">";
                            }
                        }
                        if (isDef) {
                            System.out.println(" --- message " + message);
                            // add the instrumentation immediately after def-statement
                            for (Unit temp : same) {
                                List<Unit> messageDef = new ArrayList<>();
                                messageDef.addAll(instrumentUtil.getnerateTime(jBody, "start necessary: " + b.getMethod().getSignature() + " " + message));
                                units.insertAfter(messageDef, temp);
                            }
                        }
                    }

                }

                if (generated.size() > 0) { // && lastStmt instanceof IfStmt
                    // create a return statement
                    units.insertAfter(generated, logging);
                    units.insertAfter(notrejectBoolean, lastStmt);
                    units.insertAfter(rejectChecking, notrejectBoolean);
                    units.insertAfter(rejectLog, rejectChecking);

                    lastStmt = rejectLog.get(rejectLog.size() - 1);

                    List<Unit> endingUnits = new ArrayList<>();
                    // add ending time before first non-identity statment
                    endingUnits.addAll(instrumentUtil.getnerateTime(jBody, "end necessary: " + b.getMethod().getName() + " " + b.getMethod().getDeclaringClass()));
                    units.insertAfter(endingUnits, lastStmt);
                    lastStmt = endingUnits.get(endingUnits.size() - 1);
                    if (event_enforce) {
                        if (b.getMethod().getReturnType() instanceof VoidType) {
                            ReturnVoidStmt returnVoidStmt = Jimple.v().newReturnVoidStmt();
                            units.insertAfter(returnVoidStmt, lastStmt);
                        } else if (b.getMethod().getReturnType() instanceof PrimType) {
                            ReturnStmt returnStmt = Jimple.v().newReturnStmt(IntConstant.v(0));
                            units.insertAfter(returnStmt, lastStmt);
                        } else {
                            ReturnStmt returnStmt = Jimple.v().newReturnStmt(NullConstant.v());
                            units.insertAfter(returnStmt, lastStmt);
                        }
                    }
                }
                List<Unit> endingUnits = new ArrayList<>();
                endingUnits.addAll(instrumentUtil.getnerateTime(jBody, "end necessary: " + b.getMethod().getName() + " " + b.getMethod().getDeclaringClass()));
                units.insertBefore(endingUnits, firstNonIdentityStmt);

            } else if (unreachableCallbacks.contains(b.getMethod().getSignature()) || UIUnreachableCallbacks.contains(b.getMethod().getSignature())) {
                // Call the class we have already created
                RCallbacks.put(b.getMethod().getSignature(), 0);

                // Count the number of irrelevant events
                irrelevant_events += getNumberOfEvents(b.getMethod());

                Unit firstNonIdentityStmt = jBody.getFirstNonIdentityStmt();

                // create an integer of value zero
                IntConstant zero = IntConstant.v(0);
                LocalGenerator lg = Scene.v().createLocalGenerator(jBody);
                Local condition = lg.generateLocal(IntType.v());

                // create an equality expression pf condition=zero
                EqExpr eqExpr = Jimple.v().newEqExpr(condition, zero);
                Value one = IntConstant.v(1);

                // assign value @one = 1 to the local variable @condition
                AssignStmt assignStmt = Jimple.v().newAssignStmt(condition, one);
                units.insertBefore(assignStmt, firstNonIdentityStmt);

                // create a label and nop to jump to if the equality is true
                NopStmt nop = Jimple.v().newNopStmt();

                // create the if statement,
                IfStmt ifStmt = Jimple.v().newIfStmt(eqExpr, nop);
                units.insertAfter(ifStmt, assignStmt);

                // add ending time before first non-identity statment
                // add the statements one another to the list
                generated.addAll(instrumentUtil.getnerateTime(jBody, "start irrelevant: " + b.getMethod().getName() + " " + b.getMethod().getDeclaringClass()));
                units.insertAfter(generated, ifStmt);

                if (reachable_enforce || event_enforce) {
                    Unit lastStmt = generated.get(generated.size() - 1);
                    // create a return statement
                    if (b.getMethod().getReturnType() instanceof VoidType) {
                        ReturnVoidStmt returnVoidStmt = Jimple.v().newReturnVoidStmt();
                        units.insertAfter(returnVoidStmt, lastStmt);
                    } else if (b.getMethod().getReturnType() instanceof PrimType) {
                        ReturnStmt returnStmt = Jimple.v().newReturnStmt(IntConstant.v(0));
                        units.insertAfter(returnStmt, lastStmt);
                    } else {
                        ReturnStmt returnStmt = Jimple.v().newReturnStmt(NullConstant.v());
                        units.insertAfter(returnStmt, lastStmt);
                    }

                }
                // add the statements one another to the list
                units.insertBefore(nop, firstNonIdentityStmt);


                generated = new ArrayList<>();
                // add starting time before first non-identity statment
                generated.addAll(instrumentUtil.getnerateTime(jBody, "end irrelevant: " + b.getMethod().getName() + " " + b.getMethod().getDeclaringClass()));
                units.insertBefore(generated, jBody.getFirstNonIdentityStmt());

            }

            b.validate();
        }

    }

    private Set<Abstraction> removeDuplicateAbstractions(Set<Abstraction> abstractionSet) {
        Set<Abstraction> uniqueSet = new HashSet<>();
        for (Abstraction abstraction : abstractionSet) {
            if (uniqueSet.size() > 0) {
                boolean isFound = false;
                for (Abstraction abstraction1 : uniqueSet) {
                    if (abstraction.getState().getState() == abstraction1.getState().getState() && abstraction.getValue() == abstraction1.getValue()) {
                        if (abstraction.getVariable().getVariableType() == abstraction1.getVariable().getVariableType()) {
                            if (abstraction.getVariable() instanceof LocalVariable) {
                                if (((LocalVariable) abstraction.getVariable()).getLocal().toString().equals(((LocalVariable) abstraction1.getVariable()).getLocal().toString()))
                                    isFound = true;
                            } else {
                                if (((GlobalVariable) abstraction.getVariable()).getGlobal().toString().equals(((GlobalVariable) abstraction1.getVariable()).getGlobal().toString()))
                                    isFound = true;
                            }
                        }

                    }
                }
                if (!isFound)
                    uniqueSet.add(abstraction);
            } else {
                uniqueSet.add(abstraction);
            }
        }
        return uniqueSet;
    }

    private Value generateValue(HashMap<Local, Local> newVars, Value rhsCurrent, Value lhsCurrent) {
        Value newRhs = null;

        if (lhsCurrent instanceof Local) {
            if (rhsCurrent instanceof Constant) {
                newRhs = rhsCurrent;
            } else if (rhsCurrent instanceof Local) {
                if (newVars.containsKey(lhsCurrent)) {
                    newRhs = newVars.get(lhsCurrent);
                }
            } else if (rhsCurrent instanceof FieldRef) {
                if (rhsCurrent instanceof InstanceFieldRef) {
                    Value base = ((InstanceFieldRef) rhsCurrent).getBase();
                    if (newVars.containsKey((Local) base))
                        newRhs = Jimple.v().newInstanceFieldRef(newVars.get((Local) base), ((InstanceFieldRef) rhsCurrent).getFieldRef());
                    else newRhs = Jimple.v().newInstanceFieldRef(base, ((InstanceFieldRef) rhsCurrent).getFieldRef());
                } else if (rhsCurrent instanceof StaticFieldRef) {
                    newRhs = Jimple.v().newStaticFieldRef(((FieldRef) rhsCurrent).getFieldRef());
                }
            } else if (rhsCurrent instanceof BinopExpr) {

            } else if (rhsCurrent instanceof UnopExpr) {

            } else if (rhsCurrent instanceof CastExpr) {
                Value rhsVar = ((CastExpr) rhsCurrent).getOp();
                if (newVars.containsKey((Local) rhsVar)) {
                    newRhs = Jimple.v().newCastExpr(newVars.get((Local) rhsVar), ((CastExpr) rhsCurrent).getCastType());
                } else newRhs = Jimple.v().newCastExpr(rhsVar, ((CastExpr) rhsCurrent).getCastType());

            } else if (rhsCurrent instanceof InvokeExpr) {

                List<Value> args = ((InvokeExpr) rhsCurrent).getArgs();
                for (int i = 0; i < args.size(); i++) {
                    Value arg = args.get(i);
                    if (arg instanceof Local)
                        if (newVars.containsKey((Local) arg)) args.set(i, newVars.get((Local) arg));
                    // else
                    //    System.out.println(" argtype is " + arg.getType().toString());
                }
                if (rhsCurrent instanceof InstanceInvokeExpr) {
                    if (newVars.containsKey((Local) ((InstanceInvokeExpr) rhsCurrent).getBase())) {
                        if (((InstanceInvokeExpr) rhsCurrent).getMethod().isAbstract()) {
                            newRhs = Jimple.v().newInterfaceInvokeExpr(newVars.get(((InstanceInvokeExpr) rhsCurrent).getBase()), ((InstanceInvokeExpr) rhsCurrent).getMethodRef(), args);
                        } else if (((InstanceInvokeExpr) rhsCurrent).getMethod().isConcrete()) {
                            newRhs = Jimple.v().newVirtualInvokeExpr(newVars.get(((InstanceInvokeExpr) rhsCurrent).getBase()), ((InstanceInvokeExpr) rhsCurrent).getMethodRef(), args);
                        } else if (((InstanceInvokeExpr) rhsCurrent).getMethod().isStatic()) {
                            newRhs = Jimple.v().newStaticInvokeExpr(((InstanceInvokeExpr) rhsCurrent).getMethodRef(), args);
                        }
                    } else {
                        if (((InstanceInvokeExpr) rhsCurrent).getMethod().isAbstract()) {
                            newRhs = Jimple.v().newInterfaceInvokeExpr((Local) ((InstanceInvokeExpr) rhsCurrent).getBase(), ((InstanceInvokeExpr) rhsCurrent).getMethodRef(), args);
                        } else if (((InstanceInvokeExpr) rhsCurrent).getMethod().isConcrete()) {
                            newRhs = Jimple.v().newVirtualInvokeExpr((Local) ((InstanceInvokeExpr) rhsCurrent).getBase(), ((InstanceInvokeExpr) rhsCurrent).getMethodRef(), args);
                        } else if (((InstanceInvokeExpr) rhsCurrent).getMethod().isStatic()) {
                            newRhs = Jimple.v().newStaticInvokeExpr(((InstanceInvokeExpr) rhsCurrent).getMethodRef(), args);
                        }
                    }
                } else if (rhsCurrent instanceof StaticInvokeExpr) {
                    newRhs = Jimple.v().newStaticInvokeExpr(((StaticInvokeExpr) rhsCurrent).getMethodRef(), args);
                }
            }
        }
        return newRhs;
    }

    private Unit getPreviousDef(Value lhs, List<Unit> newList) {
        Unit previousUnit = null;
        for (Unit unit : newList) {
            if (unit instanceof AssignStmt) {
                Value temp = ((AssignStmt) unit).getLeftOp();
                if (lhs.equals(temp)) previousUnit = unit;
            }
        }
        return previousUnit;
    }

    private List<Local> getDefVariable(Unit unit) {

        boolean noLocal = false;
        List<Local> rhsLocals = new ArrayList<>();
        // (1) An assignment unit can be a def-site
        if (unit instanceof AssignStmt) {

            Value rhs = ((AssignStmt) unit).getRightOp();
            // (4) what type is the rhs?
            //      - Is it a castExpr ? x := (TYPE) y
            //          Check for def-sites of y and add them to the @def_sites
            //      - Is it a newExpr ? x := New TYPE , just add it to the @def_sites if not already exists
            //      - Is it an invocation ? x := call y.foo(), find the def-sites of y accordingly and add them to @def_sites

            // x : =  (Y) y
            if (rhs instanceof CastExpr) {
                rhs = ((CastExpr) rhs).getOp();
            } else if (rhs instanceof NewExpr) {
                // x := New X
                noLocal = true;
            } else if (rhs instanceof InvokeExpr) {
                //  x : = a.foo() or x := foo()/ foo(a,b,c) (-) should we also collect the value of a,b and c ?
                List<Value> args = new ArrayList<>();
                if (rhs instanceof InstanceInvokeExpr) {
                    args = ((InstanceInvokeExpr) rhs).getArgs();
                    rhs = ((InstanceInvokeExpr) rhs).getBase();
                } else if (rhs instanceof StaticInvokeExpr) {
                    noLocal = true;
                    args = ((StaticInvokeExpr) rhs).getArgs();
                }
                for (Value arg : args) {
                    if (arg instanceof Local) {
                        rhsLocals.add((Local) arg);
                    }
                }

            } else if (rhs instanceof FieldRef) {
                // x := a.<field> or x := <field>
                if (rhs instanceof InstanceFieldRef) {
                    rhs = ((InstanceFieldRef) rhs).getBase();
                } else if (rhs instanceof StaticFieldRef) {
                    noLocal = true;
                }

            }

            // Is rhs retrieved from above local ?
            if (rhs instanceof Local && !(rhs instanceof Constant)) rhsLocals.add((Local) rhs);

        }
        return rhsLocals;
    }

    private Local getLocal(Chain<Local> locals, Local local) {
        Local target = null;
        for (Local temp : locals) {
            if (local.getName().equals(temp.getName())) target = temp;
        }
        return target;
    }

    private int getNumberOfEvents(SootMethod sootMethod) {
        int count = 1;
        if (sootMethod.hasActiveBody()) {
            UnitPatchingChain units = sootMethod.getActiveBody().getUnits();
            for (Unit unit : units) {
                if (unit instanceof LookupSwitchStmt) {
                    System.out.println("[Key Type] " + ((LookupSwitchStmt) unit).getKey().getType());
                    System.out.println("[Value Type] " + unit.getTags().toString());
                    System.out.println("[Event Counts] " + ((LookupSwitchStmt) unit).getTargetCount());
                    System.out.println("[Event Values] " + ((LookupSwitchStmt) unit).getLookupValues());
                }
            }
        }
        return count;
    }

    private List<Unit> getTargetUnit(PatchingChain<Unit> units, String methodCall, Local var) {
        List<Unit> foundUnits = new ArrayList<>();
        for (Unit unit : units) {
            if (unit instanceof AssignStmt) {
                Value rhs = ((AssignStmt) unit).getRightOp();
                if (rhs.toString().contains(var.getName()) && rhs.toString().contains(methodCall)) {
                    foundUnits.add(unit);
                }
            }else if(unit instanceof IdentityStmt){
                Value rhs = ((IdentityStmt) unit).getRightOp();
                if(rhs.toString().contains(var.getName()) && rhs.getType().toString().equals(var.getType().toString()))
                    foundUnits.add(unit);
            }
        }
        return foundUnits;
    }

    private List<Unit> removeDuplicate(List<Unit> list) {
        List<Unit> newList = new ArrayList<>();
        for (Unit unit : list) {
            if (!hasUnit(newList, unit)) newList.add(unit);
        }
        return newList;
    }

    private boolean hasUnit(List<Unit> list, Unit unit) {
        for (Unit temp : list) {
            if (unit.getUnitBoxes().equals(temp.getUnitBoxes()) && unit.toString().equals(temp.toString())) return true;
        }
        return false;
    }

    private List<Unit> hasUnit(Chain<Unit> unitChain, Unit unit) {
        List<Unit> same = new ArrayList<>();
        for (Unit temp : unitChain) {
            if (temp.toString().equals(unit.toString())) same.add(temp);
        }
        return same;
    }

    public static int getAbstractionCount() {
        return abstractionCount;
    }

    public static HashMap<String, Integer> getFoundStates() {
        return foundStates;
    }

    public static HashMap<String, Integer> getRCallbacks() {
        return RCallbacks;
    }

    public static HashMap<String, Integer> getECallbacks() {
        return ECallbacks;
    }

    public static int getIrrelevant_events() {
        return irrelevant_events;
    }

    public static int getNeccessary_events() {
        return neccessary_events;
    }
}
