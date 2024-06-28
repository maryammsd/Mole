package dev.maryam.ReachabilityAnalysis.Callback;

import java.util.ArrayList;

public class CallbackSummary {
    private String signature, entryPointSignature;
    private String superClassType;
    private ArrayList<Integer> targetCallback;
    private int stateId;

    public String getSignature() {
        return signature;
    }

    public String getEntryPointSignature() {
        return entryPointSignature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public void setEntryPointSignature(String entryPointSignature) {
        this.entryPointSignature = entryPointSignature;
    }

    public String getSuperClassType() {
        return superClassType;
    }

    public void setSuperClassype(String superClassType) {
        this.superClassType = superClassType;
    }

    public ArrayList<Integer> getTargetCallback() {
        return targetCallback;
    }

    public void setTargetCallback(ArrayList<Integer> targetCallback) {
        this.targetCallback = targetCallback;
    }

    public int getStateId() {
        return stateId;
    }

    public void setStateId(int stateId) {
        this.stateId = stateId;
    }

}
