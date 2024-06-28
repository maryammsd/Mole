package dev.maryam.ReachabilityAnalysis.Core;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.entryPointCreators.AndroidEntryPointConstants;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.util.Chain;

import java.util.*;

/**
 * This class provides the inter-procedural control flow graph
 * for the main analysis.
 **/
public class InterProceduralCFGRepresentation implements ProgramRepresentation<SootMethod, Unit> {

    /**
     * The cache required  for control flow graph.
     */
    private Map<SootMethod, DirectedGraph<Unit>> cfgCache;

    /**
     * The delegated ICFG.
     */
    protected final BiDiInterproceduralCFG<Unit, SootMethod> delegateICFG;

    private String targetPackageName;

    private ArrayList<String> callbackLists, callbackType;

    public BiDiInterproceduralCFG<Unit, SootMethod> getDelegateICFG() {
        return delegateICFG;
    }


    /**
     * Instantiates a new inter-procedural CFG.
     */
    public InterProceduralCFGRepresentation(ArrayList<String> callbackLists, ArrayList<String> callbackType, String targetPackageName) {
        cfgCache = new HashMap<>();
        delegateICFG = new JimpleBasedInterproceduralCFG(true);
        this.callbackLists = callbackLists;
        this.callbackType = callbackType;
        this.targetPackageName = targetPackageName;

    }

    /**
     * Returns a list containing the entry points.
     *
     * @return the entry points
     */
    @Override
    public List<SootMethod> getEntryPoints() {
        return Scene.v().getEntryPoints();
    }

    /**
     * Returns the CFG for a given method.
     *
     * @param method the target method
     * @return the control flow graph
     */
    @Override
    public DirectedGraph<Unit> getControlFlowGraph(SootMethod method) {

        if (method.hasActiveBody()) {
            if (!cfgCache.containsKey(method)) {
                cfgCache.put(method, delegateICFG.getOrCreateUnitGraph(method));
            }
            return cfgCache.get(method);
        } else {
            return null;
        }
    }

    /**
     * Returns <code>true</code> iff the jimple statement contains an invoke expression.
     *
     * @param node the given statement
     * @return true, if the given statement contains invoke expression
     */
    @Override
    public boolean isCall(Unit node) {
        return ((soot.jimple.Stmt) node).containsInvokeExpr();
    }

    /**
     * Resolves virtual calls using the default call graph and returns a list of methods which are the
     * targets of explicit edges. TODO: Should we consider thread/clinit edges?
     *
     * @param method the method
     * @param node   the node
     * @return the list
     */
    @Override
    public List<SootMethod> resolveTargets(SootMethod method, Unit node) {
        List<SootMethod> targets = new LinkedList<>();
        Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(node);
        boolean hasEdge = false;

        while (it.hasNext()) {
            Edge edge = it.next();

            if (edge.isExplicit() || edge.isClinit()) {
                targets.add(edge.tgt());
            } else if (edge.kind() == Kind.EXECUTOR || edge.kind() == Kind.THREAD || edge.kind() == Kind.HANDLER || edge.kind() == Kind.ASYNCTASK) {
                targets.add(edge.tgt());
            }
            hasEdge = true;
        }

        if (!hasEdge) {
            // get function signature invoked at this node
            InvokeExpr ie = ((Stmt) node).getInvokeExpr();
            SootMethod sootMethod = ie.getMethod();

            for (int i = 0; i < ((Stmt) node).getInvokeExpr().getArgCount(); i++) {
                Value argValue = ((Stmt) node).getInvokeExpr().getArgBox(i).getValue();
                Type argType = argValue.getType();
                String parameterType = sootMethod.getSignature().substring(sootMethod.getSignature().indexOf("(") + 1, sootMethod.getSignature().indexOf(")"));
                if (callbackType.contains(parameterType)) {
                    SootClass sootClass = Scene.v().getSootClassUnsafe(argType.toString());
                    if (sootClass != null) {
                        for (SootMethod callbackMethod : sootClass.getMethods()) {
                            if (callbackLists.contains(parameterType + ": " + callbackMethod.getSubSignature())) {
                                if (callbackMethod.isDeclared() && callbackMethod.isConcrete()) {
                                    Edge newEdge = new Edge(method, node, callbackMethod, Kind.VIRTUAL);
                                    targets.add(callbackMethod);
                                    Scene.v().getCallGraph().addEdge(newEdge);
                                }
                            }
                        }
                    }
                }
            }
            SootClass declaredClass = sootMethod.getDeclaringClass();
            Edge newEdge = null;
            if (declaredClass.declaresMethod(sootMethod.getName(), sootMethod.getParameterTypes())) {
                newEdge = new Edge(method, node, sootMethod, Kind.VIRTUAL);
                targets.add(sootMethod);
                Scene.v().getCallGraph().addEdge(newEdge);

            } else {
                // check if the declared class of target method has superclass
                if (declaredClass.hasSuperclass()) {
                    // Get the super class
                    SootClass superClass = declaredClass.getSuperclass();
                    // While the superclass is not null or java.lang.Object
                    while (superClass != null && !superClass.getName().equals("java.lang.Object")) {

                        // check if the method is declared within the superClass
                        if (superClass.declaresMethod(sootMethod.getSignature())) {
                            sootMethod = superClass.getMethod(sootMethod.getSignature());
                            newEdge = new Edge(method, node, sootMethod, Kind.VIRTUAL);
                            // add an edge from current node to the method
                            System.out.println("Method " + method + " is added to " + node.toString());
                            targets.add(sootMethod);
                            Scene.v().getCallGraph().addEdge(newEdge);
                            break;
                        } else {
                            if (superClass.hasSuperclass())
                                superClass = superClass.getSuperclass();
                        }
                    }

                }
            }
        }
        return targets;
    }

    @Override
    public boolean isSkipCall(Unit node) {
        return false;
    }


    public boolean isAnalyzable(SootMethod method) {
        return !method.isPhantom() && method.hasActiveBody();
    }


    // remove it
    public boolean isUICallback(SootClass sootSuperClass, SootMethod sootMethod) {

        //
        String callback = sootSuperClass.getName() + ": " + sootMethod.getSubSignature();

        if (callbackLists.size() > 0) {
            if (callbackLists.contains(callback)) {
                return true;
            }
        }

        return false;

    }


    public String getSuperClass(SootClass sootClass) {
        boolean isAndroidClass = false;
        SootClass targetClass = null;

        while (!isAndroidClass) {
           // System.out.println("super class" + sootClass.getName());
            if (sootClass.hasSuperclass()) {
                targetClass = sootClass.getSuperclass();
                sootClass = targetClass;
            }else
                return sootClass.getName();
            if (targetClass != null) {
                if (targetClass.getPackageName().startsWith("android") || (targetClass.getPackageName().startsWith("com.google") && !targetClass.getPackageName().startsWith("com.google.samples.apps")))
                    isAndroidClass = true;
                else if (targetClass.getPackageName().startsWith("java"))
                    return targetClass.getPackageName();

                // this class is a preferenceActivity that inherits ListActivity and Activity and since we do the checking on android
                // it is not found as a component
                if(targetClass.getName().contains("android.preference.PreferenceActivity"))
                    targetClass = targetClass.getSuperclass().getSuperclass();

            } else
                return "java.lang.Object";
        }
        if (targetClass != null)
            return targetClass.getName();
        else
            return "no class";
    }

    public SootClass getSuperClassClass(SootClass sootClass) {
        boolean isFinished = false;
        SootClass targetClass = null;

        while (!isFinished) {
            if (sootClass.hasSuperclass()) {
                targetClass = sootClass.getSuperclass();
                sootClass = targetClass;
            }
            if (targetClass != null) {
                if (targetClass.getPackageName().startsWith("android") || (targetClass.getPackageName().startsWith("com.google") && !targetClass.getPackageName().startsWith("com.google.samples.apps") ) || targetClass.getPackageName().startsWith("java")) {
                    isFinished = true;
                    // this class is a preferenceActivity that inherits ListActivity and Activity and since we do the checking on android
                    // it is not found as a component

                    if(targetClass.getName().contains("android.preference.PreferenceActivity"))
                        return targetClass.getSuperclass().getSuperclass();
                    return targetClass;
                }
            } else
                return null;
        }
        return null;
    }

    public boolean isComponent(String sootClassName) {

        return sootClassName.startsWith(AndroidEntryPointConstants.ACTIVITYCLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.MAPACTIVITYCLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.APPCOMPATACTIVITYCLASS_V4) ||
                sootClassName.startsWith(AndroidEntryPointConstants.APPCOMPATACTIVITYCLASS_V7) ||
                sootClassName.startsWith(AndroidEntryPointConstants.APPCOMPATACTIVITYCLASS_X) ||
                sootClassName.startsWith(AndroidEntryPointConstants.APPLICATIONCLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.BROADCASTRECEIVERCLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.CONTENTPROVIDERCLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.SERVICECLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.GCMBASEINTENTSERVICECLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.GCMLISTENERSERVICECLASS);

    }

    public boolean isFragment(String sootClassName) {

        return sootClassName.startsWith(AndroidEntryPointConstants.FRAGMENTCLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.ANDROIDXFRAGMENTCLASS) ||
                sootClassName.startsWith(AndroidEntryPointConstants.SUPPORTFRAGMENTCLASS);

    }

    public boolean isFragmentLifeCycle(SootMethod sootMethod) {
        return AndroidEntryPointConstants.getFragmentLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                sootMethod.getSubSignature().contains("void <init>") ||
                sootMethod.getSubSignature().contains("android.view.View getView()");
    }

    public boolean isLifeCycleCallback(SootMethod sootMethod) {
        return AndroidEntryPointConstants.getActivityLifecycleCallbackMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getApplicationLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getBroadcastLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getContentproviderLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getComponentCallbackMethods().contains(sootMethod.getSignature()) ||
                AndroidEntryPointConstants.getFragmentLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getActivityLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getGCMIntentServiceMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getGCMListenerServiceMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getHostApduServiceMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getServiceConnectionMethods().contains(sootMethod.getSubSignature()) ||
                AndroidEntryPointConstants.getServiceLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                sootMethod.getSubSignature().contains("onBackPressed") ||
                sootMethod.getSubSignature().contains("onCreateOptionsMenu") ;
    }

    public boolean isUICallback(SootMethod sootMethod, String superClass) {
        if (callbackLists.size() > 0) {
            if (callbackLists.contains(superClass + ": " + sootMethod.getSubSignature()))
                return true;
        }
        return sootMethod.getSubSignature().contains("onOptionsItemSelected") ||
                sootMethod.getSubSignature().contains("onKey") ||
                sootMethod.getSubSignature().contains("onDraw") ||
                sootMethod.getSubSignature().contains("onMenuItem");
    }

    // remove it !
    public boolean isComponentCallback(SootClass sootSuperClass, SootMethod sootMethod) {


        if (sootSuperClass.getName().startsWith(AndroidEntryPointConstants.ACTIVITYCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.ANDROIDXFRAGMENTCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.APPLICATIONCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.BROADCASTRECEIVERCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.CONTENTPROVIDERCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.SERVICECLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.FRAGMENTCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.SUPPORTFRAGMENTCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.GCMBASEINTENTSERVICECLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.GCMLISTENERSERVICECLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.MAPACTIVITYCLASS) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.APPCOMPATACTIVITYCLASS_V4) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.APPCOMPATACTIVITYCLASS_V7) ||
                sootSuperClass.getName().startsWith(AndroidEntryPointConstants.APPCOMPATACTIVITYCLASS_X)
        ) {
            if (AndroidEntryPointConstants.getActivityLifecycleCallbackMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getApplicationLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getBroadcastLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getContentproviderLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getFragmentLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getActivityLifecycleMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getGCMIntentServiceMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getGCMListenerServiceMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getHostApduServiceMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getServiceConnectionMethods().contains(sootMethod.getSubSignature()) ||
                    AndroidEntryPointConstants.getServiceLifecycleMethods().contains(sootMethod.getSubSignature()))
                return true;

        }
        return false;

    }

    public boolean isExcludedMethod(SootMethod method) {
        if (method.getSignature().contains("<com.mikepenz.materialdrawer.adapter.BaseDrawerAdapter: >"))
            System.out.println(" here is the problem ");

        if (method.getDeclaringClass().getPackageName().startsWith(targetPackageName))
            return false;
        if(method.getDeclaringClass().getPackageName().contains("com.google.samples.apps"))
            return false;
        else return method.getDeclaringClass().getPackageName().startsWith("java") ||
                method.getDeclaringClass().getPackageName().startsWith("android") ||
                method.getDeclaringClass().getPackageName().startsWith("androidx") ||
                method.getDeclaringClass().getPackageName().startsWith("google") ||
                method.getDeclaringClass().getPackageName().startsWith("com.google") ||
                method.getDeclaringClass().getPackageName().startsWith("okhttp") ||
                method.getDeclaringClass().getPackageName().startsWith("rx") ||
                method.getDeclaringClass().getPackageName().startsWith("kotlin") ||
                method.getDeclaringClass().getPackageName().startsWith("dagger") ||
                method.getDeclaringClass().getPackageName().startsWith("org") ||
                method.getDeclaringClass().getPackageName().startsWith("io.reactivex");
     
    }


    public ArrayList<SootMethod> isInterface(SootClass sootClass) {
        ArrayList<SootMethod> overriden = new ArrayList<>();
        if (!sootClass.getInterfaces().isEmpty()) {
            Chain<SootClass> interfaces = sootClass.getInterfaces();
            for (SootClass interfaceclass : interfaces) {
                if (callbackType.contains(interfaceclass.getName())) {
                    for (SootMethod sootMethod : interfaceclass.getMethods()) {
                        SootMethod classMethod = sootClass.getMethodUnsafe(sootMethod.getSubSignature());
                        if (classMethod != null) {
                            overriden.add(sootMethod);
                        }
                    }
                }

            }
        }
        return overriden;
    }
}
