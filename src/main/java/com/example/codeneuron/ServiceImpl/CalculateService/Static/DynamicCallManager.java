package com.example.codeneuron.ServiceImpl.CalculateService.Static;

import org.apache.bcel.classfile.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicCallManager {
    private static final Pattern BOOTSTRAP_CALL_PATTERN = Pattern
            .compile("invokedynamic\t(\\d+):\\S+ \\S+ \\(\\d+\\)");
    private static final int CALL_HANDLE_INDEX_ARGUMENT = 1;

    private final Map<String, String> dynamicCallers = new HashMap<>();

    /**
     * Retrieve dynamic call relationships based on the code of the provided
     * {@link Method}.
     *
     * @param method {@link Method} to analyze the code
     * @param jc     {@link JavaClass} info, which contains the bootstrap methods
     * @see #linkCalls(Method)
     */
    public void retrieveCalls(Method method, JavaClass jc) {
        if (method.isAbstract() || method.isNative()) {
            // No code to consider
            return;
        }
        ConstantPool cp = method.getConstantPool();
        BootstrapMethod[] boots = getBootstrapMethods(jc);
        String code = method.getCode().toString();
        Matcher matcher = BOOTSTRAP_CALL_PATTERN.matcher(code);
        while (matcher.find()) {
            int bootIndex = Integer.parseInt(matcher.group(1));
            BootstrapMethod bootMethod = boots[bootIndex];
            int calledIndex = bootMethod.getBootstrapArguments()[CALL_HANDLE_INDEX_ARGUMENT];
            String calledName = getMethodNameFromHandleIndex(cp, calledIndex);
            String callerName = method.getName();
            dynamicCallers.put(calledName, callerName);
        }
    }

    private String getMethodNameFromHandleIndex(ConstantPool cp, int callIndex) {
        ConstantMethodHandle handle = (ConstantMethodHandle) cp.getConstant(callIndex);
        ConstantCP ref = (ConstantCP) cp.getConstant(handle.getReferenceIndex());
        ConstantNameAndType nameAndType = (ConstantNameAndType) cp.getConstant(ref.getNameAndTypeIndex());
        return nameAndType.getName(cp);
    }

    /**
     * Link the {@link Method}'s name to its concrete caller if required.
     *
     * @param method {@link Method} to analyze
     * @see #retrieveCalls(Method, JavaClass)
     */
    public void linkCalls(Method method) {
        int nameIndex = method.getNameIndex();
        ConstantPool cp = method.getConstantPool();
        String methodName = ((ConstantUtf8) cp.getConstant(nameIndex)).getBytes();
        String linkedName = methodName;
        String callerName = methodName;
        while (linkedName.matches("(lambda\\$)+null(\\$\\d+)+")) {
            callerName = dynamicCallers.get(callerName);
            linkedName = linkedName.replace("null", callerName);
        }
        cp.setConstant(nameIndex, new ConstantUtf8(linkedName));
    }

    private BootstrapMethod[] getBootstrapMethods(JavaClass jc) {
        for (Attribute attribute : jc.getAttributes()) {
            if (attribute instanceof BootstrapMethods) {
                return ((BootstrapMethods) attribute).getBootstrapMethods();
            }
        }
        return new BootstrapMethod[]{};
    }
}
