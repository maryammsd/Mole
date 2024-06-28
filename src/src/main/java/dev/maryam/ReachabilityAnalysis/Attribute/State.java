package dev.maryam.ReachabilityAnalysis.Attribute;

public class State {

    private String methodSignature;
    public int value;
    private int isDef,isUse;

    public State(String methodSignature, int state, int isDef){
        this.methodSignature = methodSignature;
        this.value = state;
        if(isDef == 1) {
            this.isDef = 1;
            this.isUse = 0;
        } else{
            this.isDef = 0;
            this.isUse = 1;
        }
    }

    public void setIsDef(int isDef) {
        this.isDef = isDef;
    }

    public void setIsUse(int isUse) {
        this.isUse = isUse;
    }

    public void setMethodSignature(String  methodSignature) {
        this.methodSignature = methodSignature;
    }

    public void setState(int value) {
        this.value = value;
    }



    public int getIsDef() {
        return isDef;
    }

    public int getIsUse() {
        return isUse;
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public int getState() {
        return value;
    }


}
