package dev.maryam.ReachabilityAnalysis.Attribute;

import dev.maryam.ReachabilityAnalysis.Analysis.GlobalVariable;
import dev.maryam.ReachabilityAnalysis.Analysis.LocalVariable;
import dev.maryam.ReachabilityAnalysis.Analysis.Variable;
import dev.maryam.ReachabilityAnalysis.Callback.Callback;
import soot.Unit;
import soot.util.ArraySet;

import java.util.*;

public class States {

    // Store the mapping between variables and states of GUI elements
    private Set<Abstraction> stateMap;

    // Store list of local variables involved in this constraint
    private ArrayList<Variable> varSet;

    private ArrayList<Unit> target;

    private ArrayList<Integer> ids;

    private boolean reachable = false;

    private ArrayList<Callback> widgetCallbacks;

    private ArrayList<String> reachableComponents;

    private ArrayList<String> targetComponents;

    public States() {
        this.stateMap = new ArraySet<>();
        this.varSet = new ArrayList<>();
        this.target = new ArrayList<>();
        this.ids = new ArrayList<Integer>();
    }


    public void setVarSet(ArrayList<Variable> varSet) {
        if (varSet != null) for (Variable var : varSet)
            if (var != null) setVar(var);
    }

    public void setVar(Variable var) {
        if (!hasVar(var) && var != null) varSet.add(var);
    }

    public void setStateMap(Set<Abstraction> stateMap) {
        if (stateMap != null) {
            for (Abstraction abstraction : stateMap)
                if (!hasStateMap(abstraction)) this.stateMap.add(abstraction);
        }
    }

    public void setTarget(Unit unit) {
        if (!target.contains(unit)) target.add(unit);
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public void setIds(ArrayList<Integer> newIds) {
        for (int id : newIds)
            if (!hasId(id)) ids.add(id);
    }


    public ArrayList<Variable> getVarSet() {
        return varSet;
    }

    public boolean  hasVar(Variable targetVar) {
        for (Variable variable : varSet) {
            if (variable != null) {
                if (variable.getVariableType() == targetVar.getVariableType()) {
                    if (variable instanceof LocalVariable) {
                        if (((LocalVariable) variable).getLocal().getName().equals(((LocalVariable) targetVar).getLocal().getName())) {
                            if (((LocalVariable) variable).isInstance() && ((LocalVariable) targetVar).isInstance()) {
                                if (((LocalVariable) variable).getSootField().getName().equals(((LocalVariable) targetVar).getSootField().getName())) {
                                    return true;
                                }
                            } else
                                return true;
                        }

                    } else if (variable instanceof GlobalVariable) {
                        if (((GlobalVariable) variable).getGlobal().getSignature().equals(((GlobalVariable) targetVar).getGlobal().getSignature()) &&
                                ((GlobalVariable) variable).getGfield().getName().equals(((GlobalVariable) variable).getGfield().getName()))
                            return true;
                    }
                }
            }
        }
        return false;
    }

    public Set<Abstraction> getStateMap() {
        return stateMap;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void addState(Abstraction abstraction) {
        Variable variable = abstraction.getVariable();

        // (1) Add abstraction if not such a local and state exists within it
        if (!hasVar(variable)) {
            varSet.add(variable);

            // ( + ) Just added
            // If there is the negative and positive ones both here! please don't add it
            //if(!hasStateMap(abstraction))
                stateMap.add(abstraction);
        } else {
            if (!hasStateMap(abstraction)) stateMap.add(abstraction);
        }
    }

    public boolean checkNegationExist(Abstraction abstraction) {
        for (Abstraction temp : stateMap) {
            if (temp.getVariable() instanceof LocalVariable && abstraction.getVariable() instanceof LocalVariable) {
                if (equalLocalVariables((LocalVariable) temp.getVariable(), (LocalVariable) abstraction.getVariable()) && isNegate(abstraction, temp))
                    return true;

            } else if (temp.getVariable() instanceof GlobalVariable && abstraction.getVariable() instanceof GlobalVariable) {
                if (equalGlobalVariables((GlobalVariable) temp.getVariable(), (GlobalVariable) abstraction.getVariable()) && isNegate(abstraction, temp))
                    return true;

            }
        }
        return false;
    }

    public boolean isNegate(Abstraction abstraction, Abstraction temp) {
        if (abstraction.getState().getMethodSignature().equals(temp.getState().getMethodSignature())) {
            if (abstraction.getValue() == temp.getValue())
                return false;
            else
                    return abstraction.getValue() == ((-1) * temp.getValue());
        }
        return false;
    }

    public boolean isEqual(Abstraction abstraction, Abstraction temp) {
        if (abstraction.getState().getMethodSignature().equals(temp.getState().getMethodSignature())) {
            return abstraction.getValue() == temp.getValue();
        }
        return false;
    }

    public void addId(int id) {
        if (!hasId(id)) ids.add(id);
    }

    /**
     * Add a set of abstractions to current @stateMap
     *
     * @param abstractionSet
     */
    public void addStates(Set<Abstraction> abstractionSet) {
        for (Abstraction abstraction : abstractionSet) {
            if (!hasStateMap(abstraction)) {
                stateMap.add(abstraction);
                if (!hasVar(abstraction.getVariable())) varSet.add(abstraction.getVariable());
            }
        }
    }

    public boolean hasStateMap(Abstraction abstraction) {
        for (Abstraction temp : stateMap) {
            if (temp.getState().getMethodSignature().equals(abstraction.getState().getMethodSignature()) &&
                    temp.getValue() == abstraction.getValue() &&
                    temp.getVariable().getVariableType() == abstraction.getVariable().getVariableType()) {
                if (temp.getVariable() instanceof LocalVariable) {
                    if (((LocalVariable) temp.getVariable()).getLocal().getName().equals(((LocalVariable) abstraction.getVariable()).getLocal().getName()))
                        if(((LocalVariable) temp.getVariable()).isInstance() && ((LocalVariable) abstraction.getVariable()).isInstance()){
                            if(((LocalVariable) temp.getVariable()).getSootField().getName().equals(((LocalVariable) abstraction.getVariable()).getSootField().getName()))
                                return true;
                        } else if(!((LocalVariable) temp.getVariable()).isInstance() && !((LocalVariable) abstraction.getVariable()).isInstance())
                            return true;
                } else if (temp.getVariable() instanceof GlobalVariable) {
                    if (((GlobalVariable) temp.getVariable()).getGfield().getName().equals(((GlobalVariable) abstraction.getVariable()).getGfield().getName()))
                        return true;
                }
            }
        }
        return false;
    }

    public boolean removeState(Abstraction abstraction) {
        if (stateMap.contains(abstraction)) {
            stateMap.remove(abstraction);
            varSet.remove(abstraction.getVariable());
            for (Abstraction temp : stateMap) {
                if (abstraction.getVariable() instanceof GlobalVariable) {
                    if (temp.getVariable() instanceof GlobalVariable) {
                        if (((GlobalVariable) temp.getVariable()).getGlobal().equals(((GlobalVariable) abstraction.getVariable()).getGlobal()))
                            varSet.add(abstraction.getVariable());
                    }

                } else if (abstraction.getVariable() instanceof LocalVariable)
                    if (temp.getVariable() instanceof LocalVariable) {
                        if (((LocalVariable) temp.getVariable()).getLocal().equals(((LocalVariable) abstraction.getVariable()).getLocal()))
                            varSet.add(abstraction.getVariable());
                    }
            }
            return true;
        } else return false;
    }

    public boolean removeState(int value, Variable var) {
        boolean isFound = false;
        for(Abstraction abstraction: stateMap){
            if(abstraction.getValue() == value){
                if(abstraction.getVariable() instanceof LocalVariable &&
                    var instanceof LocalVariable){
                    stateMap.remove(abstraction);
                    isFound = true;

                }else if(abstraction.getVariable() instanceof GlobalVariable &&
                var instanceof GlobalVariable){
                    stateMap.remove(abstraction);
                    isFound = true;
                }
            }
        }
        return isFound;
    }

    public Set<Abstraction> getListofAbstraction(Variable variable) {
        Set<Abstraction> abstractionSet = new HashSet<>();
        for (Abstraction abstraction : stateMap) {
            if (abstraction.getVariable() instanceof GlobalVariable) {
                if (variable instanceof GlobalVariable) {
                    if (((GlobalVariable) variable).getGlobal().equals(((GlobalVariable) abstraction.getVariable()).getGlobal()))
                        abstractionSet.add(abstraction);
                }

            } else if (abstraction.getVariable() instanceof LocalVariable) if (variable instanceof LocalVariable) {
                if (((LocalVariable) variable).getLocal().equals(((LocalVariable) abstraction.getVariable()).getLocal()))
                    abstractionSet.add(abstraction);
            }
        }
        return abstractionSet;
    }

    public void updateTargetSet(ArrayList<Unit> targets) {
        for (Unit unit : targets) {
            if (!target.contains(unit)) {
                target.add(unit);
            }
        }
    }

    public ArrayList<Unit> getTargets() {
        return target;
    }

    public ArrayList<Integer> getIds() {
        return ids;
    }

    public boolean hasId(int value) {
        for (int id : ids)
            if (id == value) return true;
        return false;
    }

    /**
     * Check the equality of variable sets
     * If the corresponding variable set has similar variables, two variable sets are assumed to be similar
     *
     * @param list1
     * @param list2
     * @return
     */
    public boolean equalVarSets(ArrayList<Variable> list1, ArrayList<Variable> list2) {
        int sumcounter = 0;
        if (list1.size() == list2.size() && !list1.isEmpty()) {
            for (Variable variable : list1) {
                int counter = 0;
                for (Variable value : list2) {
                    if (variable != null && value != null) {
                        if (variable instanceof LocalVariable && value instanceof LocalVariable) {
                            if (((LocalVariable) variable).getLocal().getName().equals(((LocalVariable) value).getLocal().getName())) {
                                if (((LocalVariable) variable).isInstance() && ((LocalVariable) value).isInstance()) {
                                    if (((LocalVariable) variable).getSootField().getName().equals(((LocalVariable) value).getSootField().getName())) {
                                        counter++;
                                        break;
                                    }
                                } else if (!((LocalVariable) variable).isInstance() && !((LocalVariable) value).isInstance()) {
                                    counter++;
                                    break;
                                }
                            }
                        } else if (variable instanceof GlobalVariable && value instanceof GlobalVariable) {
                            if (((GlobalVariable) variable).getGfield().getName().equals(((GlobalVariable) value).getGfield().getName()))
                                counter++;
                            break;
                        }
                    }
                }
                if (counter >= 1)
                    sumcounter += counter;
                else
                    return false;
            }
            return sumcounter == list1.size();

        } else return list1.isEmpty() && list2.isEmpty();
    }

    /**
     * Checking the equality of two states of different outvalues on a single unit
     * The algorithm is easy:
     * If not any option is found in the second list, the function returns false
     * If all are found, then there is no false return and should return true
     *
     * @param stateMap1
     * @param stateMap2
     * @return
     */
    public boolean equalStateMap(Set<Abstraction> stateMap1, Set<Abstraction> stateMap2) {
        if (stateMap1.size() == stateMap2.size() && !stateMap1.isEmpty()) {
            for (Abstraction abstraction1 : stateMap1) {
                boolean isfound = false;
                for (Abstraction abstraction2 : stateMap2) {
                    if (abstraction1.getState() == abstraction2.getState() && abstraction1.getValue() == abstraction2.getValue()) {
                        if (abstraction1.getVariable() instanceof LocalVariable && abstraction2.getVariable() instanceof LocalVariable) {
                            if (((LocalVariable) abstraction1.getVariable()).getLocal().getName().equals(((LocalVariable) abstraction2.getVariable()).getLocal().getName())) {
                                if (((LocalVariable) abstraction1.getVariable()).isInstance() && ((LocalVariable) abstraction2.getVariable()).isInstance()) {
                                    if (((LocalVariable) abstraction1.getVariable()).getSootField().getName().equals(((LocalVariable) abstraction2.getVariable()).getSootField().getName())) {
                                        isfound = true;
                                        break;
                                    }
                                } else {
                                    isfound = true;
                                    break;
                                }
                            }


                        } else if (abstraction1.getVariable() instanceof GlobalVariable && abstraction2.getVariable() instanceof GlobalVariable) {
                            if (((GlobalVariable) abstraction1.getVariable()).getGlobal().getSignature().equals(((GlobalVariable) abstraction2.getVariable()).getGlobal().getSignature()) &&
                                    ((GlobalVariable) abstraction1.getVariable()).getGfield().getName().equals(((GlobalVariable) abstraction2.getVariable()).getGfield().getName())) {
                                isfound = true;
                                break;
                            }
                        }
                    }
                }
                if (!isfound) return false;
            }
            return true;
        } else return stateMap1.isEmpty() && stateMap2.isEmpty();
    }

    public boolean equalIdSets(ArrayList<Integer> idlist1, ArrayList<Integer> idlist2) {
        if (idlist1.size() == idlist2.size() && !idlist1.isEmpty()) {
            for (int id1 : idlist1) {
                boolean hasId = false;
                for (int id2 : idlist2) {
                    if (id1 == id2) {
                        hasId = true;
                        break;
                    }
                }
                if (!hasId) return false;
            }
            return true;
        } else return idlist2.isEmpty() && idlist1.isEmpty();
    }

    public boolean equalLocalVariables(LocalVariable local1, LocalVariable local2){
        if(local1.isInstance() && local2.isInstance()){
            if(local1.getLocal().getName().equals(local2.getLocal().getName()) &&
            local1.getSootField().getName().equals(local2.getSootField().getName()))
                return true;
        }else if(!local1.isInstance() && !local2.isInstance()){
            if(local1.getLocal().getName().equals(local2.getLocal().getName()))
                return true;
        }
        return false;
    }

    public boolean equalGlobalVariables(GlobalVariable global1, GlobalVariable global2){
        if(global1.getGfield().getName().equals(global2.getGfield().getName()))
            return true;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        States states = (States) o;
        return reachable == states.reachable && Objects.equals(stateMap, states.stateMap) && equalVarSets(varSet, states.varSet) && Objects.equals(target, states.target);
    }



    @Override
    public int hashCode() {
        return Objects.hash(stateMap, varSet, target, reachable);
    }

    @Override
    public String toString() {
        return "States{" + "stateMap=" + stateMap + ", varSet=" + varSet + ", target=" + target + ", reachable=" + reachable + '}';
    }
}
