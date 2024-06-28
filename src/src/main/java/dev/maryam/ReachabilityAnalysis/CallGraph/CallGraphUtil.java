package dev.maryam.ReachabilityAnalysis.CallGraph;

import dev.maryam.ReachabilityAnalysis.Callback.CallbackSummary;
import dev.maryam.ReachabilityAnalysis.Callback.CallbackTypeState;
import dev.maryam.ReachabilityAnalysis.Core.InterProceduralCFGRepresentation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.scalar.SimpleLocalUses;
import soot.toolkits.scalar.UnitValueBoxPair;
import soot.util.Chain;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class CallGraphUtil {

    //In this data structure, we keep the possible call-site function calling the callback entry of a class type, e.g. execute the
    //   function where calling it in a program on an AsyncTask object make it call callbacks within it.
    //   < class name : call-site method >
    private HashMap<String, ArrayList<String>> callbackEntries = new HashMap<>();

    // In this data structure, we keep the entrypoint of each  type of classes, e.g. doInBackground is the entry point for AsyncTask
    //   < call-site method name : Lifecycle >
    private Map<String, CallbackTypeState> callbackLifeCycle = new HashMap<>();

    // In this data structure, we keep the entrypoint of each  type of classes, e.g. doInBackground is the entry point for AsyncTask
    //   < call-site method name : Lifecycle >
    private Map<String, String> callbackCallSiteInfo = new HashMap<>();

    // New edges added to the callgraph
    private Set<Edge> addedEdges = new HashSet<>();

    // Name of target package under analysis
    private String targetPackage = "";

    // Remaining methods not added to the callgraph
    public Set<SootMethod> allMethods = new HashSet<>();

    // the path pointing to callback typestates
    private String callbackTypeStateFilePath;


    private ArrayList<String> callbackLists, callbackType;
    private InterProceduralCFGRepresentation icfg;
    private int COUNTERVALUE = 4;


    public CallGraphUtil(String CallbackTypeStateFilePath, ArrayList<String> callbackLists, ArrayList<String> callbackType, InterProceduralCFGRepresentation icfg) {
        this.callbackTypeStateFilePath = CallbackTypeStateFilePath;
        this.callbackLists = callbackLists;
        this.icfg = icfg;
        this.callbackType = callbackType;
    }

    public void setCallbackLifeCycle() {

        // Instantiate the Factory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        // optional, but recommended
        // process XML securely, avoid attacks like XML External Entities (XXE)
        try {
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // parse XML file
            DocumentBuilder db = dbf.newDocumentBuilder();

            Document doc = db.parse(new File(callbackTypeStateFilePath));
            doc.getDocumentElement().normalize();

            // Get all classes and the callback lifecycle defined inside them
            NodeList list = doc.getElementsByTagName("Class");

            // Go through the list of classes
            for (int temp = 0; temp < list.getLength(); temp++) {

                Node node = list.item(temp);
                ArrayList<CallbackSummary> callbackSummaries = new ArrayList<CallbackSummary>();
                ArrayList<String> entryPoints = new ArrayList<String>();

                if (node.getNodeType() == Node.ELEMENT_NODE) {

                    // Get name of the class
                    Element element = (Element) node;
                    String name = element.getAttribute("name");

                    // Get Callbacks
                    NodeList callbackList = element.getElementsByTagName("Callback");
                    for (int temp1 = 0; temp1 < callbackList.getLength(); temp1++) {
                        Node node1 = callbackList.item(temp1);

                        if (node.getNodeType() == Node.ELEMENT_NODE) {

                            Element callbackElement = (Element) node1;
                            String entryPoint = callbackElement.getAttribute("entrypoint");
                            String signature = callbackElement.getElementsByTagName("Signature").item(0).getTextContent();
                            String stateValue = callbackElement.getElementsByTagName("State").item(0).getTextContent();
                            int state = Integer.valueOf(stateValue);
                            ArrayList<Integer> targets = new ArrayList<Integer>();

                            if (!entryPoints.contains(entryPoint)) {
                                entryPoints.add(entryPoint);
                            }

                            NodeList targetCallbacks = callbackElement.getElementsByTagName("Target");

                            for (int temp2 = 0; temp2 < targetCallbacks.getLength(); temp2++) {

                                Node node2 = targetCallbacks.item(temp2);
                                targets.add(Integer.valueOf(node2.getTextContent()));
                            }

                            CallbackSummary callbackSummary = new CallbackSummary();
                            callbackSummary.setSuperClassype(name);
                            callbackSummary.setEntryPointSignature(entryPoint);
                            callbackSummary.setSignature(signature);
                            callbackSummary.setStateId(state);
                            callbackSummary.setTargetCallback(targets);
                            callbackSummaries.add(callbackSummary);


                        }
                    }

                    // Get typestate of callbacks and add them to the callbackTypeState for one class
                    for (String tempEntryPoint : entryPoints) {
                        // add entrypoints for a target class
                        if (callbackEntries.get(name) == null) {
                            callbackEntries.put(name, new ArrayList<String>());
                            callbackEntries.get(name).add(tempEntryPoint);
                        } else {
                            callbackEntries.get(name).add(tempEntryPoint);
                        }


                        for (CallbackSummary tempSummary : callbackSummaries) {

                            // add the callback to the corresponding lifecycle for its entrypoint
                            if (tempSummary.getEntryPointSignature().equals(tempEntryPoint)) {

                                CallbackTypeState summaries = callbackLifeCycle.get(tempEntryPoint);

                                if (summaries != null) {

                                    // if there is one more elements added, check if the target callback B is target of A,
                                    //          add it immediately after it.
                                    summaries.insertCallbackSummaryToList(tempSummary);
                                    callbackLifeCycle.remove(tempEntryPoint);
                                    callbackLifeCycle.put(tempEntryPoint, summaries);

                                } else {
                                    // If no item is added, add the element to the list
                                    summaries = new CallbackTypeState(tempEntryPoint);
                                    summaries.addCallbackSummaryToList(tempSummary);
                                    callbackLifeCycle.put(tempEntryPoint, summaries);

                                }


                            }
                        }

                        // Get the entrypoint callback to the callsite
                        callbackCallSiteInfo.put(tempEntryPoint, callbackLifeCycle.get(tempEntryPoint).getCallbackLifeCycle().get(0).getSignature());

                    }
                }
            }

        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            System.out.println("LOG: ParseConfigurationException Error" + e.toString());
        } catch (SAXException e) {
            e.printStackTrace();
            System.out.println("LOG: SAXException Error" + e.toString());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("LOG: IOException Error" + e.toString());
        }


    }

    public void setAllMethods(Set<SootMethod> allMethods) {
        for (SootMethod sootMethod : allMethods) {
            this.allMethods.add(sootMethod);
        }
    }

    public Set<Edge> doCallGraphRefinement(CallGraph callGraph, String targetPackageName) {

        this.targetPackage = targetPackageName;

        setCallbackLifeCycle();
        mapEntrypointsCallbacks(callGraph, targetPackageName);

        System.out.println("Entry points are added");
        refineCallGraph(callGraph);
        System.out.println("callgraph is refined");

        return addedEdges;

    }

    /**
     * In this method,
     * * 1) we get the callgraph built up by flowdroid
     * * 2) we get the list of callbacks
     * * 3) check if any exists within the callgrah, update it according to the callback summary
     *
     * @param callGraph:         callgraph retrieved by flowDroid
     * @param targetPackageName: the target package
     */
    public void mapEntrypointsCallbacks(CallGraph callGraph, String targetPackageName) {

        // ( 3 ) We refine the callgaph by adding correct order of callbacks that are defined within the code but not
        //          added to it.

        // ( 4 ) We should map the location to where the callbacks being attached for making the callgraph precise
        Iterator<Edge> edges = callGraph.iterator();

        List<Edge> outEdges = new ArrayList<>();
        while (edges.hasNext()) {

            // ( 5 ) Get current Edge
            Edge currentEdge = edges.next();
            Chain<Unit> calleeBody = currentEdge.src().getActiveBody().getUnits();

            // ( 6 ) Get the callee and its signature
            SootMethod caller = currentEdge.getSrc().method();
            SootMethod callee = currentEdge.getTgt().method();

            // ( 7 ) Check if the SrcUnit is Invocation or Assignemnt statement
            if (currentEdge.srcStmt() instanceof InvokeStmt) {

                //  ( 8 ) Get caller class where the callee method is invoked within
                SootClass callerClass = currentEdge.srcStmt().getInvokeExpr().getMethodRef().getDeclaringClass();

                // ( 9 ) Get the callee subsignature
                String calleeSignature = callee.getSubSignature();

                // ( 10 ) Check if the caller class exists within Application classes && application package
                boolean isAppClass = Scene.v().getApplicationClasses().contains(callerClass);
                if (isAppClass && callerClass.getPackageName().startsWith(targetPackageName)) {

                    // (11) let's add the call to this method if it is overriden in other child classes
                    List<SootMethod> otherEdges = new ArrayList<>();
                    otherEdges = getParentDeclaredMethod(currentEdge.srcStmt().getInvokeExpr().getMethod(), currentEdge.srcStmt().getInvokeExpr().getMethod().getDeclaringClass());

                    for (SootMethod similarMethod : otherEdges) {
                        // add the edges just after this statement :)
                        Unit targetUnit = currentEdge.srcUnit();
                        InvokeExpr invokeExpr = getInvokeExpr(currentEdge.srcStmt().getInvokeExpr(), similarMethod);
                        InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(invokeExpr);
                        calleeBody.insertAfter(invokeStmt, targetUnit);
                        Edge newEdge = new Edge(currentEdge.src(), invokeStmt, similarMethod, Kind.VIRTUAL);
                        addedEdges.add(newEdge);
                        // let's add the edges within @similarMethod to the Callgraph
                        outEdges.addAll(updateOutEdges(similarMethod, COUNTERVALUE));
                    }

                    // (12) Is it a Runnable class?
                    getRunnables(outEdges, currentEdge, calleeBody, callerClass);

                    // (13) Let's find the callbacks and add the edges
                    //  (1-1) Get all possible functions that can be called to trigger a callback for the calleeClass
                    ArrayList<String> functionEntryMethods = callbackEntries.get(callerClass.getName());

                    //  (1-2)
                    if (callerClass.hasSuperclass()) {
                        if (!callerClass.getSuperclass().getName().startsWith("java.lang.Object"))
                            functionEntryMethods = callbackEntries.get(callerClass.getSuperclass().getName());
                    }
                    //  (1-3) Get the target callback to be called after callee
                    String entryCallback = callbackCallSiteInfo.get(calleeSignature);
                    //  (1-4) Check if the callee is one of any of @functionEntryMethods
                    if (functionEntryMethods != null) {

                        boolean isCalled = functionEntryMethods.contains(calleeSignature);
                        if (isCalled) {
                            // (1)  create a new edge consisting of a
                            //     virtual call to the @entryCallback
                            //   Hence, find the corresponding method in callee class type of the entryCallback
                            SootMethod targetCallback = null;

                            // (2) check if the entry callback is declared within the target class:
                            // yes, continue updating the call-graph
                            // o.w, set the first declared callback after it as the entry-point
                            // System.out.println("entry callback " + entryCallback.toString());
                            if (callerClass.declaresMethod(entryCallback)) {
                                targetCallback = callerClass.getMethod(entryCallback);
                            } else {
                                for (CallbackSummary callback : callbackLifeCycle.get(calleeSignature).getCallbackLifeCycle()) {
                                    int indexOfP = callback.getSignature().indexOf("(");
                                    int indexPfSpace = callback.getSignature().indexOf(" ");

                                    String callbackName = callback.getSignature().substring(indexPfSpace + 1, indexOfP);
                                    //System.out.println("CallbackName is " + callbackName);

                                    boolean isSuperMethod = callerClass.getSuperclass().declaresMethod(callback.getSignature());
                                    boolean superMethod = callerClass.getSuperclass().declaresMethod(callback.getSignature());

                                    if (callerClass.declaresMethod(callback.getSignature())) {
                                        targetCallback = callerClass.getMethod(callback.getSignature());
                                        break;
                                    }
                                }
                            }

                            if (targetCallback != null) {
                                //     Check if the method has active body and is within the methods retrieved in the app package target
                                if (allMethods.contains(targetCallback)) {

                                    Local baseObject;
                                    //System.out.println("LOG: callsite is " + currentEdge.srcStmt().toString());

                                    if (currentEdge.srcStmt().getInvokeExpr() != null) {
                                        InvokeExpr invokeExpr = currentEdge.srcStmt().getInvokeExpr();
                                        InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
                                        Value base = instanceInvokeExpr.getBase();
                                        baseObject = (Local) base;


                                        // Get Arguments of callee and target Callback and the parametertypes
                                        List<Value> calleeArgs = currentEdge.srcStmt().getInvokeExpr().getArgs();
                                        List<Type> calleeParamType = currentEdge.tgt().getParameterTypes();

                                        List<Value> targetArgs = targetCallback.getActiveBody().getParameterRefs();
                                        List<Type> targetParamType = targetCallback.getParameterTypes();
                                        boolean isArgPassed = false;

                                        // Get return type of the target callback
                                        Type targetReturnType = targetCallback.getReturnType();

                                        // Create a new callsite for the callback to point the target edge to it to
                                        //    be called after it
                                        Local local = null;
                                        VirtualInvokeExpr virtualInvokeExpr;
                                        Edge newEdge = null;
                                        Unit calleeUnit = currentEdge.srcUnit();
                                        Unit temp = null;

                                        // Check if the targetParamType is among calleeParamType
                                        LocalGenerator lg = Scene.v().createLocalGenerator(currentEdge.src().getActiveBody());

                                        if (!targetParamType.isEmpty()) {

                                            if (calleeParamType.equals(targetParamType)) {

                                                if (targetReturnType.equals(VoidType.v())) {
                                                    virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), calleeArgs);
                                                    temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                } else {
                                                    local = lg.generateLocal(targetReturnType);
                                                    virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), calleeArgs);
                                                    temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);

                                                }
                                                calleeBody.insertAfter(temp, calleeUnit);
                                                calleeUnit = temp;
                                                newEdge = new Edge(currentEdge.src(), calleeUnit, targetCallback, Kind.VIRTUAL);
                                                addedEdges.add(newEdge);
                                                // ( - ) we can remove the edge here!
                                                isArgPassed = true;
                                            } else {
                                                int counter = 0;
                                                // Is there any parameter of the callee within the targetCallback ?

                                                for (Type t : targetParamType) {
                                                    if (calleeParamType.contains(t))
                                                        counter++;
                                                }
                                                // Let's remove the ones unnecessary and link the one necessary later on
                                                if (counter == targetParamType.size()) {
                                                    List<Value> passingValues = new ArrayList<>();
                                                    for (Type t : targetParamType) {
                                                        if (calleeParamType.contains(t)) {
                                                            int index = calleeParamType.indexOf(t);
                                                            passingValues.add(calleeArgs.get(index));
                                                        }
                                                    }

                                                    if (targetReturnType.equals(VoidType.v())) {
                                                        virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), passingValues);
                                                        temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                    } else {
                                                        local = lg.generateLocal(targetReturnType);
                                                        virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), passingValues);
                                                        temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                    }

                                                    calleeBody.insertAfter(temp, calleeUnit);
                                                    calleeUnit = temp;
                                                    newEdge = new Edge(currentEdge.src(), temp, targetCallback, Kind.VIRTUAL);
                                                    addedEdges.add(newEdge);
                                                    isArgPassed = true;
                                                }
                                            }
                                        } else {
                                            local = lg.generateLocal(targetReturnType);
                                            virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), targetArgs);
                                            temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                        }

                                        // (3)  add other callbacks in the lifecycle
                                        for (CallbackSummary callback : callbackLifeCycle.get(calleeSignature).getCallbackLifeCycle()) {

                                            if (!callback.getSignature().equals(targetCallback.getSubSignature()) && callerClass.declaresMethod(callback.getSignature())) {
                                                SootMethod callbackMethod;

                                                callbackMethod = callerClass.getMethod(callback.getSignature());

                                                if (callbackMethod.hasActiveBody()) {

                                                    targetArgs = callbackMethod.getActiveBody().getParameterRefs();
                                                    targetParamType = callbackMethod.getParameterTypes();
                                                    targetReturnType = callbackMethod.getReturnType();

                                                    if (isArgPassed) {
                                                        // System.out.println("LOG: arg passed");
                                                        if (local != null) {
                                                            if (new String("[" + local.getType() + "]").equals(targetParamType.toString())) {

                                                                // Pass value by reference to the callback from previous calls
                                                                virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), local);
                                                                if (targetReturnType.equals(VoidType.v())) {
                                                                    temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                } else {
                                                                    local = lg.generateLocal(targetReturnType);
                                                                    temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                }
                                                            } else {
                                                                //System.out.println("LOG: arg with different type");
                                                                virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), targetArgs);
                                                                if (targetReturnType.equals(VoidType.v())) {
                                                                    temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                } else {
                                                                    local = lg.generateLocal(targetReturnType);
                                                                    temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                }
                                                            }
                                                            calleeBody.insertAfter(temp, calleeUnit);
                                                            calleeUnit = temp;
                                                            newEdge = new Edge(currentEdge.src(), temp, callbackMethod, Kind.VIRTUAL);
                                                            addedEdges.add(newEdge);
                                                        }

                                                    } else {
                                                        if (calleeParamType.equals(targetParamType)) {
                                                            virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), calleeArgs);
                                                            if (targetReturnType.equals(VoidType.v())) {
                                                                temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                            } else {
                                                                local = lg.generateLocal(targetReturnType);
                                                                temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);

                                                            }
                                                            isArgPassed = true;
                                                        } else {
                                                            if (local != null) {

                                                                if (local.getType().equals(targetParamType) && !local.getType().equals(VoidType.v())) {
                                                                    // Pass value by reference to the callback from previous calls
                                                                    virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), local);
                                                                    if (targetReturnType.equals(VoidType.v())) {
                                                                        temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                    } else {
                                                                        local = lg.generateLocal(targetReturnType);
                                                                        temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                    }
                                                                } else {
                                                                    int counter = 0;
                                                                    // Is there any parameter of the callee within the targetCallback ?

                                                                    for (Type t : targetParamType) {
                                                                        if (calleeParamType.contains(t))
                                                                            counter++;
                                                                    }
                                                                    // Let's remove the ones unnecessary and link the one necessary later on
                                                                    if (counter > 0 && counter == targetParamType.size()) {
                                                                        List<Value> passingValues = new ArrayList<>();
                                                                        for (Type t : targetParamType) {
                                                                            if (calleeParamType.contains(t)) {
                                                                                int index = calleeParamType.indexOf(t);
                                                                                passingValues.add(calleeArgs.get(index));
                                                                            }
                                                                        }

                                                                        virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), passingValues);
                                                                        if (targetReturnType.equals(VoidType.v())) {
                                                                            temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                        } else {
                                                                            local = lg.generateLocal(targetReturnType);
                                                                            temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                        }

                                                                        isArgPassed = true;
                                                                    }
                                                                }
                                                            }

                                                        }
                                                        calleeBody.insertAfter(temp, calleeUnit);
                                                        calleeUnit = temp;
                                                        newEdge = new Edge(currentEdge.src(), temp, callbackMethod, Kind.VIRTUAL);
                                                        addedEdges.add(newEdge);

                                                    }

                                                }
                                            }
                                        }

                                        // (4)  add the final edges to the ending edge ( the last callback in the lifecycle)
                                        outEdges.addAll(updateOutEdges(callee.method(), COUNTERVALUE));

                                    }
                                }
                            }
                        }
                    }
                }

                // ( 14 ) Check if the call-site is an explicit intent call, and add the call to its dummy main method
                //  An explicit intent call is in the form of
                //    <android.content.Intent: void <init>(android.content.Context,java.lang.class)>(var1,class_name)
                //    where @var1 is context type and @class_name is String representation of a class name
                //  There is always call after it to startActivity, startActivityForResult, startService or sendBroadCast
                getComponentCommunication(targetPackageName, currentEdge, callee);

                // if the type of the callee is any form of 1) @callbackType 2) contains set and listener 3) its package consists of view or widget
                addNotResolvedListeners(callee, caller, currentEdge.srcStmt().getInvokeExpr().getArgs(), currentEdge.srcUnit());

            } else if (currentEdge.srcStmt() instanceof AssignStmt) {

                Value rhsop = ((AssignStmt) currentEdge.srcStmt()).getRightOp();
                if (rhsop instanceof InvokeExpr) {
                    // Get caller class where the callee method is invoked within
                    Value lhsop = ((AssignStmt) currentEdge.srcStmt()).getLeftOp();
                    // Get caller class where the callee method is invoked within
                    SootClass callerClass = currentEdge.srcStmt().getInvokeExpr().getMethodRef().getDeclaringClass();
                    // Check if the caller class exists within Application classes && application package
                    boolean isAppClass = Scene.v().getApplicationClasses().contains(callerClass);

                    if (isAppClass && callerClass.getPackageName().startsWith(targetPackageName)) {

                        // (1) let's add the call to this method if it is overriden in other child classes
                        List<SootMethod> otherEdges = new ArrayList<>();
                        otherEdges = getParentDeclaredMethod(currentEdge.srcStmt().getInvokeExpr().getMethod(), currentEdge.srcStmt().getInvokeExpr().getMethod().getDeclaringClass());
                        for (SootMethod similarMethod : otherEdges) {
                            // add the edges just after this statement :)
                            Unit targetUnit = currentEdge.srcUnit();
                            InvokeExpr invokeExpr = getInvokeExpr(rhsop, similarMethod);
                            AssignStmt assignStmt = Jimple.v().newAssignStmt(lhsop, invokeExpr);
                            calleeBody.insertAfter(assignStmt, targetUnit);
                            Edge newEdge = new Edge(currentEdge.src(), assignStmt, similarMethod, Kind.VIRTUAL);
                            addedEdges.add(newEdge);
                            // update the outEdges
                            outEdges.addAll(updateOutEdges(similarMethod, COUNTERVALUE));
                        }
                        // (2) Is it a Runnable class?

                        getRunnables(outEdges, currentEdge, calleeBody, callerClass);
                        // Find the use-site of @rhsop and see if there is any of the entry-method calls there,
                        // then add the calls to the functions
                        // get use-site of @lhsop

                        ExceptionalUnitGraph graph = new ExceptionalUnitGraph(currentEdge.src().getActiveBody());
                        SimpleLocalDefs localDefs = new SimpleLocalDefs(graph);
                        SimpleLocalUses localUses = new SimpleLocalUses(graph, localDefs);

                        // (2) Is it an entry-point to a function call that will implicitly calls a callback at runtime? Let's add the edges to it!
                        ArrayList<String> functionEntryMethods = callbackEntries.get(rhsop.getType().toString());

                        //  (1-3) Get the target callback to be called after callee
                        String calleeSignature = null;
                        String entryCallback = null;


                        List<UnitValueBoxPair> pairs = localUses.getUsesOf(currentEdge.srcUnit());
                        Unit targetUnit = null;
                        for (UnitValueBoxPair pair : pairs) {
                            if (pair.getUnit() instanceof InvokeStmt) {
                                if (((InvokeStmt) pair.getUnit()).getInvokeExpr() instanceof InstanceInvokeExpr) {
                                    InvokeExpr invokeExpr = ((InvokeStmt) pair.getUnit()).getInvokeExpr();
                                    calleeSignature = invokeExpr.getMethod().getSubSignature();
                                    entryCallback = callbackCallSiteInfo.get(invokeExpr.getMethod().getSubSignature());
                                    if (entryCallback != null)
                                        targetUnit = pair.getUnit();
                                }
                            }
                        }

                        //  (1-4) Check if the callee is one of any of @functionEntryMethods
                        if (functionEntryMethods != null && calleeSignature != null && entryCallback != null) {

                            boolean isCalled = functionEntryMethods.contains(calleeSignature);
                            if (isCalled) {

                                // (1)  create a new edge consisting of a
                                //     virtual call to the @entryCallback
                                //   Hence, find the corresponding method in callee class type of the entryCallback
                                SootMethod targetCallback = null;

                                // (2) check if the entry callback is declared within the target class:
                                // yes, continue updating the call-graph
                                // o.w, set the first declared callback after it as the entry-point
                                // System.out.println("entry callback " + entryCallback.toString());

                                if (callerClass.declaresMethod(entryCallback)) {
                                    targetCallback = callerClass.getMethod(entryCallback);
                                } else {
                                    for (CallbackSummary callback : callbackLifeCycle.get(calleeSignature).getCallbackLifeCycle()) {
                                        int indexOfP = callback.getSignature().indexOf("(");
                                        int indexPfSpace = callback.getSignature().indexOf(" ");

                                        String callbackName = callback.getSignature().substring(indexPfSpace + 1, indexOfP);

                                        boolean isSuperMethod = callerClass.getSuperclass().declaresMethod(callback.getSignature());
                                        boolean superMethod = callerClass.getSuperclass().declaresMethod(callback.getSignature());

                                        if (callerClass.declaresMethod(callback.getSignature())) {
                                            targetCallback = callerClass.getMethod(callback.getSignature());
                                            break;
                                        }
                                    }
                                }

                                if (targetCallback != null) {
                                    //     Check if the method has active body and is within the methods retrieved in the app package target
                                    if (allMethods.contains(targetCallback)) {

                                        Local baseObject;

                                        if ((targetUnit instanceof InvokeStmt)) {
                                            baseObject = (Local) lhsop;

                                            // Get Arguments of callee and target Callback and the parametertypes
                                            List<Value> calleeArgs = ((InstanceInvokeExpr) ((InvokeStmt) targetUnit).getInvokeExpr()).getArgs();
                                            List<Type> calleeParamType = ((InstanceInvokeExpr) ((InvokeStmt) targetUnit).getInvokeExpr()).getMethod().getParameterTypes();

                                            List<Value> targetArgs = targetCallback.getActiveBody().getParameterRefs();
                                            List<Type> targetParamType = targetCallback.getParameterTypes();
                                            boolean isArgPassed = false;

                                            // Get return type of the target callback
                                            Type targetReturnType = targetCallback.getReturnType();

                                            // Create a new callsite for the callback to point the target edge to it to
                                            //    be called after it
                                            Local local = null;
                                            VirtualInvokeExpr virtualInvokeExpr;
                                            Edge newEdge = null;
                                            Unit calleeUnit = targetUnit;
                                            Unit temp = null;

                                            // Check if the targetParamType is among calleeParamType
                                            LocalGenerator lg = Scene.v().createLocalGenerator(currentEdge.src().getActiveBody());

                                            if (!targetParamType.isEmpty()) {

                                                if (calleeParamType.equals(targetParamType)) {

                                                    if (targetReturnType.equals(VoidType.v())) {
                                                        virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), calleeArgs);
                                                        temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                    } else {
                                                        local = lg.generateLocal(targetReturnType);
                                                        virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), calleeArgs);
                                                        temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);

                                                    }
                                                    calleeBody.insertAfter(temp, calleeUnit);
                                                    calleeUnit = temp;
                                                    newEdge = new Edge(currentEdge.src(), calleeUnit, targetCallback, Kind.VIRTUAL);
                                                    addedEdges.add(newEdge);
                                                    // ( - ) we can remove the edge here!
                                                    isArgPassed = true;
                                                } else {
                                                    int counter = 0;
                                                    // Is there any parameter of the callee within the targetCallback ?

                                                    for (Type t : targetParamType) {
                                                        if (calleeParamType.contains(t))
                                                            counter++;
                                                    }
                                                    // Let's remove the ones unnecessary and link the one necessary later on
                                                    if (counter == targetParamType.size()) {
                                                        List<Value> passingValues = new ArrayList<>();
                                                        for (Type t : targetParamType) {
                                                            if (calleeParamType.contains(t)) {
                                                                int index = calleeParamType.indexOf(t);
                                                                passingValues.add(calleeArgs.get(index));
                                                            }
                                                        }

                                                        if (targetReturnType.equals(VoidType.v())) {
                                                            virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), passingValues);
                                                            temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                        } else {
                                                            local = lg.generateLocal(targetReturnType);
                                                            virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), passingValues);
                                                            temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                        }

                                                        calleeBody.insertAfter(temp, calleeUnit);
                                                        calleeUnit = temp;
                                                        newEdge = new Edge(currentEdge.src(), temp, targetCallback, Kind.VIRTUAL);
                                                        addedEdges.add(newEdge);
                                                        isArgPassed = true;
                                                    }
                                                }
                                            } else {
                                                local = lg.generateLocal(targetReturnType);
                                                virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, targetCallback.makeRef(), targetArgs);
                                                temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                            }


                                            // System.out.println(" First callback added is: " + temp.toString());
                                            // (3)  add other callbacks in the lifecycle
                                            for (CallbackSummary callback : callbackLifeCycle.get(calleeSignature).getCallbackLifeCycle()) {

                                                if (!callback.getSignature().equals(targetCallback.getSubSignature()) && callerClass.declaresMethod(callback.getSignature())) {
                                                    SootMethod callbackMethod;

                                                    callbackMethod = callerClass.getMethod(callback.getSignature());

                                                    if (callbackMethod.hasActiveBody()) {

                                                        targetArgs = callbackMethod.getActiveBody().getParameterRefs();
                                                        targetParamType = callbackMethod.getParameterTypes();
                                                        targetReturnType = callbackMethod.getReturnType();

                                                        if (isArgPassed) {
                                                            //  System.out.println("LOG: arg passed");
                                                            if (local != null) {
                                                                if (("[" + local.getType() + "]").equals(targetParamType.toString())) {

                                                                    // Pass value by reference to the callback from previous calls
                                                                    virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), local);
                                                                    if (targetReturnType.equals(VoidType.v())) {
                                                                        temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                    } else {
                                                                        local = lg.generateLocal(targetReturnType);
                                                                        temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                    }
                                                                } else {

                                                                    if (!targetParamType.isEmpty()) {
                                                                        List<Value> args = new ArrayList<>();
                                                                        for (Type t : targetParamType) {
                                                                            if (targetArgs.contains(t))
                                                                                args.add(targetArgs.get(targetArgs.indexOf(t)));
                                                                            else {
                                                                                Local newVar = lg.generateLocal(t);
                                                                                args.add(newVar);
                                                                            }
                                                                        }
                                                                        virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), args);
                                                                        if (targetReturnType.equals(VoidType.v())) {
                                                                            temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                        } else {
                                                                            local = lg.generateLocal(targetReturnType);
                                                                            temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            calleeBody.insertAfter(temp, calleeUnit);
                                                            calleeUnit = temp;
                                                            newEdge = new Edge(currentEdge.src(), temp, callbackMethod, Kind.VIRTUAL);
                                                            addedEdges.add(newEdge);

                                                        }

                                                    } else {

                                                        if (calleeParamType.equals(targetParamType)) {
                                                            virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), calleeArgs);
                                                            if (targetReturnType.equals(VoidType.v())) {
                                                                temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                            } else {
                                                                local = lg.generateLocal(targetReturnType);
                                                                temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);

                                                            }
                                                            isArgPassed = true;
                                                        } else {
                                                            if (local != null) {

                                                                if (local.getType().equals(targetParamType) && !local.getType().equals(VoidType.v())) {
                                                                    // Pass value by reference to the callback from previous calls
                                                                    virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), local);
                                                                    if (targetReturnType.equals(VoidType.v())) {
                                                                        temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                    } else {
                                                                        local = lg.generateLocal(targetReturnType);
                                                                        temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                    }
                                                                } else {
                                                                    int counter = 0;
                                                                    // Is there any parameter of the callee within the targetCallback ?

                                                                    for (Type t : targetParamType) {
                                                                        if (calleeParamType.contains(t))
                                                                            counter++;
                                                                    }
                                                                    // Let's remove the ones unnecessary and link the one necessary later on
                                                                    if (counter > 0 && counter == targetParamType.size()) {
                                                                        List<Value> passingValues = new ArrayList<>();
                                                                        for (Type t : targetParamType) {
                                                                            if (calleeParamType.contains(t)) {
                                                                                int index = calleeParamType.indexOf(t);
                                                                                passingValues.add(calleeArgs.get(index));
                                                                            }
                                                                        }

                                                                        virtualInvokeExpr = Jimple.v().newVirtualInvokeExpr(baseObject, callbackMethod.makeRef(), passingValues);
                                                                        if (targetReturnType.equals(VoidType.v())) {
                                                                            temp = Jimple.v().newInvokeStmt(virtualInvokeExpr);
                                                                        } else {
                                                                            local = lg.generateLocal(targetReturnType);
                                                                            temp = Jimple.v().newAssignStmt(local, virtualInvokeExpr);
                                                                        }

                                                                        isArgPassed = true;
                                                                    }
                                                                }
                                                            }

                                                        }
                                                        calleeBody.insertAfter(temp, calleeUnit);
                                                        calleeUnit = temp;
                                                        newEdge = new Edge(currentEdge.src(), temp, callbackMethod, Kind.VIRTUAL);
                                                        addedEdges.add(newEdge);
                                                        //System.out.println("Callback added is " + temp.toString());
                                                    }

                                                }
                                            }
                                            // (4)  add the final edges to the ending edge ( the last callback in the lifecycle)
                                            outEdges.addAll(updateOutEdges(callee.method(), COUNTERVALUE));
                                        }
                                    }
                                }
                            }

                        }


                    }

                    getComponentCommunication(targetPackageName, currentEdge, callee);

                    addNotResolvedListeners(callee, caller, currentEdge.srcStmt().getInvokeExpr().getArgs(), currentEdge.srcUnit());
                }
            }
        }


        // This should be iterative
        while (!outEdges.isEmpty()) {
            int counter = COUNTERVALUE;
            Edge edge = outEdges.get(0);
            System.out.println(" - function:  " + edge.getTgt().method().getSignature());
            Scene.v().getCallGraph().addEdge(edge);
            SootMethod targetMethod = edge.getTgt().method();

            if (Scene.v().getReachableMethods().contains(edge.getTgt().method()))
                System.out.println(" -- reachable: true");
            else {
                System.out.println(" -- reachable: false");
                // Get edges of this funciton until layer 10 ! Add them to the call Graph !
                if (targetMethod.hasActiveBody() && targetMethod.getDeclaringClass().getPackageName().startsWith(targetPackageName) && !targetMethod.getSignature().contains("$jacocoInit")) {
                    // Let's add the edges out of it ourself up to level CG_DEPTH
                    ArrayList<Edge> addedOutEdges = new ArrayList<>();
                    addedOutEdges = updateOutEdges(targetMethod, counter);
                    for (Edge edge1 : addedOutEdges) {
                        // Is it an Intent Call?
                        if (edge1.getTgt().method().getSignature().startsWith("<android.content.Intent: void <init>(android.content.Context,java.lang.Class)>")) {

                            // (1) Get arguments: extract the target class name
                            List<Value> args = ((Stmt) edge1.srcUnit()).getInvokeExpr().getArgs();
                            String className = "";

                            if (args.size() == 2) {
                                // ( - ) It is better to check if the type of the current arg is any of Activity, Service or BroadCast reciever
                                className = getClassName(args.get(1).toString());
                                className = className.replace('/', '.');
                            }
                            // (2) Is there any class representing @class_name ? What is its type ?
                            if (!className.isEmpty() && className.startsWith(targetPackage)) {
                                Edge newEdge = handleIntentCall(className, edge1.src(), edge1.srcUnit());
                                if (newEdge != null)
                                    outEdges.add(newEdge);
                            }
                        }
                        // Is it a runnable?
                        if (edge1.getTgt().method() instanceof InstanceInvokeExpr) {
                            Edge temp = checkRunnable(targetMethod.getActiveBody().getUnits(), targetMethod.getDeclaringClass(), edge1.srcUnit(), targetMethod, edge1.tgt().method(), (Local) ((InstanceInvokeExpr) edge1.srcStmt().getInvokeExpr()).getBase());
                            if (temp != null) {
                                outEdges.add(temp);
                            }
                        }
                    }
                    outEdges.addAll(addedOutEdges);
                    // Is there any call to Intent ? Is there any Listener ? Is there any dummy method for fragment or components missing? Add them all here
                }
            }

            System.out.println(" --------------------------- Done for this function --------------------");
            outEdges.remove(0);
        }
    }

    public void getRunnables(List<Edge> outEdges, Edge currentEdge, Chain<Unit> calleeBody, SootClass callerClass) {
        if (currentEdge.srcStmt().getInvokeExpr() instanceof InstanceInvokeExpr) {
            Edge temp = checkRunnable(calleeBody, callerClass, currentEdge.srcUnit(), currentEdge.src(), currentEdge.tgt(), (Local) ((InstanceInvokeExpr) currentEdge.srcStmt().getInvokeExpr()).getBase());
            if (temp != null) {
                addedEdges.add(temp);
                outEdges.addAll(updateOutEdges(temp.tgt(), COUNTERVALUE));
            }
        }
    }

    private void addNotResolvedListeners(SootMethod callee, SootMethod caller, List<Value> args, Unit unit) {
        boolean sigListener = false, parListener = false, isInterface = false;
        // index of parameter of listener
        int index = 0;
        // if the type of the callee's class is any form of 1) @callbackType interfaces
        Chain<SootClass> interfaces = callee.getDeclaringClass().getInterfaces();
        for (SootClass temp : interfaces) {
            if (callbackType.contains(temp.getName()) &&
                    temp.getMethodUnsafe(callee.getSubSignature()) != null)
                isInterface = true;
        }
        // 2) contains set and listener in the method signature
        if (callee.getSubSignature().startsWith("set") && callee.getSubSignature().endsWith("Listener")) {
            sigListener = true;
        }
        // 3) one of its parameters is a listener type
        for (Type parameter : callee.getParameterTypes()) {
            if (!parListener)
                index++;
            if (callbackType.contains(parameter.toString())) {
                parListener = true;
            }
        }
        if (callee.getSignature().contains("void <init>") &&
                callee instanceof InvokeStmt) {
            SootClass declaringClass = callee.getDeclaringClass();

            // (1) Does its declaring class have interfaces?
            SootClass superClassName = icfg.getSuperClassClass(declaringClass);
            ArrayList<SootMethod> methods = new ArrayList<>();
            if (superClassName != null) {
                if (superClassName.getInterfaces().size() > 0) {
                    for (SootClass temp : superClassName.getInterfaces()) {
                        for (SootMethod sootMethod : declaringClass.getMethods()) {
                            SootMethod interfaceMethod = temp.getMethodUnsafe(sootMethod.getSubSignature());
                            if (callbackType.contains(temp.getName()) &&
                                    interfaceMethod != null) {
                                isInterface = true;
                                if (callbackLists.contains(interfaceMethod.getSignature()))
                                    methods.add(sootMethod);
                            }
                        }
                    }
                }
            }
        }
    }

    public void getComponentCommunication(String targetPackageName, Edge currentEdge, SootMethod callee) {
        if (callee.getSignature().startsWith("<android.content.Intent: void <init>(android.content.Context,java.lang.Class)>")) {

            // (1) Get arguments: extract the target class name
            List<Value> args = currentEdge.srcStmt().getInvokeExpr().getArgs();
            String className = "";

            if (args.size() == 2) {
                // ( - ) It is better to check if the type of the current arg is any of Activity, Service or BroadCast reciever
                className = getClassName(args.get(1).toString());
                className = className.replace('/', '.');
            }

            // (2) Is there any class representing @class_name ? What is its type ?
            if (!className.isEmpty() && className.startsWith(targetPackageName)) {
                Edge newEdge = handleIntentCall(className, currentEdge.src(), currentEdge.srcUnit());
                if (newEdge != null)
                    addedEdges.add(newEdge);
            }
        }
    }

    private Edge handleIntentCall(String className, SootMethod srcMethod, Unit srcUnit) {
        String dummyMainMethodSignature = "<dummyMainClass: " + className + " dummyMainMethod_" + className.replace('.', '_') + "(android.content.Intent)>";
        SootClass dummyMainClass = Scene.v().getSootClassUnsafe("dummyMainClass");
        // (3) Is it activity? service ? broadcastreceiver? fragment ?
        // ( - ) Not completed yet
        // (4) Do we have the class representing the dummy main method of this class ?
        if (dummyMainClass != null) {
            SootMethod dummyMainMethod = Scene.v().getMethod(dummyMainMethodSignature);
            if (dummyMainMethod != null) {
                // (5) If yes, let's add a statement after it in the caller method
                //      staticinvoke dummy_main_method(null)
                if (srcMethod.hasActiveBody()) {
                    Unit calleeUnit = srcUnit;
                    StaticInvokeExpr dummyCall = Jimple.v().newStaticInvokeExpr(dummyMainMethod.makeRef(), NullConstant.v());
                    Unit dummyUnit = Jimple.v().newInvokeStmt(dummyCall);
                    Chain<Unit> srcBody = srcMethod.getActiveBody().getUnits();
                    srcBody.insertAfter(dummyUnit, calleeUnit);

                    // (6) add the edge to edges needed to be added to the @CallGraph
                    Edge newEdge = new Edge(srcMethod, dummyUnit, dummyMainMethod, Kind.STATIC);
                    return newEdge;
                }
            }
        } else {
            // create the dummyMainMethod for this component !
        }
        return null;
    }

    private Edge checkRunnable(Chain<Unit> calleeBody, SootClass callerClass, Unit srcUnit, SootMethod callerMethod, SootMethod calleeMethod, Local base) {
        Chain<SootClass> interfaces = callerClass.getInterfaces();
        for (SootClass sootClass : interfaces) {
            if (sootClass.getName().equals("java.lang.Runnable")) {
                SootMethod runMethod = calleeMethod.getDeclaringClass().getMethodUnsafe("void run()");
                if (runMethod != null && calleeMethod.getName().equals("<init>")) {
                    if (base.getType().equals(callerClass.getType())) {
                        InvokeExpr invokeExpr = Jimple.v().newVirtualInvokeExpr(base, runMethod.makeRef());
                        InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(invokeExpr);
                        Edge newEdge = new Edge(callerMethod, invokeStmt, runMethod, Kind.VIRTUAL);
                        calleeBody.insertAfter(invokeStmt, srcUnit);
                        return newEdge;
                    }
                }
            }
        }
        return null;
    }

    private InvokeExpr getInvokeExpr(Value value, SootMethod similarMethod) {
        if (value instanceof InstanceInvokeExpr) {
            if (value instanceof SpecialInvokeExpr) {
                return Jimple.v().newSpecialInvokeExpr((Local) ((SpecialInvokeExpr) value).getBase(), similarMethod.makeRef(), ((SpecialInvokeExpr) value).getArgs());
            } else if (value instanceof VirtualInvokeExpr) {
                return Jimple.v().newVirtualInvokeExpr((Local) ((VirtualInvokeExpr) value).getBase(), similarMethod.makeRef(), ((VirtualInvokeExpr) value).getArgs());
            } else if (value instanceof InterfaceInvokeExpr) {
                return Jimple.v().newInterfaceInvokeExpr((Local) ((InterfaceInvokeExpr) value).getBase(), similarMethod.makeRef(), ((InterfaceInvokeExpr) value).getArgs());
            }
        } else if (value instanceof StaticInvokeExpr) {
            return Jimple.v().newStaticInvokeExpr(similarMethod.makeRef(), ((StaticInvokeExpr) value).getArgs());
        } else if (value instanceof DynamicInvokeExpr) {
            return Jimple.v().newDynamicInvokeExpr(((DynamicInvokeExpr) value).getBootstrapMethodRef(), ((DynamicInvokeExpr) value).getBootstrapArgs(), similarMethod.makeRef(), ((DynamicInvokeExpr) value).getArgs());
        }
        System.out.println("Error: invoke expr type is not checked above!!1");
        return null;
    }

    private List<SootMethod> getParentDeclaredMethod(SootMethod sootMethod, SootClass sootClass) {
        List<SootMethod> similarMethods = new ArrayList<>();

        if (sootClass.getPackageName().startsWith(targetPackage) && !sootMethod.isDeclared()) {

            boolean hasSuperClass = sootClass.hasSuperclass();
            while (hasSuperClass && !sootClass.getSuperclass().getName().startsWith("java.lang.Object")) {
                SootMethod similarmethod = sootClass.getMethodUnsafe(sootMethod.getName(), sootMethod.getParameterTypes(), sootMethod.getReturnType());
                if (similarmethod != null) {
                    similarMethods.add(similarmethod);
                }
                sootClass = sootClass.getSuperclass();
                if (sootClass.getName().startsWith("java.lang.Object"))
                    hasSuperClass = false;
            }
        }
        return similarMethods;
    }

    private String getClassName(String toString) {
        String name = "";
        if (toString.contains("class")) {
            int first = toString.indexOf(" ");
            int last = toString.indexOf(";");
            if (first < last && last < toString.length()) name = toString.substring(first + 3, last);
        }
        return name;
    }


    /**
     * @param sootMethod : the target package
     * @return
     */
    public ArrayList<Edge> updateOutEdges(SootMethod sootMethod, int counter) {
        ArrayList<Edge> outEdges = new ArrayList<>();
        counter--;

        // Does this method exists within the app-level unreachable methods of the callgraph?
        if (sootMethod.hasActiveBody() &&
                sootMethod.getDeclaringClass().getPackageName().startsWith(targetPackage) &&
                !Scene.v().getReachableMethods().contains(sootMethod)) {

            if (counter > 0) {

                Chain<Unit> body = sootMethod.getActiveBody().getUnits();

                for (Unit unit : body) {
                    InvokeExpr invokeExpr = null;

                    if (unit instanceof InvokeStmt) {
                        invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
                    } else if (unit instanceof AssignStmt) {
                        AssignStmt assignStmt = (AssignStmt) unit;
                        if (assignStmt.getRightOp() instanceof InvokeExpr)
                            invokeExpr = (InvokeExpr) assignStmt.getRightOp();
                    }
                    if (invokeExpr != null && // there is a method invocation
                            !invokeExpr.getMethod().getSignature().equals(sootMethod.getSignature()) && // Is it a call to itslef?
                            hasEdge(unit, sootMethod) && //Scene.v().getCallGraph().edgesOutOf(unit) == null && // Is it previously analysed?
                            invokeExpr.getMethod().getDeclaringClass().getPackageName().startsWith(targetPackage) && // Is it an app-level method?
                            !invokeExpr.getMethod().getSignature().contains("$jacocoInit")) {
                        System.out.println(" --- [" + counter + "] Update outEdges of " + sootMethod.getSignature());
                        invokeExpr.apply(new AbstractExprSwitch() {
                            @Override
                            public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
                                Edge outEdge = new Edge(sootMethod, unit, v.getMethod(), Kind.SPECIAL);
                                System.out.println("[special invoke] " + v.getMethod().getSignature());
                                outEdges.add(outEdge);
                            }

                            @Override
                            public void caseStaticInvokeExpr(StaticInvokeExpr v) {
                                Edge outEdge = new Edge(sootMethod, unit, v.getMethod(), Kind.STATIC);
                                System.out.println("[static invoke] " + v.getMethod().getSignature());
                                outEdges.add(outEdge);
                            }

                            @Override
                            public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
                                Edge outEdge = new Edge(sootMethod, unit, v.getMethod(), Kind.VIRTUAL);
                                System.out.println("[virt invoke] " + v.getMethod().getSignature());
                                outEdges.add(outEdge);
                            }

                            @Override
                            public void defaultCase(Object obj) {
                                super.defaultCase(obj);
                            }
                        });


                        if (Scene.v().getReachableMethods().contains(invokeExpr.getMethod())) {
                            System.out.println("[reachable] " + invokeExpr.getMethod().toString());
                        } else {
                            System.out.println("[unreachable] " + invokeExpr.getMethod().toString());
                            outEdges.addAll(updateOutEdges(invokeExpr.getMethod(), counter));
                        }
                    }
                }
            } else {
                System.out.println(" --- is it reachable ?" + Scene.v().getReachableMethods().contains(sootMethod));
            }
        }
        return outEdges;
    }

    private boolean hasEdge(Unit unit, SootMethod sootMethod) {
        for (Edge edge : Scene.v().getCallGraph()) {
            if (edge.getSrc().method().getSignature().equals(sootMethod.getSignature()) &&
                    edge.srcUnit().toString().equals(unit.toString()))
                return true;
        }
        return false;
    }

    /**
     * In this function, we
     * ( 1 ) add the callsites within a recently added callbacks to the callgraph and making the callgraph precise
     *
     * @param callGraph : callgraph retrieved by flowDroid
     */
    public void refineCallGraph(CallGraph callGraph) {
        List<Edge> outEdges = new ArrayList<>();
        for (SootMethod sootMethod : allMethods) {
            if (callGraph.edgesInto(sootMethod).next().kind().equals(Kind.INVALID) && sootMethod.getDeclaringClass().getName().startsWith(targetPackage)) {
                outEdges.addAll(updateOutEdges(sootMethod, COUNTERVALUE));
            }
        }
        for (Edge edge : outEdges)
            Scene.v().getCallGraph().addEdge(edge);
    }


}
