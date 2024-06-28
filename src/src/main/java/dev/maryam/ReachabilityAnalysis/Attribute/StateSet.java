package dev.maryam.ReachabilityAnalysis.Attribute;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StateSet {
    private ArrayList<State> states;
    private Map<String,Integer> stateValueSet;
    private Map<String, Integer> DefUseSet;

    public StateSet(String statePath){

        // define @states, @stateValueSet and @DefUseSet and add states from @statePath
        states =  new ArrayList<>();
        stateValueSet =  new HashMap<>();
        DefUseSet = new HashMap<>();
        readFromFile(statePath);

    }

    public ArrayList<State> getStates() {
        return states;
    }

    public Map<String, Integer> getstateValueSet() {
        return stateValueSet;
    }

    public Map<String,Integer> getDefUseSet() {
        return DefUseSet;
    }

    public boolean addState(State state) {
        if(state.getMethodSignature() != null){
            if(state.getState() > 0) {
                stateValueSet.put(state.getMethodSignature(),state.getState());
                return true;
            }

        }
        return false;
    }

    public void addDefUse(State state) {
        if(state.getMethodSignature() != null){
            if(state.getIsDef() == 1){
                DefUseSet.put(state.getMethodSignature(),1);
            }else {
                DefUseSet.put(state.getMethodSignature(),0);
            }
        }
    }


    public int getState(String methodSignature){
        if(methodSignature != null){
            if(stateValueSet.containsKey(methodSignature)){
                int state = getstateValueSet().get(methodSignature);
                return state;
            }
        }
        return -1;
    }

    public int getIsDef(String methodSignature){
        if(methodSignature != null){
            if(DefUseSet.containsKey(methodSignature)){
                int isDef = DefUseSet.get(methodSignature);
                return isDef;
            }
        }
        return -1;
    }

    public void readFromFile(String statePath){
        String line = "";
        String splitBy = ";";
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(statePath));
            while ((line = br.readLine()) != null)
            {
                String methodSignature, objectType;
                int value = -1, isDef = -1;
                System.out.println(line);
                String[] stateValues = line.split(splitBy);
                // 1st element: object type
                objectType = stateValues[0];

                // 2nd element: method signature
                methodSignature = stateValues[1];


                // 3rd element: def or use
                if(stateValues[2].equals("DEF")){
                    isDef = 1;
                }else if(stateValues[2].equals("USE")){
                    isDef = 0;
                }

                if(isDef == 1){
                    // remove the name of the arguments and just keep the type of the values there in the signature :)
                    int first = methodSignature.indexOf("(");
                    int end = methodSignature.indexOf(")");
                    if(first > 0 && end > 0 && first+1 < end) {
                        String args = "(";
                        String[] arguments = methodSignature.substring(first + 1, end).split(", ");
                        if (arguments.length > 0) {
                            for (int i = 0; i < arguments.length; i++) {
                                String[] argValue = arguments[i].split(" ");
                                if(argValue.length == 2){
                                    //System.out.println(argValue);
                                    args += argValue[0];
                                }else
                                    System.out.println("Error "+ argValue.length);
                                if(i != (arguments.length-1))
                                    args+=",";
                            }
                            args += ")";
                            methodSignature = methodSignature.substring(0,first)+args;

                        }

                    }
                }
                //5th element: value
                value = Integer.valueOf(stateValues[4]);

                // Add state to @state sets
                State state = new State(methodSignature,value,isDef);
                if(!states.contains(state)){
                    states.add(state);
                }
                // Add the methodSignature -> Def or Use to @defUseSet
                if(getDefUseSet().get(state.getMethodSignature()) == null){
                    // add it
                    DefUseSet.put(methodSignature,isDef);
                }

                // Add the methodSignature -> value to @stateValueSet
                if(getstateValueSet().get(methodSignature) == null){
                    stateValueSet.put(methodSignature, value);
                }
            }
        int x = 0;

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


}
