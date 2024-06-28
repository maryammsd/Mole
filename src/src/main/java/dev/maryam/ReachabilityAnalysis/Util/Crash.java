package dev.maryam.ReachabilityAnalysis.Util;

public class Crash {


    private  String apkName;
    private  String targetPackageName;
    private  String targetMethod;
    private  String targetClass;
    private  int Linenumber;
    private  String outPutFilePath;

    public  String getApkName() {
        return apkName;
    }

    public  int getLinenumber() {
        return Linenumber;
    }

    public  String getTargetPackageName() {
        return targetPackageName;
    }

    public  String getTargetMethod() {
        return targetMethod;
    }

    public  String getTargetClass() {
        return targetClass;
    }

    public  String getOutPutFilePath() {
        return outPutFilePath;
    }

    public  void setOutPutFilePath(String outPutFilePath) {
        this.outPutFilePath = outPutFilePath;
    }

    public  void setApkName(String apkName) {
        this.apkName = apkName;
    }

    public  void setTargetPackageName(String targetPackageName) {
        this.targetPackageName = targetPackageName;
    }

    public  void setLinenumber(int linenumber) {
        Linenumber = linenumber;
    }

    public  void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }

    public  void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }
}
