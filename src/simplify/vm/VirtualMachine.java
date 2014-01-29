package simplify.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.ClassUtils;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.util.ReferenceUtil;
import org.jf.dexlib2.writer.builder.BuilderClassDef;
import org.jf.dexlib2.writer.builder.BuilderField;
import org.jf.dexlib2.writer.builder.BuilderMethod;
import org.jf.dexlib2.writer.builder.BuilderMethodParameter;

import simplify.Main;
import simplify.SmaliClassUtils;

public class VirtualMachine {

    private static final Logger log = Logger.getLogger(Main.class.getSimpleName());

    private static List<String> buildImmutableClasses(List<BuilderClassDef> classDefs) {
        List<String> result = new ArrayList<String>();
        for (BuilderClassDef classDef : classDefs) {
            boolean isImmutable = (classDef.getAccessFlags() & AccessFlags.FINAL.getValue()) != 0;
            if (isImmutable) {
                result.add(classDef.getType());
            }
        }

        return result;
    }

    private static MethodContext buildRootContext(BuilderMethod method) {
        List<? extends BuilderMethodParameter> parameters = method.getParameters();
        int registerCount = method.getImplementation().getRegisterCount();
        int parameterCount = parameters.size();

        MethodContext result = new MethodContext(registerCount, parameterCount, 0);

        boolean isStatic = ((method.getAccessFlags() & AccessFlags.STATIC.getValue()) != 0);
        if (!isStatic) {
            // First parameter (p0) is instance reference for non-static methods
            String type = method.getDefiningClass();
            result.pokeRegister(result.getParameterStart() - 1, new RegisterStore(type, "this"));
        }
        // Assume all input values are unknown.
        for (int paramRegister = 0; paramRegister < parameterCount; paramRegister++) {
            BuilderMethodParameter parameter = parameters.get(paramRegister);
            String type = parameter.getType();
            result.setParameter(paramRegister, new RegisterStore(type, new UnknownValue()));
        }

        return result;
    }

    private static Map<String, List<? extends TryBlock<? extends ExceptionHandler>>> buildTryCatchList(
                    List<BuilderClassDef> classDefs) {
        Map<String, List<? extends TryBlock<? extends ExceptionHandler>>> result = new HashMap<String, List<? extends TryBlock<? extends ExceptionHandler>>>();

        for (BuilderClassDef classDef : classDefs) {
            for (BuilderMethod method : classDef.getMethods()) {
                String methodDescriptor = ReferenceUtil.getMethodDescriptor(method);
                result.put(methodDescriptor, method.getImplementation().getTryBlocks());
            }
        }

        return result;

    }

    private static String getClassNameFromMethodDescriptor(String methodDescriptor) {
        return methodDescriptor.split("->", 2)[0];
    }

    private final Map<String, ClassContext> classNameToClassContext;
    private final List<String> initializedClasses;
    private final int maxCallDepth;
    private final int maxNodeVisits;
    private final MethodExecutor methodExecutor;
    private final Map<String, ContextGraph> methodDescriptorToInstructionGraph;
    private final Map<String, List<? extends TryBlock<? extends ExceptionHandler>>> methodToTryCatchList;

    public VirtualMachine(List<BuilderClassDef> classDefs, int maxNodeVisits, int maxCallDepth) {
        this.maxNodeVisits = maxNodeVisits;
        this.maxCallDepth = maxCallDepth;

        methodToTryCatchList = buildTryCatchList(classDefs);

        classNameToClassContext = buildNameToClassContext(classDefs);

        methodExecutor = new MethodExecutor(this);

        initializedClasses = new ArrayList<String>(classDefs.size());

        // Build graphs last because that's when handlers are assigned and some handlers access this vm instance.
        methodDescriptorToInstructionGraph = buildMethodDescriptorToInstructionGraph(classDefs);

    }

    public void updateInstructionGraph(BuilderMethod method) {
        String methodDescriptor = ReferenceUtil.getMethodDescriptor(method);
        ContextGraph graph = new ContextGraph(this, method);
        graph.setRootContext(buildRootContext(method));
        methodDescriptorToInstructionGraph.put(methodDescriptor, graph);
    }

    public ContextGraph execute(String methodDescriptor) {
        MethodContext mctx = methodDescriptorToInstructionGraph.get(methodDescriptor).getRootContext();

        return execute(methodDescriptor, mctx);
    }

    public ContextGraph execute(String methodDescriptor, MethodContext mctx) {
        // Invoking a method (including <init>) is a reason to statically initialize a class.
        staticallyInitializeMethodClassIfNecessary(methodDescriptor);

        ContextGraph result = null;
        try {
            result = methodExecutor.execute(methodDescriptor, mctx);
        } catch (MaxNodeVisitsExceeded | MaxCallDepthExceeded e) {
            log.warning("Exceeded max node visits for " + e.getMessage() + " in " + methodDescriptor + "\nContext: "
                            + mctx);
        }

        return result;
    }

    public ClassContext getClassExecutionContext(String methodDescriptor) {
        // Since this is called for the use or assignment of a class' field, clinit the class
        staticallyInitializeMethodClassIfNecessary(methodDescriptor);

        String className = getClassNameFromMethodDescriptor(methodDescriptor);
        return classNameToClassContext.get(className);
    }

    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    public int getMaxNodeVisits() {
        return maxNodeVisits;
    }

    public Map<String, List<? extends TryBlock<? extends ExceptionHandler>>> getMethodToTryCatchList() {
        return methodToTryCatchList;
    }

    public boolean isImmutableClass(String smaliClassName) {
        if (smaliClassName.startsWith("[")) {
            // Array contents can be mutated, regardless of class.
            return false;
        }

        if (smaliClassName.equals("?")) {
            // Unknown type. Was probably lazy somewhere and didn't get implied type.
            return false;
        }

        if (smaliClassName.equals("Ljava/lang/String;")) {
            return true;
        }

        if (SmaliClassUtils.isPrimitiveType(smaliClassName)) {
            return true;
        }

        String className = smaliClassName.replaceAll("/", ".");
        try {
            Class<?> clazz = ClassUtils.getClass(className, false);
            return ClassUtils.isPrimitiveOrWrapper(clazz);

        } catch (ClassNotFoundException e) {
        }

        return false;
    }

    public boolean isMethodDefined(String methodDescriptor) {
        return methodDescriptorToInstructionGraph.containsKey(methodDescriptor);
    }

    private ClassContext buildClassContext(ContextGraph graph) {
        // graph may be null!
        // TODO Auto-generated method stub
        return null;
    }

    private Map<String, ContextGraph> buildMethodDescriptorToInstructionGraph(final List<BuilderClassDef> classDefs) {
        Map<String, ContextGraph> result = new HashMap<String, ContextGraph>();

        for (BuilderClassDef classDef : classDefs) {
            for (BuilderMethod method : classDef.getMethods()) {
                String methodDescriptor = ReferenceUtil.getMethodDescriptor(method);
                ContextGraph graph = new ContextGraph(this, method);

                graph.setRootContext(buildRootContext(method));

                result.put(methodDescriptor, graph);
            }
        }

        return result;
    }

    private Map<String, ClassContext> buildNameToClassContext(List<BuilderClassDef> classDefs) {
        Map<String, ClassContext> result = new HashMap<String, ClassContext>(classDefs.size());

        for (BuilderClassDef classDef : classDefs) {
            for (BuilderField field : classDef.getFields()) {
                // TODO: implement this and class contexts, make sure array is totally populated
            }
        }

        return result;
    }

    public ContextGraph getInstructionGraph(String methodDescriptor) {
        ContextGraph result = new ContextGraph(methodDescriptorToInstructionGraph.get(methodDescriptor));

        return result;
    }

    void staticallyInitializeMethodClassIfNecessary(String methodDescriptor) {
        // This method should be called when a class is first used. A usage is:
        // 1.) The invocation of a method declared by the class (not inherited from a superclass)
        // 2.) The invocation of a constructor of the class (covered by #1)
        // 3.) The use or assignment of a field declared by a class (not inherited from a superclass), except for fields
        // that are both static and final, and are initialized by a compile-time constant expression.

        String className = getClassNameFromMethodDescriptor(methodDescriptor);
        if (initializedClasses.contains(className)) {
            return;
        }
        initializedClasses.add(className);

        String clinitDescriptor = className + "-><clinit>()V";
        if (!methodDescriptorToInstructionGraph.containsKey(clinitDescriptor)) {
            // No clinit for this class
            return;
        }

        // Just need to execute, don't need the resulting graph. Any class member sets will be recorded by the op
        // handler in the ClassContext.
        execute(clinitDescriptor);
    }
}
