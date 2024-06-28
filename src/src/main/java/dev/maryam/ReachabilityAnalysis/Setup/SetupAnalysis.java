package dev.maryam.ReachabilityAnalysis.Setup;

import dev.maryam.ReachabilityAnalysis.Analysis.GlobalVariable;
import dev.maryam.ReachabilityAnalysis.Analysis.LocalVariable;
import dev.maryam.ReachabilityAnalysis.Analysis.ReachabilityAnalysis;
import dev.maryam.ReachabilityAnalysis.Attribute.Abstraction;
import dev.maryam.ReachabilityAnalysis.Attribute.StateSet;
import dev.maryam.ReachabilityAnalysis.Attribute.States;
import dev.maryam.ReachabilityAnalysis.CallGraph.AndroidCallGraphFilter;
import dev.maryam.ReachabilityAnalysis.CallGraph.CallGraphUtil;
import dev.maryam.ReachabilityAnalysis.Core.DataFlowSolution;
import dev.maryam.ReachabilityAnalysis.Core.InterProceduralCFGRepresentation;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import soot.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.pointer.DumbPointerAnalysis;
import soot.options.Options;
import soot.util.Chain;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static soot.SootClass.BODIES;


public class SetupAnalysis {

    private String apkPath;
    private final String androidJarPath;
    private final String callbackListPath;
    private final String outputPath;
    private String targetClass, targetMethod;
    private String targetPackageName;
    private int lineNumber;
    private String CallbackFilePath, CallbackTypeStateFilePath;
    private InfoflowAndroidConfiguration config;
    private final SetupApplication setupApp;
    private Set<SootMethod> allMethods;
    private Map<String, States> UIReachableCallbacks, runMethods;
    private Set<String> UIUnreachableCallbacks, ReachableCallbacks, UnReachableCallbacks;
    private Set<SootClass> reachableComponents, unreachableComponents;
    private int NumOfUnreachablestmts = 0, NumOfReachablestmts = 0, totalStatements = 0;
    private JSONArray jsonResult;
    private String CallgraphAlgorithm = "SPARK";
    private DataFlowSolution<Unit, Map<Unit, States>> solution;
    private ReachabilityAnalysis analysis;
    private InterProceduralCFGRepresentation icfg;
    private StateSet stateSet;
    private ArrayList<String> callbackType, callbackList;
    private int abstractionCount = 0;

    /**
     * @param apkPath        : path where target apk is located within
     * @param androidJarPath : path where android platform jar files are located within
     */

    public SetupAnalysis(String apkPath, String androidJarPath, String CallgraphAlgorithm, String callbackListPath, String outputPath, int timeout) {
        this.apkPath = apkPath;
        this.androidJarPath = androidJarPath;
        this.callbackListPath = callbackListPath;
        this.outputPath = outputPath;
        this.CallgraphAlgorithm = CallgraphAlgorithm;
        config = new InfoflowAndroidConfiguration();

        // (1) Set the configuration file for the current analysis
        setupConfig(timeout);

        // (2) Now setup the flowdroid for analysis
        setupApp = new SetupApplication(config);


        // (3) Lets store all the methods of the application in a list
        allMethods = new HashSet<>();

        // (4) Create output Directory
        File outPutdir = new File(outputPath);
        if (!outPutdir.exists()) {
            outPutdir.mkdirs();
        }

    }

    public Boolean startAnalysis() throws IOException, XmlPullParserException {


        // Extract the call-graph with Flowdroid
        boolean canStart = createDummyMainClass();

        if (canStart) {
            // Start the Main Analysis
            SceneTransformer transformer = createTransformer();
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.interval", transformer));
            PackManager.v().getPack("wjtp").apply();

            try {
                jsonResult = logAnalyisResultEnhanced();

            } catch (IOException e) {
                e.printStackTrace();
            }

            // print analysis results
            System.out.println("[Analysis Results] Unreachable Callbacks: " + getUIUnreachableCallbacks().size());
            System.out.println("[Analysis Results] Reachable Callbacks: " + getUIReachableCallbacks().size());
            System.out.println("[Analysis Results] Unreachable statements: " + NumOfUnreachablestmts);
            System.out.println("[Analysis Results] Reachable statements: " + NumOfReachablestmts);
            System.out.println("[Analysis Results] Total statements: " + totalStatements);
            System.out.println("[Analysis Results] Total abstractions: " + abstractionCount);
            return true;
        } else {
            return false;
        }
    }

    public Boolean createDummyMainClass() throws IOException, XmlPullParserException {

        // (1) setup the callgraph construction configuration
        setSetupApp();

        // (2) construct the call-graph considering callbacks
        setupApp.constructCallgraph();
        System.out.println("[Callgraph] is constructed successfully");

        // get all methods within an application
        getAllApplicationMethods();
        if (Scene.v().getPointsToAnalysis() instanceof DumbPointerAnalysis) {
            try {
                Method method = SetupApplication.class.getDeclaredMethod("constructCallgraphInternal");
                method.setAccessible(true);
                method.invoke(setupApp);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        // abort InfoFlow analysis since we don't need the analysis but only need call graph construction
        setupApp.abortAnalysis();

        // dump the methods in an application in a file
        for (SootMethod sootMethod : allMethods) {
            if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName)) {
                writeTofile(sootMethod.getSignature(), "allmethods.txt");
            }
        }

        // (3) validate if the crash point exist in the callgraph
        if (isTargetValid(lineNumber, targetMethod, targetClass)) {
            System.out.println("[Target] line number, method and class are found");
            callbackList = new ArrayList<>();
            try {
                callbackList = readFromfile(callbackListPath);

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error: Callback list file is empty");
            }
            callbackType = new ArrayList<>();
            try {
                callbackType = readFromfile(CallbackFilePath);

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error: callback file path is empty");
            }

            // (4) refine the call-graph by adding correct execution order of some callbacks of specific object types
            CallGraphUtil callGraphUtil = new CallGraphUtil(getCallbackTypeStateFilePath(), callbackList, callbackType, new InterProceduralCFGRepresentation(callbackList, callbackType, targetPackageName));
            callGraphUtil.setAllMethods(allMethods);
            Set<Edge> addedEdges = callGraphUtil.doCallGraphRefinement(Scene.v().getCallGraph(), targetPackageName);

            // (5) Add the newly added edges to the callgraph
            System.out.println("[Callgraph] # of new edges added to the callgraph after refinement" + addedEdges.size());
            for (Edge edge : addedEdges) {
                Scene.v().getCallGraph().addEdge(edge);
            }

            printCallgraph(Scene.v().getCallGraph());
            System.out.println("[Callgraph] is refined successfully");
            return true;
        }

        // (4) crash point does not exist in the call graph
        System.out.println("[Target] line number, method and class are not found");
        System.out.println("[Target] the analysis is aborted .... ");

        // dump the call graph for further analysis
        printCallgraph(Scene.v().getCallGraph());
        return false;

    }

    public SceneTransformer createTransformer() {
        return new SceneTransformer() {
            @Override
            protected void internalTransform(String s, Map<String, String> map) {
                // Extract the callgraph with considering the callbacks
                ArrayList<String> callbackList = new ArrayList<>();
                try {
                    callbackList = readFromfile(callbackListPath);

                } catch (IOException e) {
                    e.printStackTrace();
                }

                ArrayList<String> callbackType = new ArrayList<>();
                try {
                    callbackType = readFromfile(CallbackFilePath);

                } catch (IOException e) {
                    e.printStackTrace();
                }


                icfg = new InterProceduralCFGRepresentation(callbackList, callbackType, targetPackageName);


                stateSet = new StateSet("/home/maryam/SootTutorial/files/GUI_STATE_API.csv");
                // Create an instance of the analysis setting target line number, method and class name
                analysis = new ReachabilityAnalysis(targetClass, targetMethod, targetPackageName, lineNumber, icfg, stateSet);

                System.out.println("[Analysis] is starting");
                // Perform the analysis
                analysis.doAnalysis();
                System.out.println("[Analysis] is finished");

                solution = analysis.getMeetOverValidPathsSolution();

            }
        };
    }

    public JSONArray logAnalyisResultEnhanced() throws IOException {
        UIUnreachableCallbacks = new HashSet<>();
        UIReachableCallbacks = new HashMap<>();
        ReachableCallbacks = new HashSet<>();
        UnReachableCallbacks = new HashSet<>();
        reachableComponents = new HashSet<>();
        unreachableComponents = new HashSet<>();
        runMethods = new HashMap<>();

        int countComponent = 0, countRfragment = 0, countTfragment = 0, countUICallback = 0, reachableCallback = 0, uistates = 0;

        ArrayList<SootClass> analyzedClasses = getAllClasses();
        analyzedClasses.addAll(Scene.v().getApplicationClasses());
        // @methodArray: the output json file consisting of several methods and the reachability analysis of them.
        JSONArray methodArray = new JSONArray();
        System.out.println("[Info] # of Analyzed Methods: " + analysis.getMethods().size());
        System.out.println("[Info] # of Found Methods by Soot: " + allMethods.size());
        System.out.println("[Classes] analyzed Classes"+ getAllClasses().size());
        System.out.println("[Classes] Found classes by Soot "+ Scene.v().getApplicationClasses().size());

        // Find the components and add their callbacks to reachable callbacks if they have at least one
        for (SootClass sootClass : analyzedClasses){

            boolean isComponentReachable = false, isFragmentReachable = false;
            ArrayList<String> tempUnreachableCallbacks = new ArrayList<>();
            totalStatements += countTotalStatements(sootClass);
            String superClass = icfg.getSuperClass(sootClass);

            // Is class under app package or is it a library class?
            if (sootClass.getPackageName().startsWith(targetPackageName)) {
                // Is it a component ?
                if (icfg.isComponent(superClass)) {

                    countComponent += 1;
                    for (SootMethod sootMethod : sootClass.getMethods()) {

                        if (analysis.getMethods().contains(sootMethod)) {
                            // let's keep track of reachable or unreachable states
                            System.out.println("[method] " + sootMethod.getSubSignature());
                            JSONArray functionArray = new JSONArray();
                            States methodState = new States();
                            // yes: check the reachability
                            functionArray = checkReachability(sootMethod, methodState);
                            methodArray.add(functionArray);

                            if(!methodState.getStateMap().isEmpty())
                                uistates += methodState.getStateMap().size();

                            // does the analysis have the method?
                            if (icfg.isLifeCycleCallback(sootMethod)) {
                                //      does it have target statement ?
                                //      does it have reachable stmts?
                                //      does it have states ?
                                if (methodState.isReachable() || isComponentReachable) {
                                    if (!isComponentReachable)
                                        isComponentReachable = true;
                                    // add the callback to reachableCallbacklist
                                    ReachableCallbacks.add(sootMethod.getSignature());
                                    reachableCallback++;
                                } else {
                                    // no: leave it and add it to the unreachable list and add the methodState as well;
                                    tempUnreachableCallbacks.add(sootMethod.getSignature());
                                }
                            } else
                                countUICallback += checkUICallbackReachability(superClass, sootMethod, methodState);

                        }
                    }

                    // if reachability is found
                    if (isComponentReachable) {
                        System.out.println("[component] reachable " + sootClass.getName());
                        reachableComponents.add(sootClass);
                        // change unreachable callbacks to reachable ones
                        States methodState = new States();
                        methodState.setReachable(true);
                        // for all methods that are lifecycle, add them to reachable callbacks
                        for (String temp : tempUnreachableCallbacks) {
                            ReachableCallbacks.add(temp);
                            reachableCallback++;
                        }

                    } else {
                        System.out.println("[component] unreachable " + sootClass.getName() + " with "+ tempUnreachableCallbacks.size());
                        unreachableComponents.add(sootClass);
                        // if not , just add them to unreachable callbacks
                        UnReachableCallbacks.addAll(tempUnreachableCallbacks);
                        setUnreachableCallbacks(superClass,sootClass);
                    }
                } else if (icfg.isFragment(superClass)) {
                    countTfragment++;
                    for (SootMethod sootMethod : sootClass.getMethods()) {

                        if (analysis.getMethods().contains(sootMethod)) {
                            System.out.println("[Method] " + sootMethod.getSubSignature());
                            // let's keep track of reachable or unreachable states
                            JSONArray functionArray = new JSONArray();
                            // Collected states in a method
                            States methodState = new States();
                            // yes: check the reachability
                            functionArray = checkReachability(sootMethod, methodState);
                            methodArray.add(functionArray);
                            if(!methodState.getStateMap().isEmpty())
                                uistates += methodState.getStateMap().size();

                            // does the analysis have the method?
                            if (icfg.isFragmentLifeCycle(sootMethod)) {
                                //      does it have target statement ?
                                //      does it have reachable stmts?
                                //      does it have states ?
                                if (methodState.isReachable() || isFragmentReachable) {
                                    if (!isFragmentReachable)
                                        isFragmentReachable = true;
                                    // add the callback to reachableCallbacklist
                                    ReachableCallbacks.add(sootMethod.getSignature());
                                    reachableCallback++;
                                } else {
                                    // no: leave it and add it to the unreachable list and add the methodState as well;
                                    tempUnreachableCallbacks.add(sootMethod.getSignature());
                                }
                            } else {
                                countUICallback += checkUICallbackReachability(superClass, sootMethod, methodState);
                            }
                        }else{
                            if(isFragmentReachable || analysis.getFragmentList().contains(sootClass.getName())){
                                if(icfg.isFragmentLifeCycle(sootMethod)){
                                    ReachableCallbacks.add(sootMethod.getSignature());
                                    reachableCallback++;
                                }else {
                                    States methodState = new States();
                                    methodState.setReachable(true);
                                    countUICallback += checkUICallbackReachability(superClass, sootMethod, methodState);
                                }
                            }else{
                                if(icfg.isFragmentLifeCycle(sootMethod)) {
                                    tempUnreachableCallbacks.add(sootMethod.getSignature());
                                }
                            }
                        }
                    }

                    // if reachability is found
                    if (isFragmentReachable) {
                        countRfragment++;
                        System.out.println("[fragment] reachable " + sootClass.getName());
                        // change unreachable callbacks to reachable ones
                        States methodState = new States();
                        methodState.setReachable(true);
                        // for all methods that are lifecycle, add them to reachable callbacks
                        for (String temp : tempUnreachableCallbacks) {
                            ReachableCallbacks.add(temp);
                            reachableCallback++;
                        }
                    } else {

                        System.out.println("[fragment] unreachable " + sootClass.getName());
                        // if not , just add them to unreachable callbacks
                        UnReachableCallbacks.addAll(tempUnreachableCallbacks);
                        setUnreachableCallbacks(superClass, sootClass);
                    }
                }

                // Does it have any callback within it handling event?
                // Is the callback reachable ?
                ArrayList<SootMethod> overriden = icfg.isInterface(sootClass);
                if (!overriden.isEmpty()) {
                    for (SootMethod sootMethod : overriden) {
                        // retrieve the corresponding method in this class
                        SootMethod classMethod = sootClass.getMethodUnsafe(sootMethod.getSubSignature());
                        if (classMethod != null) {
                            if (analysis.getMethods().contains(classMethod)) {
                                // let's keep track of reachable or unreachable states
                                //System.out.println("[overriden] " + classMethod.getSignature());
                                JSONArray functionArray = new JSONArray();
                                // Collected states in a method
                                States methodState = new States();
                                // yes: check the reachability
                                functionArray = checkReachability(classMethod, methodState);
                                if(!methodState.getStateMap().isEmpty())
                                    uistates += methodState.getStateMap().size();
                                methodArray.add(functionArray);
                                countUICallback += checkUICallbackReachability(sootMethod.getDeclaringClass().getName(), classMethod, methodState);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("[Component] # of necessary Components: " + reachableComponents.size() + " / " + countComponent);
        System.out.println("[Fragment] # of necessary Fragments: " + countRfragment + " out of " + countTfragment);
        System.out.println("[Fragment] # of found states: " + uistates );
        System.out.println("[Callbacks] # of necessary component/fragment callbacks: " + ReachableCallbacks.size() + " out of " + (ReachableCallbacks.size()+UnReachableCallbacks.size()));
        System.out.println("[Callbacks] # of UI necessary callbacks: " + UIReachableCallbacks.size() + " out of " + (UIReachableCallbacks.size()+UIUnreachableCallbacks.size()));
        return methodArray;
    }


    private void setupConfig(int timeout) {
        
        // Setup target file, android jars, code elimination, timeout and callback per component
        config.getAnalysisFileConfig().setTargetAPKFile(apkPath);
        config.getAnalysisFileConfig().setAndroidPlatformDir(this.androidJarPath);
        config.setCodeEliminationMode(InfoflowConfiguration.CodeEliminationMode.NoCodeElimination);
        config.getCallbackConfig().setCallbackAnalysisTimeout(timeout);
        config.getCallbackConfig().setMaxAnalysisCallbackDepth(20);
        config.getCallbackConfig().setMaxCallbacksPerComponent(100);
        
        System.out.println("[Configuration] timeout for Call Graph Construction is " + config.getCallbackConfig().getCallbackAnalysisTimeout());        
        System.out.println("[Configuration] Max callback depth is " + config.getCallbackConfig().getMaxAnalysisCallbackDepth());
        System.out.println("[Configuration] Max callback per component " + config.getCallbackConfig().getMaxCallbacksPerComponent());
        System.out.println("[Configuration] callback analysis algorithm is " + config.getCallbackConfig().getCallbackAnalyzer());

        config.setEnableReflection(true);
        config.setFlowSensitiveAliasing(true);

        config.setEnableLineNumbers(true);
        config.getCallbackConfig().setEnableCallbacks(true);
        config.getCallbackConfig().setCallbackAnalyzer(InfoflowAndroidConfiguration.CallbackAnalyzer.Default);
        config.setMergeDexFiles(true);
        config.setTaintAnalysisEnabled(true);
    }

    private void setSetupApp(){
        
        // reset soot and setup soot config
        G.reset();
        Options options = G.v().soot_options_Options();

        // set the path directories and debug level
        options.set_force_android_jar(androidJarPath);
        options.set_soot_classpath(androidJarPath);
        options.set_src_prec(Options.src_prec_apk);
        options.set_prepend_classpath(true);
        options.set_process_dir(Collections.singletonList(apkPath));
        options.set_include_all(true);
        options.set_keep_line_number(true);
        options.set_print_tags_in_output(true);
        options.set_debug(true);
        options.set_keep_offset(true);
        options.set_process_multiple_dex(true);
        //options.set_no_writeout_body_releasing(true);

        switch (CallgraphAlgorithm) {
            case "CHA":
                options.setPhaseOption("cg.cha", "on");
                options.setPhaseOption("cg.all-reachable", "true");
                break;
            case "VTA":
                options.setPhaseOption("cg.spark", "on");
                options.setPhaseOption("cg.all-reachable", "true");
                options.setPhaseOption("cg.spark", "vta:true");
                options.setPhaseOption("cg.spark", "string-constants:true");
                break;
            case "RTA":
                options.setPhaseOption("cg.spark", "on");
                options.setPhaseOption("cg.spark", "rta:true");
                options.setPhaseOption("cg.spark", "on-fly-cg:false");
                options.setPhaseOption("cg.spark", "string-constants:true");
                break;
            default:
                options.setPhaseOption("cg.spark", "on");
                options.setPhaseOption("cg.all-reachable", "true");
                options.setPhaseOption("cg.safe-newinstance", "true");
                options.setPhaseOption("cg", "implicit-entry:true");
                options.setPhaseOption("cg.spark", "string-constants:true");
        }

        List<String> excludes = new ArrayList<>();
        // exclude libraries that we do not want to go under the analysis
        excludes.add("java");
        excludes.add("javax");
        //excludes.add("android");
        //excludes.add("androidx");
        excludes.add("sun");
        options.set_exclude(excludes);

        // add fragment classes bodies
        Scene.v().addBasicClass("androidx.fragment.app.FragmentActivity", BODIES);
        Scene.v().addBasicClass("androidx.fragment.app.ListFragment", BODIES);
        Scene.v().addBasicClass("androidx.fragment.app.Fragment", BODIES);

        SootConfigForAndroid sootConfigForAndroid = new SootConfigForAndroid();
        sootConfigForAndroid.setSootOptions(options, config);
        setupApp.setSootConfig(sootConfigForAndroid);

        // set callback list
        setupApp.setCallbackFile(CallbackFilePath);

        System.out.println("[Configuration] is done");
    }

    public void setCallbackFilePath(String callbackFilePath) {
        CallbackFilePath = callbackFilePath;
    }

    public void setCallbackTypeStateFilePath(String callbackTypeStateFilePath) {
        CallbackTypeStateFilePath = callbackTypeStateFilePath;
    }

    public String getCallbackTypeStateFilePath() {
        return CallbackTypeStateFilePath;
    }

    public void setTargetPackageName(String targetPackageName) {
        this.targetPackageName = targetPackageName;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }


    public ArrayList<SootClass> getAllClasses(){
        ArrayList<SootClass> classes = new ArrayList<>();
        int count = 0;

        for(SootMethod sootMethod: analysis.getMethods()){
            if(!classes.contains(sootMethod.getDeclaringClass()))
                classes.add(sootMethod.getDeclaringClass());
            if(Scene.v().getApplicationClasses().contains(sootMethod.getDeclaringClass()))
                count++;
        }
        classes.addAll(Scene.v().getApplicationClasses());

        System.out.println(" [not found] " + count);

        return classes;
    }

    private int countTotalStatements(SootClass sootClass) {
        int count = 0;
        if(sootClass.getPackageName().startsWith(targetPackageName)){
            for(SootMethod sootMethod: sootClass.getMethods()){
                if(sootMethod.hasActiveBody()){
                    count += sootMethod.getActiveBody().getUnits().size();
                }
            }
        }
        return count;
    }

    private void setUnreachableCallbacks(String superClass, SootClass sootClass) {
        for(SootMethod sootMethod: sootClass.getMethods()){
            if(icfg.isLifeCycleCallback(sootMethod) ){
                UnReachableCallbacks.add(sootMethod.getSignature());
            }
            if(icfg.isUICallback(sootMethod,superClass)){
                UIUnreachableCallbacks.add(sootMethod.getSignature());
            }
        }
    }

    private int checkUICallbackReachability(String superClass, SootMethod sootMethod, States methodState) {
        if (icfg.isUICallback(sootMethod, superClass)) {
            // check if it is a UI callback
            System.out.println("[UI callback] found " + sootMethod.getSignature());
            if (methodState.isReachable()) {
                System.out.println("[UI callback] necessary");
                /**
                if (methodState.getTargets().size() > 0) {
                    System.out.println("[target] ");
                    UIReachableCallbacks.put(sootMethod.getSignature(), methodState);
                    return 1;
                } else if (methodState.getStateMap().size() > 0) {
                    System.out.println("[statemap] ");
                    UIReachableCallbacks.put(sootMethod.getSignature(), methodState);
                    return 1;
                }
                 **/
                UIReachableCallbacks.put(sootMethod.getSignature(), methodState);
                return 1;
            }
            UIUnreachableCallbacks.add(sootMethod.getSignature());
            return 1;
        }
        return 0;
    }

    private JSONArray checkReachability(SootMethod sootMethod, States methodState) {
        JSONArray functionObj = new JSONArray();

        JSONObject methodSignature = new JSONObject();
        methodSignature.put("Method_Signature", sootMethod.getSignature());
        functionObj.add(methodSignature);
        List<Unit> headNodes = icfg.getControlFlowGraph(sootMethod).getHeads();

        for (Unit unit : sootMethod.getActiveBody().getUnits()) {
            // Get inValue and outValue of @unit
            String inValue = formatConditionsBefore(solution.getValueBefore(unit), unit);
            String outValue = formatConditionsAfter(solution.getValueAfter(unit), unit, sootMethod);

            // Is it head node?
            //      Yes, get the states of it and merge it with curent states
            //      no, continue
            if (headNodes.contains(unit)) {
                Map<Unit, States> valueBefore = solution.getValueBefore(unit);
                if (valueBefore != null) {
                    if (valueBefore.containsKey(unit))
                        if (valueBefore.get(unit).isReachable() && (
                                valueBefore.get(unit).getStateMap() != null ||
                                        valueBefore.get(unit).getTargets() != null)) {
                            methodState.setReachable(true);
                            methodState.addStates(valueBefore.get(unit).getStateMap());
                            methodState.updateTargetSet(valueBefore.get(unit).getTargets());
                            methodState.setIds(valueBefore.get(unit).getIds());
                        }
                }
            }
            if ((outValue.contains("TRUE") && inValue.contains("TRUE")) ||
                    (outValue.contains("FALSE") && inValue.contains("TRUE"))) {
                if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName))
                    NumOfReachablestmts++;
                JSONObject unitInfoObj = new JSONObject();
                JSONObject unitObj = new JSONObject();
                unitInfoObj.put("Line", unit.getJavaSourceStartLineNumber());
                unitInfoObj.put("Statement", unit.toString());
                unitInfoObj.put("inValue", inValue);
                unitInfoObj.put("outValue", outValue);
                unitObj.put("Unit", unitInfoObj);
                functionObj.add(unitObj);
            } else {
                if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName))
                    NumOfUnreachablestmts++;
            }
        }
        return functionObj;
    }

    private boolean isComponentClass(SootClass declaringClass) {
        SootClass otherClass = null;
        boolean isSystemClass = false;

        while (!isSystemClass) {
            if (declaringClass.hasSuperclass() && !declaringClass.hasOuterClass()) {
                otherClass = declaringClass.getSuperclass();
                declaringClass = otherClass;
            } else if (declaringClass.hasOuterClass()) {
                otherClass = declaringClass.getOuterClass();
                declaringClass = otherClass;
            }
            if (otherClass != null) {
                if (otherClass.getPackageName().startsWith("android"))
                    isSystemClass = true;
                else if (otherClass.getPackageName().startsWith("java"))
                    return false;
            } else
                return false;
        }
        return otherClass.getName().startsWith("android.app.Activity") ||
                otherClass.getName().startsWith("android.support.v7.app.AppCompatActivity") ||
                otherClass.getName().startsWith("androidx.appcompat.app.AppCompatActivity") ||
                otherClass.getName().startsWith("android.app.Service") ||
                otherClass.getName().startsWith("android.content.BroadcastReceiver") ||
                otherClass.getName().startsWith("android.content.ContentProvider");
    }

    /**
     * Get all methods within the application
     */
    public void getAllApplicationMethods() {
        allMethods = new HashSet<>();
        Iterator<SootClass> iterator = Scene.v().getClasses().snapshotIterator();
        while (iterator.hasNext()) {
            SootClass sootClass = iterator.next();

            if (!sootClass.isApplicationClass() || sootClass.isLibraryClass()) continue;

            if (sootClass.getPackageName().startsWith("java") || sootClass.getName().startsWith("android") || sootClass.getName().startsWith("com.google"))
                continue;


            for (SootMethod sootMethod : sootClass.getMethods()) {

                if (sootMethod.getSignature().contains("checkForExternalPermission")) {
                    if (!sootMethod.hasActiveBody()) {
                        sootMethod.retrieveActiveBody();
                        if (sootMethod.hasActiveBody()) {
                            System.out.println("[Callgraph] ActiveBody retrieved for method " + sootMethod.getSignature());
                        } else {
                            System.out.println("[Callgraph] ActiveBody not retrieved for method " + sootMethod.getSignature());
                        }
                    }

                }
                if (!sootMethod.hasActiveBody()) {
                    if (!(sootMethod.getDeclaringClass().getName().startsWith("java")) &&
                            !(sootMethod.getDeclaringClass().getPackageName().startsWith("android")) &&
                            !(sootMethod.getDeclaringClass().getPackageName().startsWith("com.google")) &&
                            !(sootMethod.getDeclaringClass().getPackageName().startsWith("org.junit")) &&
                            !sootClass.isInterface()) {
                        try {
                            if (sootMethod.getSignature().contains("onCreateView") || sootMethod.getSignature().contains("Fragment"))
                                System.out.println("method is :" + sootMethod.getSignature());

                            sootMethod.retrieveActiveBody();

                        } catch (Exception ignored) {
                            System.out.println("ERROR: " + ignored.toString());
                        }
                    }
                }
                allMethods.add(sootMethod);
            }
        }
        System.out.println("LOG: app has " + allMethods.size() + " methods!");

    }

    public void getUIMethods() throws IOException {
        ArrayList<String> methodList = new ArrayList<>();
        Map<SootMethod, SootMethod> overridenMethods = new HashMap<>();
        for (SootMethod sootMethod : allMethods) {
            if (sootMethod.hasActiveBody()) {

                // if the method's package is similar to target package name,
                //      add # of statements to total statements
                //    if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName))
                //        totalStatements += sootMethod.getActiveBody().getUnits().size();

                // if the method's package is similar to target package name,
                //      and it is not among reachable callbacks
                //      add # of statements to total statements


                boolean isUICallback = isUICallback(sootMethod);
                boolean isLifeCycleCallback = isLifeCycleCallback(sootMethod);

                if (isUICallback || isLifeCycleCallback) {

                    if (!UIReachableCallbacks.containsKey(sootMethod.getSignature())) {

                        boolean isReachable = false;
                        boolean canContinue = true;
                        SootClass declaringClass = null;
                        if (sootMethod.getDeclaringClass().hasSuperclass() && !sootMethod.getDeclaringClass().hasOuterClass()) {
                            declaringClass = sootMethod.getDeclaringClass().getSuperclass();
                        } else if (sootMethod.getDeclaringClass().hasOuterClass()) {
                            declaringClass = sootMethod.getDeclaringClass().getOuterClass();
                        }

                        // (1) Is it a callback within reachable components?

                        // (2) Is the method within superClass or OuterClass?
                        while (canContinue) {
                            if (declaringClass != null) {
                                if (declaringClass.getPackageName().startsWith(targetPackageName)) {
                                    SootMethod similarMethod = declaringClass.getMethodUnsafe(sootMethod.getName(), sootMethod.getParameterTypes(), sootMethod.getReturnType());
                                    if (similarMethod != null) {
                                        overridenMethods.put(sootMethod, similarMethod);
                                        isReachable = true;
                                        canContinue = false;
                                    }
                                } else
                                    canContinue = false;
                                if (canContinue) {
                                    if (declaringClass.hasSuperclass() && !declaringClass.hasOuterClass()) {
                                        declaringClass = declaringClass.getSuperclass();
                                    } else if (declaringClass.hasOuterClass()) {
                                        declaringClass = declaringClass.getOuterClass();
                                    }
                                }
                            }
                        }

                        if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName) && !isReachable) {
                            UIUnreachableCallbacks.add(sootMethod.getSignature());
                            methodList.add(sootMethod.getSignature() + "\tttt irrelevant callback + \n");
                        } else if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName) && isReachable) {
                            methodList.add(sootMethod.getSignature() + "\tttt necessary callback + \n");
                            States temp = new States();
                            temp.setReachable(true);
                            UIReachableCallbacks.put(sootMethod.getSignature(), temp);
                        }
                    } else {
                        if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName))
                            methodList.add(sootMethod.getSignature() + "\tttt  necessary callback + \n");
                    }
                }
            }
        }

        for (SootMethod sootMethod : overridenMethods.keySet()) {
            SootMethod similarMethod = overridenMethods.get(sootMethod);

            if (UIReachableCallbacks.containsKey(similarMethod.getSignature())) {
                States temp = new States();
                temp.setReachable(true);
                UIReachableCallbacks.put(sootMethod.getSignature(), temp);
                UIUnreachableCallbacks.remove(sootMethod.getSignature());
            }
        }

        Collections.sort(methodList);
        writeTofile(methodList.toString(), outputPath + "/callbacks.txt");
    }

    public String formatConditionsAfter(Map<Unit, States> value, Unit unit, SootMethod sootMethod) {
        StringBuffer sb = new StringBuffer();

        if (value == null) {
            sb.append(" Reachability =  FALSE ");
            return sb.toString();
        }
        List<Unit> successors = analysis.getIcfg().getControlFlowGraph(sootMethod).getSuccsOf(unit);
        boolean isReachable = false;
        for (Unit temp : successors) {
            if (value.containsKey(temp)) {
                if (value.get(temp).isReachable()) {
                    isReachable = true;
                }
            }
        }
        if (isReachable) {
            sb.append(" Reachability =  TRUE  ");
        } else {
            sb.append(" Reachability =  FALSE ");
        }


        return sb.toString();
    }

    public String formatConditionsBefore(Map<Unit, States> value, Unit unit) {
        StringBuilder sb = new StringBuilder();
        if (value == null) {
            sb.append(" Reachability =  FALSE ");
            return sb.toString();
        }

        States currentUnit = value.get(unit);
        if (currentUnit != null) {
            if (currentUnit.isReachable()) {
                String isTarget = currentUnit.getTargets().contains(unit) ? "yes " : "no";
                sb.append(" Reachability =  TRUE  and isTarget ").append(isTarget);
            } else {
                sb.append(" Reachability =  FALSE ... ");
            }
            if (!currentUnit.getStateMap().isEmpty()) {
                sb.append(", { ");
                for (Abstraction abstraction : currentUnit.getStateMap()) {
                    abstractionCount++;
                    if (abstraction.getVariable() instanceof LocalVariable)
                        if (!sb.toString().contains(" [ State:  " + ((LocalVariable) abstraction.getVariable()).getLocal().toString() + " has " + abstraction.getState().getMethodSignature() + " == " + abstraction.getValue() + " ]"))
                            sb.append(" [ State:  ").append(((LocalVariable) abstraction.getVariable()).getLocal()).append(" has ").append(abstraction.getState().getMethodSignature()).append(" == ").append(abstraction.getValue()).append(" ] , on").append(currentUnit.getIds().toString());
                        else if (abstraction.getVariable() instanceof GlobalVariable)
                            if (!sb.toString().contains(" [ State:  " + ((GlobalVariable) abstraction.getVariable()).getGlobal().toString() + " has " + abstraction.getState().getMethodSignature() + " == " + abstraction.getValue() + " ]"))

                                sb.append(" [ State:  ").append(((GlobalVariable) abstraction.getVariable()).getGlobal()).append(" has ").append(abstraction.getState().getMethodSignature()).append(" == ").append(abstraction.getValue()).append(" ] , on").append(currentUnit.getIds().toString());

                }
                sb.append(" } ");
            }
        } else
            sb.append(" Reachability =  FALSE ");

        return sb.toString();
    }



    public boolean isTargetValid(int lineNumber, String methodName, String className) {
        System.out.println("Target is class " + className + " method " + methodName + " line " + lineNumber);
        for (SootMethod sootMethod : allMethods) {
            if (sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName) ||
                    targetPackageName.startsWith(sootMethod.getDeclaringClass().getPackageName())) {
                // get return value of method and target method name
                String returnValueS = sootMethod.getReturnType().toString();
                String returnValueT = methodName.substring(methodName.indexOf(":") + 2, methodName.lastIndexOf(" "));
                // get method name and target method name
                String methodNameS = sootMethod.getName();
                int base = methodName.indexOf(":");
                String temp = methodName.substring(base + 1, methodName.indexOf("("));
                String methodNameT = methodName.substring(base + temp.lastIndexOf(" ") + 2, methodName.indexOf("("));
                // get method parameters types and target method parameters types
                String methodParS = sootMethod.getParameterTypes().toString();
                String methodParT = methodName.substring(methodName.indexOf("(") + 1, methodName.indexOf(")"));
                // check if the return values are the same
                //          the parameters are the same
                if (returnValueS.equalsIgnoreCase(returnValueT) && methodNameS.equals(methodNameT)) {

                    if (methodParS.replaceAll("\\s", "").equalsIgnoreCase("[" + methodParT + "]")) {
                        // System.out.println("[Info] methods have same parameters and return values");
                        if (sootMethod.hasActiveBody()) {
                            int startLine = 1000000;
                            int endLine = 0;
                            for (Unit unit : sootMethod.getActiveBody().getUnits())
                                if (unit.getJavaSourceStartLineNumber() != -1) {
                                    if (unit.getJavaSourceStartLineNumber() > endLine)
                                        endLine = unit.getJavaSourceStartLineNumber();
                                    if (unit.getJavaSourceStartLineNumber() < startLine)
                                        startLine = unit.getJavaSourceStartLineNumber();
                                }

                            if (startLine >= -1 && endLine > 0 && endLine >= startLine) {
                                System.out.println("[Info] Target Line number is" + lineNumber);
                                if ((lineNumber <= endLine) && (lineNumber >= startLine)) {
                                    System.out.println("[Info] Lines match. Correct range is " + startLine + " to " + endLine);
                                    for (Unit unit1 : sootMethod.getActiveBody().getUnits())
                                        System.out.println("Unit " + unit1.toString() + "at line " + unit1.getJavaSourceStartLineNumber());
                                    return true;
                                }
                            } else
                                System.out.println("[Info] startline " + startLine + " endline " + endLine);
                            System.out.println("[Info] Lines doesn't match. Correct range is " + startLine + " to " + endLine);
                            for(Unit unit: sootMethod.getActiveBody().getUnits())
                                System.out.println("unit "+ unit.toString() + unit.getJavaSourceStartLineNumber());
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isLifeCycleCallback(SootMethod sootMethod) {
        SootClass declaringClass = sootMethod.getDeclaringClass();

        boolean isUIorLifecycleCallback = false;
        // Check if the class "extends" any super class or "implements" any interfaces
        if (declaringClass.getInterfaces() != null) {
            Chain<SootClass> interfaces = declaringClass.getInterfaces();
            for (SootClass sootClass : interfaces) {
                if (sootClass.getName().startsWith("android") || sootClass.getName().startsWith("com.google")) {
                    isUIorLifecycleCallback = analysis.getIcfg().isComponentCallback(sootClass, sootMethod);
                    if (isUIorLifecycleCallback)
                        break;
                    isUIorLifecycleCallback = analysis.getIcfg().isUICallback(sootClass, sootMethod);
                    if (isUIorLifecycleCallback)
                        break;
                }
            }
        }
        if (isUIorLifecycleCallback) {
            try {
                // Check if method is overriden in the application code
                SootMethod sootMethod1 = declaringClass.getMethodUnsafe(sootMethod.getName(), sootMethod.getParameterTypes(), sootMethod.getReturnType());
                if (sootMethod1 != null) {
                    return true;
                }
            } catch (AmbiguousMethodException e) {
                e.printStackTrace();
            }
        }

        // Is there any "extends" relationship?
        if (sootMethod.getDeclaringClass().hasSuperclass() || sootMethod.getDeclaringClass().hasOuterClass()) {
            SootClass temp = null;
            if (sootMethod.getDeclaringClass().hasOuterClass())
                temp = sootMethod.getDeclaringClass().getOuterClass();
            else if (sootMethod.getDeclaringClass().hasSuperclass())
                temp = sootMethod.getDeclaringClass().getSuperclass();
            do {
                if (temp.getName().equals("java.lang.Object")) {
                    break;
                } else {
                    // Is it an android class?
                    if (temp.getName().startsWith("android") || temp.getName().startsWith("com.google")) {
                        isUIorLifecycleCallback = analysis.getIcfg().isComponentCallback(temp, sootMethod);
                        if (isUIorLifecycleCallback)
                            break;
                        isUIorLifecycleCallback = analysis.getIcfg().isUICallback(temp, sootMethod);
                        if (isUIorLifecycleCallback)
                            break;
                    }
                    // Does it declare an interface?
                    if (temp.getInterfaces() != null) {
                        Chain<SootClass> interfaces = temp.getInterfaces();
                        for (SootClass sootClass : interfaces) {
                            if (sootClass.getName().startsWith("android") || sootClass.getName().startsWith("com.google")) {
                                isUIorLifecycleCallback = analysis.getIcfg().isComponentCallback(sootClass, sootMethod);
                                if (isUIorLifecycleCallback)
                                    break;
                                isUIorLifecycleCallback = analysis.getIcfg().isUICallback(sootClass, sootMethod);
                                if (isUIorLifecycleCallback)
                                    break;
                            }
                        }
                    }
                    if (isUIorLifecycleCallback)
                        break;

                    // Does it have any super or outer class?
                    if (temp.hasSuperclass() && temp.hasOuterClass())
                        temp = temp.getOuterClass();
                    else if (temp.hasSuperclass())
                        temp = temp.getSuperclass();
                }
            } while (temp != null && (temp.hasSuperclass()));

            if (isUIorLifecycleCallback) {
                try {
                    // Check if method is overriden in the application code
                    SootMethod sootMethod1 = declaringClass.getMethodUnsafe(sootMethod.getName(), sootMethod.getParameterTypes(), sootMethod.getReturnType());
                    if (sootMethod1 != null) {
                        return true;
                    }
                } catch (AmbiguousMethodException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public boolean isUICallback(SootMethod sootMethod) {
        SootClass declaringClass = sootMethod.getDeclaringClass();
        boolean isUIorLifecycleCallback = false;

        // Is there any "extends" relationship?
        if (sootMethod.getDeclaringClass().hasSuperclass() || sootMethod.getDeclaringClass().hasOuterClass()) {
            SootClass temp = null;

            if (sootMethod.getDeclaringClass().hasOuterClass())
                temp = sootMethod.getDeclaringClass().getOuterClass();
            else if (sootMethod.getDeclaringClass().hasSuperclass())
                temp = sootMethod.getDeclaringClass().getSuperclass();
            do {
                assert temp != null;
                if (temp.getName().equals("java.lang.Object")) {
                    break;
                } else {
                    if (temp.getName().startsWith("android")) {
                        isUIorLifecycleCallback = analysis.getIcfg().isUICallback(temp, sootMethod);
                        break;
                    }
                    if (temp.hasSuperclass() && temp.hasOuterClass())
                        temp = temp.getOuterClass();
                    else if (temp.hasSuperclass())
                        temp = temp.getSuperclass();
                }
            } while (temp != null && (temp.hasSuperclass()));

            if (isUIorLifecycleCallback) {
                try {
                    // Check if method is overriden in the application code
                    SootMethod sootMethod1 = declaringClass.getMethodUnsafe(sootMethod.getName(), sootMethod.getParameterTypes(), sootMethod.getReturnType());
                    if (sootMethod1 != null) {
                        return true;
                    }
                } catch (AmbiguousMethodException e) {
                    e.printStackTrace();
                }
            }
        }

        if (sootMethod.getDeclaringClass().getInterfaces().size() > 0) {
            // extract interfaces and check if the method is abstract and implemented within the current
            //     sootClass context
            for (SootClass sootClass : declaringClass.getInterfaces()) {
                isUIorLifecycleCallback = analysis.getIcfg().isUICallback(sootClass, sootMethod);
                if (isUIorLifecycleCallback) {
                    try {
                        SootMethod sootMethod1 = sootClass.getMethodUnsafe(sootMethod.getName(), sootMethod.getParameterTypes(), sootMethod.getReturnType());
                        if (sootMethod1 != null) {
                            return true;
                        }
                    } catch (AmbiguousMethodException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
        return false;
    }

    public void printCallgraph(CallGraph callGraph) throws IOException {
        // Print some general information of the generated callgraph. Note that although usually the nodes in callgraph
        // are assumed to be methods, the edges in Soot's callgraph is from Unit to SootMethod.
        AndroidCallGraphFilter androidCallGraphFilter = new AndroidCallGraphFilter(targetPackageName);
        int classIndex = 0;
        String data = "";
        System.out.println("[# of valid classes]"+ androidCallGraphFilter.getValidClasses().size());
        for (SootClass sootClass : androidCallGraphFilter.getValidClasses()) {
            data += String.format("Class %d: %s", ++classIndex, sootClass.getName());
            System.out.println("class "+ sootClass.getName());
            for (SootMethod sootMethod : sootClass.getMethods()) {
                data += ("------- Method: " + sootMethod.getSignature() + " -------\n");
                int incomingEdge = 0;
                for (Iterator<Edge> iter = callGraph.edgesInto(sootMethod); iter.hasNext(); ) {
                    Edge it = iter.next();
                    data += ("Coming edge is: " + it.getSrc().toString() + "\n");
                    // data += ("Where it comes from" + it.getSrc().method().getActiveBody().toString());
                    //data += ("Body " + it.getTgt().method().getActiveBody().toString());
                    incomingEdge++;
                }
                int outgoingEdge = 0;
                for (Iterator<Edge> iter = callGraph.edgesOutOf(sootMethod); iter.hasNext(); ) {
                    Edge it = iter.next();
                    data += ("Outgoing edge is: " + it.getTgt().toString() + "\n");
                    outgoingEdge++;
                }
                data += (String.format("\tMethod %s, #IncomeEdges: %d, #OutgoingEdges: %d", sootMethod.getName(), incomingEdge, outgoingEdge) + "\n");
            }
        }
        data += ("-----------\n");
        writeTofile(data, outputPath + "/callgraph.txt");

    }

    public Set<String> getReachableCallbacks() {
        return ReachableCallbacks;
    }

    public Set<String> getUnReachableCallbacks() {
        return UnReachableCallbacks;
    }

    public Set<SootClass> getReachableComponents() {
        return reachableComponents;
    }

    public Map<String, States> getUIReachableCallbacks() {
        return UIReachableCallbacks;
    }

    public Set<String> getUIUnreachableCallbacks() {
        return UIUnreachableCallbacks;
    }

    public Map<String, States> getRunMethods() {
        return runMethods;
    }

    public int getNumOfReachablestmts() {
        return NumOfReachablestmts;
    }

    public int getNumOfUnreachablestmts() {
        return NumOfUnreachablestmts;
    }

    public int getTotalStatements() {
        return totalStatements;
    }

    public JSONArray getJsonResult() {
        return jsonResult;
    }

    public void writeTofile(String data, String name) throws IOException {
        File file = new File(name);
        FileWriter fileWriter = new FileWriter(file, true);
        fileWriter.write(data + "\n");
        fileWriter.close();

    }

    public ArrayList<String> readFromfile(String name) throws IOException {
        ArrayList<String> callbackList = new ArrayList<>();
        File file = new File(name);
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.startsWith("<")) {
                String temp = line.substring(line.lastIndexOf("<") + 1, line.lastIndexOf(">"));
                callbackList.add(temp);
            } else {
                callbackList.add(line);
            }
        }
        fileReader.close();

        return callbackList;

    }

    public StateSet getStateSet() {
        return stateSet;
    }

}
