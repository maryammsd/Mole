package dev.maryam.ReachabilityAnalysis.Callback;

import java.util.ArrayList;

public class CallbackTypeState {
    private String entryPointSignature;
    private ArrayList<CallbackSummary> callbackLifeCycle;

    public CallbackTypeState(String entryPointSignature){
        this.entryPointSignature = entryPointSignature;
        callbackLifeCycle = new ArrayList<CallbackSummary>();
    }
    public void setCallbackLifeCycle(ArrayList<CallbackSummary> callbackLifeCycle) {
        this.callbackLifeCycle = callbackLifeCycle;
    }

    public ArrayList<CallbackSummary> getCallbackLifeCycle() {
        return callbackLifeCycle;
    }

    public void setEntryPointSignature(String entryPointSignature) {
        this.entryPointSignature = entryPointSignature;
    }

    public String getEntryPointSignature() {
        return entryPointSignature;
    }

    public void addCallbackSummaryToList(CallbackSummary callback) {
        callbackLifeCycle.add(callback);
    }

    public void insertCallbackSummaryToList(CallbackSummary callback) {
        int index = -1;
        for(CallbackSummary temp: callbackLifeCycle){
            if(temp.getTargetCallback().contains(callback.getStateId())){
                int tempIndex = callbackLifeCycle.indexOf(temp);
                index = tempIndex;

            }
        }
        if(index >=0 && index < callbackLifeCycle.size())
            callbackLifeCycle.add(index+1, callback);
    }
}
