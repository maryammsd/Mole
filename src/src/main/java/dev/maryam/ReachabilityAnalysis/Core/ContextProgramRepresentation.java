package dev.maryam.ReachabilityAnalysis.Core;

import soot.*;
import soot.jimple.IdentityStmt;
import soot.jimple.ThisRef;
import soot.jimple.toolkits.callgraph.ContextSensitiveEdge;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

import java.util.*;

/**
 * This class provides the inter-procedural control flow graph
 * for the main analysis.
 **/
public class ContextProgramRepresentation<M, N> implements vasco.ProgramRepresentation<MethodOrMethodContext, Unit> {

    /**
     * The cache required  for control flow graph.
     */
    private Map<SootMethod, DirectedGraph<Unit>> cfgCache;

    /**
     * The delegated ICFG.
     */
    protected final BiDiInterproceduralCFG<Unit, SootMethod> delegateICFG;

    public BiDiInterproceduralCFG<Unit, SootMethod> getDelegateICFG() {
        return delegateICFG;
    }


    /**
     * Instantiates a new inter-procedural CFG.
     */
    public ContextProgramRepresentation() {
        cfgCache = new HashMap<SootMethod, DirectedGraph<Unit>>();
        delegateICFG = new JimpleBasedInterproceduralCFG(true);
    }

    /**
     * Returns a list containing the entry points.
     *
     * @return the entry points
     */
    @Override
    public List<MethodOrMethodContext> getEntryPoints() {
        List<MethodOrMethodContext> entryPoints = new ArrayList<>();
        for (MethodOrMethodContext method : Scene.v().getEntryPoints()) {
            entryPoints.add(method);
        }
        return entryPoints;
    }

    /**
     * Returns the CFG for a given method.
     *
     * @param method the target method
     * @return the control flow graph
     */
    @Override
    public DirectedGraph<Unit> getControlFlowGraph(MethodOrMethodContext method) {

        if (!cfgCache.containsKey(method.method())) {
            cfgCache.put(method.method(), new ExceptionalUnitGraph(method.method().getActiveBody()));
        }
        return cfgCache.get(method.method());
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
     * Returns the state that if the method is a phantom method or doesn't have an active body inside!
     *
     * @param sootMethod : target method
     * @return true or false
     */
    @Override
    public boolean isPhantomMethod(MethodOrMethodContext sootMethod) {
        return sootMethod.method().isPhantom() || !sootMethod.method().hasActiveBody();
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
    public List<MethodOrMethodContext> resolveTargets(MethodOrMethodContext method, Unit node) {

        List<MethodOrMethodContext> targets = new LinkedList<MethodOrMethodContext>();
        @SuppressWarnings("rawtypes")
        Iterator it = Scene.v().getContextSensitiveCallGraph().edgesOutOf(method.context(), method.method(), node);
        while (it.hasNext()) {
            ContextSensitiveEdge edge = (ContextSensitiveEdge) it.next();
            if (edge.kind().isExplicit()) {
                targets.add(MethodContext.v(edge.tgt(), edge.tgtCtxt()));
            }
        }
        return targets;
    }

    /**
     * Resolves virtual calls using the default call graph and returns a list of methods which are the
     * targets of implicit edges. TODO: Should we consider thread/clinit edges?
     *
     * @param method the method
     * @param node   the node
     * @return the list
     */

    public List<SootMethod> addImplicitEdges(SootMethod method, Unit node) {
        List<SootMethod> targets = new LinkedList<SootMethod>();
        Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(node);
        while (it.hasNext()) {
            Edge edge = it.next();
            if (edge.isExplicit()) {
                targets.add(edge.tgt());
            }
        }
        return targets;
    }



    public boolean isAnalyzable(MethodOrMethodContext method) {
        return !method.method().isPhantom() && method.method().hasActiveBody();
    }


    /**
     * Return the first identity statement assigning from \@this.
     *
     * @param method the method
     * @return the first identity statement assigning from \@this
     */
    public IdentityStmt getIdentityStmt(SootMethod method) {
        for (Unit s : method.getActiveBody().getUnits()) {
            if (s instanceof IdentityStmt && ((IdentityStmt) s).getRightOp() instanceof ThisRef) {
                return (IdentityStmt) s;
            }
        }
        throw new RuntimeException("couldn't find identityref!" + " in " + method);
    }
}
