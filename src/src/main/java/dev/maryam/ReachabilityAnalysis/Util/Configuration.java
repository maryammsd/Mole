package dev.maryam.ReachabilityAnalysis.Util;

import java.io.File;

public class Configuration {

    private static String sourceDirectory = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "IntervalAnalysis";

    private  String outputPath;
    private  String androidJarPath;
    private  String callbackList;
    private  String callbackTypestate;
    private  boolean isSingleApp = false;

    public void Configuration(){
    }
    public  void setAndroidJarPath(String androidJarPath) {
        this.androidJarPath = androidJarPath;
    }

    public  void setCallbackList(String callbackList) {
        this.callbackList = callbackList;
    }

    public  void setCallbackTypestate(String callbackTypestate) {
        this.callbackTypestate = callbackTypestate;
    }

    public  void setIsSingleApp(boolean isSingleApp) {
        this.isSingleApp = isSingleApp;
    }

    public  void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public  void setSourceDirectory(String sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public  String getAndroidJarPath() {
        return androidJarPath;
    }

    public  String getCallbackList() {
        return callbackList;
    }

    public  String getCallbackTypestate() {
        return callbackTypestate;
    }

    public  String getOutputPath() {
        return outputPath;
    }

    public  String getSourceDirectory() {
        return sourceDirectory;
    }

    public  boolean isIsSingleApp() {
        return isSingleApp;
    }
}
