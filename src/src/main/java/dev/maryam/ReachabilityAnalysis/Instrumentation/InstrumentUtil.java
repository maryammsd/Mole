package dev.maryam.ReachabilityAnalysis.Instrumentation;

import soot.*;
import soot.jimple.*;
import soot.options.Options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static soot.SootClass.HIERARCHY;

public class InstrumentUtil {
    public  final String TAG = "<FUZZING>";

    public static void setupSoot(String apkPath, String androidJarPath, String outputPath){
        // reset soot and setup soot config
        G.reset();

        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_validate(true); // Validate Jimple bodies in each transofrmation pack
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_force_overwrite(true);
        Options.v().set_output_format(Options.output_format_dex);
        Options.v().set_android_jars(androidJarPath);
        Options.v().set_process_dir(Collections.singletonList(apkPath));
        Options.v().set_include_all(true);

        Options.v().set_process_multiple_dex(true);
        Options.v().set_output_dir(outputPath + "/instrument/");


        // Resolve required classes
        Scene.v().addBasicClass("android.app.Application$OnProvideAssistDataListener", HIERARCHY);
        Scene.v().addBasicClass("java.io.PrintStream", SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System", SootClass.SIGNATURES);
        Scene.v().addBasicClass("android.util.Log", SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.AssertionError", SootClass.SIGNATURES);

        Scene.v().loadNecessaryClasses();

        System.out.println("[Instrumentation Phase] Starting ...");
        System.out.println("[Instrumentation Phase] Setting Configuration is done!");

    }

    public  List<Unit> generateLogStmts(JimpleBody b, String msg) {
        return generateLogStmts(b, msg, null);
    }

    public  List<Unit> getnerateTime(JimpleBody b, String msg) {
        List<Unit> generated = new ArrayList<>();
        // get current time
        SootMethod sm = Scene.v().getMethod("<java.lang.System: long nanoTime()>");
        StaticInvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(sm.makeRef());
        Local timeVar = generateNewLocal(b, LongType.v());
        AssignStmt assignStmt = Jimple.v().newAssignStmt(timeVar, invokeExpr);
        generated.add(assignStmt);
        generated.addAll(generateLogStmts(b, msg, timeVar));
        return generated;
    }

    public  List<Unit> generateLogStmts(JimpleBody b, String msg, Value value) {
        List<Unit> generated = new ArrayList<>();
        Value logMessage = StringConstant.v(msg);
        Value logType = StringConstant.v(TAG);
        Value logMsg = logMessage;
        if (value != null)
            logMsg = this.appendTwoStrings(b, logMessage, value, generated);
        SootMethod sm = Scene.v().getMethod("<android.util.Log: int i(java.lang.String,java.lang.String)>");
        StaticInvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(sm.makeRef(), logType, logMsg);
        generated.add(Jimple.v().newInvokeStmt(invokeExpr));
        return generated;
    }

    private  Local appendTwoStrings(Body b, Value s1, Value s2, List<Unit> generated) {
        RefType stringType = Scene.v().getSootClass("java.lang.String").getType();
        SootClass builderClass = Scene.v().getSootClass("java.lang.StringBuilder");
        RefType builderType = builderClass.getType();
        NewExpr newBuilderExpr = Jimple.v().newNewExpr(builderType);
        Local builderLocal = generateNewLocal(b, builderType);
        generated.add(Jimple.v().newAssignStmt(builderLocal, newBuilderExpr));
        Local tmpLocal = generateNewLocal(b, builderType);
        Local resultLocal = generateNewLocal(b, stringType);

        VirtualInvokeExpr appendExpr = Jimple.v().newVirtualInvokeExpr(builderLocal,
                builderClass.getMethod("java.lang.StringBuilder append(java.lang.String)").makeRef(), toString(b, s2, generated));
        VirtualInvokeExpr toStrExpr = Jimple.v().newVirtualInvokeExpr(builderLocal, builderClass.getMethod("java.lang.String toString()").makeRef());

        generated.add(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(builderLocal, builderClass.getMethod("void <init>(java.lang.String)").makeRef(), s1)));
        generated.add(Jimple.v().newAssignStmt(tmpLocal, appendExpr));
        generated.add(Jimple.v().newAssignStmt(resultLocal, toStrExpr));

        return resultLocal;
    }

    public  Value toString(Body b, Value value, List<Unit> generated) {
        SootClass stringClass = Scene.v().getSootClass("java.lang.String");
        if (value.getType().equals(stringClass.getType()))
            return value;
        Type type = value.getType();

        if (type instanceof PrimType) {
            Local tmpLocal = generateNewLocal(b, stringClass.getType());
            generated.add(Jimple.v().newAssignStmt(tmpLocal,
                    Jimple.v().newStaticInvokeExpr(stringClass.getMethod("java.lang.String valueOf(" + type.toString() + ")").makeRef(), value)));
            return tmpLocal;
        } else if (value instanceof Local){
            Local base = (Local) value;
            SootMethod toStrMethod = Scene.v().getSootClass("java.lang.Object").getMethod("java.lang.String toString()");
            Local tmpLocal = generateNewLocal(b, stringClass.getType());
            generated.add(Jimple.v().newAssignStmt(tmpLocal,
                    Jimple.v().newVirtualInvokeExpr(base, toStrMethod.makeRef())));
            return tmpLocal;
        }
        else{
            throw new RuntimeException(String.format("The value %s should be primitive or local but it's %s", value, value.getType()));
        }
    }

    /**
     * @param b: type of the local variable to be instrumented
     */
    public List<Unit> generateAssertionErrorCall(JimpleBody b, String msg) {

        List<Unit> generated = new ArrayList<>();

        // Create the Local variable with type of <java.lang.AssertionError>
        SootClass errorClass = Scene.v().getSootClass("java.lang.AssertionError");
        NewExpr newExpr = Jimple.v().newNewExpr(errorClass.getType());
        Type assertionType = RefType.v(errorClass);
        Local localAssertion = generateNewLocal(b, assertionType);
        AssignStmt assignStmt = Jimple.v().newAssignStmt(localAssertion, newExpr);
        generated.add(assignStmt);

        // Invoke the assertionError init function
        SpecialInvokeExpr specialInvokeExpr = generateAssertionErrorMethod(localAssertion,msg);
        generated.add(Jimple.v().newInvokeStmt(specialInvokeExpr));

        // throw the error :)
        Unit throwStmt = Jimple.v().newThrowStmt(localAssertion);
        generated.add(throwStmt);
        return generated;
    }

    /**
     * @param AssertionError: type of the local variable to be instrumented
     * @return
     */
    private  SpecialInvokeExpr generateAssertionErrorMethod(Local AssertionError, String msg) {
        SootMethod sm = Scene.v().getMethod("<java.lang.AssertionError: void <init>(java.lang.Object)>");
        Value value = StringConstant.v(msg);
        SpecialInvokeExpr sinvokeExpr = Jimple.v().newSpecialInvokeExpr(AssertionError, sm.makeRef(), value);
        return sinvokeExpr;
    }


    public  Local generateNewLocal(Body body, Type type) {
        LocalGenerator lg = Scene.v().createLocalGenerator(body);// new LocalGenerator(body);
        return lg.generateLocal(type);
    }

    public List<Unit> generateIfStatement(JimpleBody jBody) {

        List<Unit> generated = new ArrayList<>();

        // create an integer of value 0
        IntConstant zero=IntConstant.v(0);
        Local condition = generateNewLocal(jBody, IntType.v());

        // create an equality expression pf condition=zero
        EqExpr eqExpr =Jimple.v().newEqExpr(condition,zero);
        Value one=IntConstant.v(1);

        // assign value @one = 1 to the local variable @condition
        AssignStmt assignStmt =Jimple.v().newAssignStmt(condition, one);
        generated.add(assignStmt);

        return generated;
    }
}
