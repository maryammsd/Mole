package dev.maryam.ReachabilityAnalysis;

import dev.maryam.ReachabilityAnalysis.Analysis.ReachabilityAnalysis;
import dev.maryam.ReachabilityAnalysis.Instrumentation.InstrumentUtil;
import dev.maryam.ReachabilityAnalysis.Instrumentation.MethodCallInjector;
import dev.maryam.ReachabilityAnalysis.Setup.SetupAnalysis;
import dev.maryam.ReachabilityAnalysis.Util.Configuration;
import dev.maryam.ReachabilityAnalysis.Util.Crash;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.xmlpull.v1.XmlPullParserException;
import soot.BodyTransformer;
import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.jimple.infoflow.android.axml.AXmlAttribute;
import soot.jimple.infoflow.android.axml.AXmlHandler;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.axml.ApkHandler;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ReachabilityAnalysisMain {


    private static ReachabilityAnalysis analysis;
    private static String CallgraphAlgorithm = "SPARK";
    private static Configuration config;
    private static SetupAnalysis setup;

    private static Crash singlaAppBugInfo;
    private static ArrayList<Crash> multipleAppBugInfo;
    private static long analysisTime;
    private static long instrumentationTime;
    private static int analysis_type = 0;

    public static void main(String[] args) throws XmlPullParserException, IOException {

        // (1) Check for arguments received by the user
        if (args.length >= 2) {
            int timeout = 3600;
            String alg ="", configPath ="", configType="";
            boolean isCallgraph = false, isConfig=false, isMConfig = false, isTimeout = false;
            for (String arg : args) {

                if (isCallgraph) {
                    alg = arg;
                    isCallgraph = false;
                }

                if (isTimeout) {
                    System.out.println(arg);
                    timeout = Integer.parseInt(arg);
                    if(timeout <=0 )
                        timeout = 3600;
                    isTimeout = false;
                }

                if (isConfig) {
                    configType ="s";
                    configPath = arg;
                    isConfig = false;
                }

                if (isMConfig) {
                    configType = "m";
                    configPath = arg;
                    isMConfig = false;
                }
                switch (arg) {
                    case "-h":
                        System.out.println("--------------------------------------------------------------------");
                        System.out.println(" This tool is designed to perform an attribute sensitive reachability analysis for finding necessary callbacks in Android conscerning a specific crash");
                        System.out.println(" Please ensure that you specify the class, method and line of the crash point in a separate json file according to explanations in the README file.");
                        System.out.println(" Here are the commands you can use to setup the analysis: ");
                        System.out.println(" -f <config-path> : enter the configuration file path for reproducing one bug of an application");
                        System.out.println(" -lf <config-path>: enter the configuration file path for reproducing several bugs of different applications");
                        System.out.println(" -ca <algorithm>  : enter the name of callgraph algorithm in soot (e.g. SPARK, CHA, RTA and VTA)");
                        System.out.println(" -h               : help");
                        System.out.println("--------------------------------------------------------------------");
                        return;
                    case "-ca":
                        isCallgraph = true;
                        break;
                    case "-f":
                        isConfig = true;
                        break;
                    case "-t":
                        isTimeout = true;
                        break;
                    case "-mf":
                        isMConfig = true;
                        break;
                    case "event":
                        analysis_type = 2;
                        break;
                    case "baseline":
                        analysis_type = 0;
                        break;
                    case "reach":
                        analysis_type = 1;
                        break;
                    default:
                        break;
                }
            }

            // Checking callgraph algorithm entered
            if(!alg.isEmpty()){
                if (alg.equals("CHA") || alg.equals("SPARK") || alg.equals("VTA") || alg.equals("RTA")) {
                    CallgraphAlgorithm = alg;
                } else {
                    System.out.println("--------------------------------------------------------------------");
                    System.out.println("ERROR: Such an algorithm doesn't exist! ");
                    System.out.println("Please choose one algorithm among this set: <CHA, SPARK, VTA, RTA> ");
                    System.out.println("--------------------------------------------------------------------");
                    return;
                }
            }

            // Checking the config file entered and start the analysis
            if(!configType.isEmpty()){
                if (configPath.endsWith(".json")){
                    if(configType.equals("s"))
                        performSingleAppAnalysis(configPath,timeout);
                    else if(configType.equals("m"))
                        performMultipleAppAnalysis(configPath,timeout);
                }else{
                    System.out.println("--------------------------------------------------------------------");
                    System.out.println("ERROR: Configuration file is not entered correctly! ");
                    System.out.println("Please run the program including the command -f <config-path> or -mf <config-path> ");
                    System.out.println("--------------------------------------------------------------------");
                    return;
                }
            }else {
                System.out.println("--------------------------------------------------------------------");
                System.out.println("ERROR: Configuration file is not entered! ");
                System.out.println("Please run the program including the command -f <config-path> or -mf <config-path> ");
                System.out.println("--------------------------------------------------------------------");
                return;
            }

        } else {
            System.out.println("--------------------------------------------------------------------");
            System.out.println("Please run the program with command -f <config-path> -ca <callgraph-algorithm> -t <timeout> ");
            System.out.println("--------------------------------------------------------------------");
        }


    }

    public static void performSingleAppAnalysis(String configPath, int timeout) throws XmlPullParserException, IOException {
        singlaAppBugInfo =  new Crash();
        config = new Configuration();
        config.setIsSingleApp(true);

        boolean isCorrect = configSettingSingleApp(configPath), success =false;


        if (isCorrect) {
            // Setup FlowDroid to extract Call graph from it
            setup = new SetupAnalysis(new String(config.getSourceDirectory() + "/" + singlaAppBugInfo.getApkName()), config.getAndroidJarPath(), CallgraphAlgorithm,"/home/maryam/SootTutorial/files/UICallback.txt" , config.getOutputPath()+"/"+singlaAppBugInfo.getApkName(), timeout);
            setup.setTargetClass(singlaAppBugInfo.getTargetClass());
            setup.setTargetMethod(singlaAppBugInfo.getTargetMethod());
            setup.setLineNumber(singlaAppBugInfo.getLinenumber());
            setup.setTargetPackageName(singlaAppBugInfo.getTargetPackageName());
            setup.setCallbackFilePath(config.getCallbackList());
            setup.setCallbackTypeStateFilePath(config.getCallbackTypestate());

            // Start the analysis here!
            analysisTime = System.nanoTime();
            success = setup.startAnalysis();
            analysisTime = System.nanoTime() - analysisTime;

            // Perform Instrumentation Phase
            if(success) {
                performInstrumentation(setup,config,singlaAppBugInfo);
            }
        }

    }

    public static void performMultipleAppAnalysis(String configPath, int timeout) throws XmlPullParserException, IOException {
        config = new Configuration();
        config.setIsSingleApp(false);
        multipleAppBugInfo =  new ArrayList<>();
        boolean isCorrect = configSettingMultipleApp(configPath), success = false;

        if (isCorrect) {

            // Loop through the list of applications and see if the instrumentation works fine or not.
            for(Crash crashInfo: multipleAppBugInfo){
                // Setup FlowDroid to extract Call graph from it
                setup = new SetupAnalysis(new String(config.getSourceDirectory() + "/" + crashInfo.getApkName()), config.getAndroidJarPath(), CallgraphAlgorithm,"/home/maryam/SootTutorial/files/UICallback.txt" ,  config.getOutputPath()+"/"+crashInfo.getApkName(), timeout);
                setup.setCallbackFilePath(config.getCallbackList());
                setup.setCallbackTypeStateFilePath(config.getCallbackTypestate());

                setup.setTargetClass(crashInfo.getTargetClass());
                setup.setTargetMethod(crashInfo.getTargetMethod());
                setup.setLineNumber(crashInfo.getLinenumber());
                setup.setTargetPackageName(crashInfo.getTargetPackageName());
                // Start the analysis here!
                analysisTime = System.nanoTime();
                success = setup.startAnalysis();
                analysisTime = System.nanoTime() - analysisTime;

                // Perform Instrumentation Phase
                if(success){
                    performInstrumentation(setup,config,crashInfo);

                }
            }

        }

    }

    public static boolean configSettingSingleApp(String filePath) {

        try {
            FileReader file = new FileReader(filePath);

            // Create an instance of a JSON Parser
            JSONParser jsonParser = new JSONParser();

            //Read JSON file
            Object obj = jsonParser.parse(file);

            JSONObject configInfo = (JSONObject) obj;
            JSONObject android_setting = (JSONObject) configInfo.get("android_setting");
            if (!android_setting.isEmpty()) {
                config.setAndroidJarPath(android_setting.get("android_platform").toString());
                config.setCallbackList(android_setting.get("android_callback_list").toString());
                config.setCallbackTypestate(android_setting.get("callback_typestates").toString());

            } else {
                System.out.println("ERROR: No application object found! Correct the setting file!");
                return false;
            }

            JSONObject application_setting = (JSONObject) configInfo.get("application_setting");
            if (!application_setting.isEmpty()) {
                singlaAppBugInfo.setApkName(application_setting.get("target_app").toString());
                singlaAppBugInfo.setTargetPackageName(application_setting.get("target_package").toString());
                singlaAppBugInfo.setTargetClass(application_setting.get("target_class").toString());
                singlaAppBugInfo.setTargetMethod(application_setting.get("target_method").toString());
                singlaAppBugInfo.setLinenumber(Integer.parseInt(application_setting.get("target_line").toString()));
            } else {
                System.out.println("ERROR: No application object found! Correct the setting file!");
                return false;
            }

            JSONObject analysis_setting = (JSONObject) configInfo.get("analysis_setting");
            if (!analysis_setting.isEmpty()) {
                config.setOutputPath(analysis_setting.get("output_path").toString());
                singlaAppBugInfo.setOutPutFilePath(analysis_setting.get("output_path").toString() + singlaAppBugInfo.getApkName() + "-" + java.time.LocalDate.now() + "-" + java.time.LocalTime.now() + ".json");
            } else {
                System.out.println("ERROR: No output path information found! Correct the setting file!");
                return false;
            }

            file.close();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static boolean configSettingMultipleApp(String filePath) {
        try {
            FileReader file = new FileReader(filePath);

            // Create an instance of a JSON Parser
            JSONParser jsonParser = new JSONParser();

            //Read JSON file
            Object obj = jsonParser.parse(file);

            JSONObject configInfo = (JSONObject) obj;
            JSONObject android_setting = (JSONObject) configInfo.get("android_setting");
            if (!android_setting.isEmpty()) {
                config.setAndroidJarPath(android_setting.get("android_platform").toString());
                config.setCallbackList(android_setting.get("android_callback_list").toString());
                config.setCallbackTypestate(android_setting.get("callback_typestates").toString());

            } else {
                System.out.println("ERROR: No application object found! Correct the setting file and run the program again!");
                return false;
            }

            JSONObject analysis_setting = (JSONObject) configInfo.get("analysis_setting");
            if (!analysis_setting.isEmpty()) {
                config.setOutputPath(analysis_setting.get("output_path").toString());} else {
                System.out.println("ERROR: No output path information found! Correct the setting file and run the program again!");
                return false;
            }

            for (int i = 0; i < 100; i++) {

                String appLabel = "Application" + String.valueOf(i+1);
                Crash appBugInfo = new Crash();
                System.out.println(" applabel: "+  appLabel);
                JSONObject application_setting = (JSONObject) configInfo.get(appLabel);
                if (application_setting != null) {
                    if (!application_setting.isEmpty()) {
                        appBugInfo.setApkName(application_setting.get("target_app").toString());
                        appBugInfo.setTargetPackageName(application_setting.get("target_package").toString());
                        appBugInfo.setTargetClass(application_setting.get("target_class").toString());
                        appBugInfo.setTargetMethod(application_setting.get("target_method").toString());
                        appBugInfo.setLinenumber(Integer.parseInt(application_setting.get("target_line").toString()));
                        multipleAppBugInfo.add(appBugInfo);
                        if(config.getOutputPath() !=  null){
                            multipleAppBugInfo.get(i).setOutPutFilePath(analysis_setting.get("output_path").toString() + appBugInfo.getApkName() + "-" + java.time.LocalDate.now() + "-" + java.time.LocalTime.now() + ".json");
                        }
                    } else {
                        System.out.println("ERROR: "+i+"th Application object is empty! Correct the setting file and run the program again!");
                        return false;
                    }
                } else {
                    if(i == 1) {
                        System.out.println("ERROR: No application object found! Correct the setting file and run the program again!");
                        return false;
                    }else{
                        return true;
                    }
                }
            }
            file.close();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    private static void performInstrumentation(SetupAnalysis setup, Configuration config, Crash crashInfo) {
        instrumentationTime = System.nanoTime();
        logAnalysisResult(setup, crashInfo.getOutPutFilePath());
        BodyTransformer instrumentTransformer = new MethodCallInjector(setup.getUIReachableCallbacks(), setup.getUIUnreachableCallbacks(), setup.getReachableCallbacks(), setup.getUnReachableCallbacks(),setup.getStateSet(), setup.getRunMethods(),analysis_type);
        InstrumentUtil.setupSoot(new String(config.getSourceDirectory() + "/" + crashInfo.getApkName()), config.getAndroidJarPath(), config.getOutputPath());
        System.out.println("[Instrumentation Phase] android API version is "+ Scene.v().getAndroidAPIVersion());
        System.out.println("[Instrumentation Phase] android min SDK version is "+ Scene.v().getAndroidSDKVersionInfo().minSdkVersion);
        if(Scene.v().getAndroidSDKVersionInfo().minSdkVersion <= 22) {
            Scene.v().getAndroidSDKVersionInfo().minSdkVersion = 23;
            System.out.println("[Instrumentation Phase] android min SDK version is updated to " + Scene.v().getAndroidSDKVersionInfo().minSdkVersion);
        }
        PackManager.v().getPack("jtp").add(new Transform("jtp.logInstrumentation", instrumentTransformer));
        PackManager.v().runPacks();
        PackManager.v().writeOutput();

        int count = 0;
        for(String found: MethodCallInjector.getFoundStates().keySet()){
            count++;
            System.out.println(" [State] # "+ count + " "+ found + " "+ MethodCallInjector.getFoundStates().get(found));
        }

        System.out.println(" ---------- Callbacks: Necessary or Irrelevant --------");
        for(String callback: MethodCallInjector.getRCallbacks().keySet()){
            System.out.println(" [Callback] "+ callback + " necessary " + MethodCallInjector.getRCallbacks().get(callback) );
            if(MethodCallInjector.getECallbacks().containsKey(callback)){
                System.out.println(" *** reachable events ***");
            }
        }
        instrumentationTime = System.nanoTime() - instrumentationTime;
        System.out.println("[Count Necessary Events] "+ MethodCallInjector.getNeccessary_events());
        System.out.println("[Count Irrelevant Events] "+ MethodCallInjector.getIrrelevant_events());
        System.out.println("[Count Abstraction] "+ MethodCallInjector.getAbstractionCount());
    }

    public static void setMinSDKVersion(String apkFilePath) throws IOException, XmlPullParserException {
        System.out.println("[ APK Manifest Checking] ... ");
        File apkFile = new File(apkFilePath);
        ProcessManifest processManifest =  new ProcessManifest(apkFile);
        AXmlHandler aXmlHandler = processManifest.getAXml();
        List<AXmlNode> aXmlNodeList = aXmlHandler.getNodesWithTag("uses-sdk");
        if(aXmlNodeList.isEmpty()){
            System.out.println("[ APK Manifest Checking]: Adding uses-sdk ");
            AXmlNode usesSDK = new AXmlNode("uses-sdk", null, aXmlHandler.getDocument().getRootNode());
            usesSDK.addAttribute(new AXmlAttribute<String>("minSdkVersion",  new String("23"), new String("\"http://schemas.android.com/apk/res/android\"")));
            aXmlHandler.getDocument().getRootNode().addChild(usesSDK);
        }else{
            System.out.println("[ APK Manifest Checking]: Modifying uses-sdk ");

            Iterator<AXmlNode> aXmlNodeIterator = aXmlNodeList.iterator();
            boolean uses_sdk_found = false;

            while(aXmlNodeIterator.hasNext())
            {
                AXmlNode itNode = aXmlNodeIterator.next();
                if(itNode.getAttribute("minSdkVersion") == null) {
                    itNode.addAttribute(new AXmlAttribute<String>("minSdkVersion",  new String("23"), new String("\"http://schemas.android.com/apk/res/android\"")));
                    uses_sdk_found = true;
                }else if(itNode.getAttribute("minSdkVersion") != null) {

                    String minSdkVersion = itNode.getAttribute("minSdkVersion").getValue().toString();
                    int minSdkVersionInt = Integer.parseInt(minSdkVersion);
                    System.out.println("APK MinSdkVersion" +  minSdkVersion);
                    System.out.println("APK targetSDKVersion" +  itNode.getAttribute("targetSdkVersion").getValue().toString());
                    if(minSdkVersionInt < 23){
                        // Create a new Node
                        AXmlNode newNode = new AXmlNode("uses-sdk", itNode.getNamespace(), itNode.getParent());
                        Set<String> attrs = itNode.getAttributes().keySet();
                        for(String attrName: attrs){
                            if(!attrName.equals("minSdkVersion"))
                                newNode.addAttribute(itNode.getAttribute(attrName));
                            else{
                                newNode.addAttribute(new AXmlAttribute<Integer>("minSdkVersion",24, itNode.getNamespace()));
                            }

                        }
                        System.out.println(itNode.getAttribute("minSdkVersion").getValue().getClass() + " is this ");
                        aXmlHandler.getDocument().getRootNode().removeChild(itNode.getParent());
                        aXmlHandler.getDocument().getRootNode().addChild(newNode);

                    }

                    minSdkVersion = itNode.getAttribute("minSdkVersion").getValue().toString();

                    System.out.println("And now it is APK MinSdkVersion" +  minSdkVersion);
                    uses_sdk_found = true;
                }

                if(uses_sdk_found)
                    break;
            }
        }
        byte[] axmlBA = aXmlHandler.toByteArray();

        FileOutputStream fileOutputStream = new FileOutputStream(".\\AndroidManifest.xml");
        fileOutputStream.write(axmlBA);
        fileOutputStream.close();

        List<File> fileList = new ArrayList<File>();
        File newManifest = new File(".\\AndroidManifest.xml");
        fileList.add(newManifest);

        ApkHandler apkH = new ApkHandler(apkFile);
        apkH.addFilesToApk(fileList);

    }

    public static void logAnalysisResult(SetupAnalysis setup, String outputPath){
        JSONArray methodArray =  new JSONArray();
        methodArray = setup.getJsonResult();

        JSONArray appArray = new JSONArray();

        JSONObject analysisInfo = new JSONObject();
        analysisInfo.put("NumOfReachableCallbacks", setup.getUIReachableCallbacks().size());

        analysisInfo.put("NumOfUnReachableCallbacks", setup.getUIUnreachableCallbacks().size());

        analysisInfo.put("NumOfReachableStmts", setup.getNumOfReachablestmts());

        analysisInfo.put("NumOfUnReachableStmts", setup.getNumOfUnreachablestmts());

        analysisInfo.put("NumOfTotalStmts", setup.getTotalStatements());

        analysisInfo.put("AnalysisTime", String.valueOf(TimeUnit.SECONDS.convert(analysisTime, TimeUnit.NANOSECONDS) + " sec"));

        analysisInfo.put("InstrumentationTime",  String.valueOf(TimeUnit.SECONDS.convert(instrumentationTime, TimeUnit.NANOSECONDS) + " sec"));

        analysisInfo.put("TotalTime",  String.valueOf(TimeUnit.SECONDS.convert(instrumentationTime+analysisTime, TimeUnit.NANOSECONDS) + " sec"));


        methodArray.add(analysisInfo);

        appArray.add(methodArray);


        try {
            FileWriter file = new FileWriter(outputPath);
            if (file != null) {
                file.write(appArray.toString());
            }
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}