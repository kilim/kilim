/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.tools;

import static kilim.Constants.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * This is a replacement for the jasmin bytecode assembler and uses the same
 * syntax. The main reason for writing it is that jasmin (v 2.1 at the time of
 * writing) didn't correcly support annotations. That is, the annotations
 * inserted by jasmin don't show up in java.lang.reflect.Method, even though the
 * annotations are in the class file. It was easier to write this tool than to
 * release a separate fix for jasmin.
 * 
 * Usage: java kilim.tools.Asm <.j file(s)>
 * @author sriram srinivasan (sriram@malhar.net)
 */
public class Asm {
    static boolean                  quiet          = false;
    static String                   outputDir      = ".";
    static Pattern                  wsPattern      = Pattern.compile("\\s+");
    static Pattern                  commentPattern = Pattern.compile("^;.*$| ;[^\"]*");

    private boolean                 eofOK          = false;
    private ClassWriter             cv;
    private MethodVisitor           mv;
    private int                     maxLocals = 1;
    private int                     maxStack = 1;
    private HashSet<String>         declaredLabels = new HashSet<String>();
    private HashMap<String, Label>  labels         = new HashMap<String, Label>();
    private String                  className;
    private String                  methodName;
    private String                  fileName;
    private Line                    line, bufferedLine;
    private Matcher                 lastMatch= null; // for error context
    private Pattern                 lastPattern = null;


    private LineNumberReader        reader;

    static HashMap<String, Integer> modifiers      = new HashMap<String, Integer>();

    static {
        modifiers.put("public", ACC_PUBLIC);
        modifiers.put("private", ACC_PRIVATE);
        modifiers.put("protected", ACC_PROTECTED);
        modifiers.put("static", ACC_STATIC);
        modifiers.put("final", ACC_FINAL);
        modifiers.put("super", ACC_SUPER);
        modifiers.put("synchronized", ACC_SYNCHRONIZED);
        modifiers.put("volatile", ACC_VOLATILE);
        modifiers.put("transient", ACC_TRANSIENT);
        modifiers.put("native", ACC_NATIVE);
        modifiers.put("interface", ACC_INTERFACE);
        modifiers.put("abstract", ACC_ABSTRACT);
        modifiers.put("strict", ACC_STRICT);
        modifiers.put("enum", ACC_ENUM);
    }

    public static void main(String[] args) throws IOException {
        List<String> files = parseArgs(args);
        for (String arg : files) {
            if (!quiet) {System.out.println("Asm: "  + arg);}
            new Asm(arg).write();
        }
    }

    public Asm(String afileName) throws IOException {
        fileName = afileName;
        reader = new LineNumberReader(new FileReader(fileName));
        cv = new ClassWriter(false);
        try {
            parseClass();
        } catch (EOF eof) {
            if (!eofOK) {
                System.err.println("Premature end of file: " + fileName);
                System.exit(1);
            }
        } catch (AsmException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            System.out.println("File: " + fileName);
            if (methodName != null) {
                System.out.println("Method: " + methodName);
            }
            System.out.println("");
            System.out.println("Line " + line);
            System.out.println("Last pattern match: " + lastPattern);
            throw e;
        }
    }

    // .class public final Foo/Bar/Baz 
    private static String classNamePatternStr = "[\\w/$]+";
    private static String modifierPatternStr = "public|private|protected|static|final|synchronized|volatile|transient|native|abstract|strict| ";
    private static Pattern classPattern = Pattern.compile("\\.(class|interface) ((" + modifierPatternStr + ")*)(" + classNamePatternStr + ")$");
    private void parseClass() {
        readLine();
        // match class declaration
        int acc = 0;
        if (!lineMatch(classPattern)) {
            err("Expected .class or .interface declaration");
        }
        
        if (line.startsWith(".interface")) {
            acc = ACC_INTERFACE;
        }
        
        acc |= parseModifiers(group(2));
        className = group(4);
        String superClassName = parseSuper();
        String[] interfaces = parseInterfaces();
        cv.visit(V1_5, acc, className, null, superClassName, interfaces);
        
        parseClassBody();

        eofOK = true;
    }

    private int parseModifiers(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.equals("")) return 0;
        int acc = 0;
        for (String modifier : split(wsPattern, s)) {
            if (!modifiers.containsKey(modifier)) {
                err("Modifier " + modifier + " not recognized");
            }
            acc |= modifiers.get(modifier);
        }
        return acc;
    }

    private static Pattern superPattern = Pattern.compile("\\.super (" + classNamePatternStr + ")$");

    private String parseSuper() {
        readLine();
        if (!lineMatch(superPattern)) {
            err("Expected .super <superclass>");
        }
        return group(1);
    }

    private static Pattern implementsPattern = Pattern.compile("\\.implements +(" + classNamePatternStr + ")$");

    private String[] parseInterfaces() {
        StringList interfaces = new StringList();
        while (true) {
            readLine();
            if (!lineMatch(implementsPattern)) {
                putBackLine();
                return interfaces.toArray();
            }
            interfaces.add(group(1));
        }
    }

    private void parseClassBody() {
        while (true) {
            readLine(); // this breaks out of the loop upon EOF
            if (lineMatch(fieldPattern)) {
                parseField();
            } else if (lineMatch(methodPattern)) {
                parseMethod();
            } else if (lineMatch(annotationPattern)){
                readLine();
                if (!line.startsWith(".end annotation")) {
                    err(".end annotation not present");
                }
            } else {
                err("Expected field, method or annotation in class body");
            }
        }
    }

    // The field declaration "public final String fileName = "foobar" is declared as
    // .field public final  fieldName [[Ljava/lang/String; = "foobar" 
    // .field (modifier)* name type (= constval)?
    private static String namePatternStr = "[$\\w]+";
    private static String descPatternStr = "[$\\[\\w/;]+";
    private static Pattern fieldPattern  = 
        Pattern.compile(".field +((" + modifierPatternStr + ")*) +(" + namePatternStr + ") +(" + descPatternStr + ") *(= *(.*))?");
    private void parseField() {
        String name       = group(3);
        String desc       = group(4);
        String valueStr   = group(6);
        Object value      = valueStr == null ? null : 
                             parseValue(valueStr, 
                                       (desc.equals(D_DOUBLE) || desc.equals(D_LONG)));
        cv.visitField(
                parseModifiers(group(1)), 
                name, // field name
                desc,
                null, // no signature 
                value);
    }

    // A method declaration for
    //   private static final Object[][]foobar(int, long, boolean)
    // is specified as
    //   .method private final static foobar(IJZ)[[Ljava/lang/Object; 
    //   .method <init>(IJZ)
    private static String methodNamePatternStr = "[<>\\w]+"; // 
    private static Pattern methodPattern = Pattern.compile(".method +(("+ modifierPatternStr + ")*) ("+ methodNamePatternStr + ") *([(][^\\s]+)");

    private void parseMethod() {
        eofOK = false;
        methodName = group(3);
        int acc = parseModifiers(group(1));
        String desc = group(4);
        
        String[] exceptions = parseMethodExceptions();
        mv = cv.visitMethod(
                acc,
                methodName,
                desc, // method desc
                null, // signature
                exceptions);

        parseMethodBody();
        eofOK = true;
    }

    private static Pattern throwsPattern = Pattern.compile("^ *\\.throws +(" + classNamePatternStr + ")");

    private String[] parseMethodExceptions() {
        StringList l = new StringList();
        while (true) {
            readLine();
            if (!lineMatch(throwsPattern)) {
                putBackLine();
                return l.toArray();
            }
            l.add(group(1));
        }
    }

    private void parseMethodBody() {
        labels.clear();
        declaredLabels.clear();
        mv.visitCode();
        while (true) {
            readLine();
            if (line.startsWith(".end method")) {
                break;
            } else if (line.startsWith(".")) {
                parseMethodDirective();
            } else if (lineMatch(labelPattern)) {
                parseLabel();
            } else {
                parseInstructions();
            }
        }
        checkLabelDeclarations();
        mv.visitMaxs(maxStack, maxLocals);
        mv.visitEnd();
    }

    private static Pattern labelPattern = Pattern.compile("^(\\w+) *: *$");
    private void parseLabel() {
        String str = group(1);
        if (declaredLabels.contains(str)) {
            err("Duplicate label " + str);
        } else {
            declaredLabels.add(str);
            Label l = getLabel(str);
            mv.visitLabel(l);
        }
    }
    
    private void checkLabelDeclarations() {
        for (String key: labels.keySet()) {
            if (!declaredLabels.contains(key)) {
                throw new AsmException("Label " + key + " not declared in " + methodName);
            }
        }
    }

    static Pattern localsPattern     = Pattern.compile(".limit +locals +([0-9]+)");
    static Pattern stackPattern      = Pattern.compile(".limit +stack +([0-9]+)");
    static Pattern catchPattern      = Pattern.compile(".catch +(" + classNamePatternStr + ") +from +([\\w]+) +to +([\\w]+) +using +([\\w]+)");
    static Pattern annotationPattern = Pattern.compile(".annotation +((visible) )?([\\w/;]+)");
    private void parseMethodDirective() {
        if (lineMatch(localsPattern)) {
            maxLocals = parseInt(group(1));
        } else if (lineMatch(stackPattern)) {
            maxStack = parseInt(group(1));
        } else if (lineMatch(catchPattern)) {
            String exceptionType = group(1);
            if (exceptionType.equals("all")) {
                exceptionType = null;
            }
            Label fromLabel = getLabel(group(2));
            Label toLabel = getLabel(group(3));
            Label usingLabel = getLabel(group(4));
            mv.visitTryCatchBlock(fromLabel, toLabel, usingLabel, exceptionType);
        } else if (lineMatch(annotationPattern)) {
            parseAnnotation();
        } else if (!quiet) {
            System.err.println("Directive ignored: " + line);
        }
    }

    private void parseAnnotation() {
        String s = group(2);
        boolean visible = s == null ? false : s.equals("visible");
        String desc = group(3);
        mv.visitAnnotation(desc, visible);
        readLine();
        if (!line.startsWith(".end annotation")) {
            err(".end annotation not present");
        }
    }
    
    static String                                 opcodeStrs[]   = { "nop",
            "aconst_null", "iconst_m1", "iconst_0", "iconst_1", "iconst_2",
            "iconst_3", "iconst_4", "iconst_5", "lconst_0", "lconst_1",
            "fconst_0", "fconst_1", "fconst_2", "dconst_0", "dconst_1",
            "bipush", "sipush", "ldc", "ldc_w", "ldc2_w", "iload", "lload",
            "fload", "dload", "aload", "iload_0", "iload_1", "iload_2",
            "iload_3", "lload_0", "lload_1", "lload_2", "lload_3", "fload_0",
            "fload_1", "fload_2", "fload_3", "dload_0", "dload_1", "dload_2",
            "dload_3", "aload_0", "aload_1", "aload_2", "aload_3", "iaload",
            "laload", "faload", "daload", "aaload", "baload", "caload",
            "saload", "istore", "lstore", "fstore", "dstore", "astore",
            "istore_0", "istore_1", "istore_2", "istore_3", "lstore_0",
            "lstore_1", "lstore_2", "lstore_3", "fstore_0", "fstore_1",
            "fstore_2", "fstore_3", "dstore_0", "dstore_1", "dstore_2",
            "dstore_3", "astore_0", "astore_1", "astore_2", "astore_3",
            "iastore", "lastore", "fastore", "dastore", "aastore", "bastore",
            "castore", "sastore", "pop", "pop2", "dup", "dup_x1", "dup_x2",
            "dup2", "dup2_x1", "dup2_x2", "swap", "iadd", "ladd", "fadd",
            "dadd", "isub", "lsub", "fsub", "dsub", "imul", "lmul", "fmul",
            "dmul", "idiv", "ldiv", "fdiv", "ddiv", "irem", "lrem", "frem",
            "drem", "ineg", "lneg", "fneg", "dneg", "ishl", "lshl", "ishr",
            "lshr", "iushr", "lushr", "iand", "land", "ior", "lor", "ixor",
            "lxor", "iinc", "i2l", "i2f", "i2d", "l2i", "l2f", "l2d", "f2i",
            "f2l", "f2d", "d2i", "d2l", "d2f", "i2b", "i2c", "i2s", "lcmp",
            "fcmpl", "fcmpg", "dcmpl", "dcmpg", "ifeq", "ifne", "iflt", "ifge",
            "ifgt", "ifle", "if_icmpeq", "if_icmpne", "if_icmplt", "if_icmpge",
            "if_icmpgt", "if_icmple", "if_acmpeq", "if_acmpne", "goto", "jsr",
            "ret", "tableswitch", "lookupswitch", "ireturn", "lreturn",
            "freturn", "dreturn", "areturn", "return", "getstatic",
            "putstatic", "getfield", "putfield", "invokevirtual",
            "invokespecial", "invokestatic", "invokeinterface", "unused",
            "new", "newarray", "anewarray", "arraylength", "athrow",
            "checkcast", "instanceof", "monitorenter", "monitorexit", "wide",
            "multianewarray", "ifnull", "ifnonnull", "goto_w", "jsr_w" };

    private final static HashMap<String, Integer> opcodeMap      = new HashMap<String, Integer>();
    private final static byte[]                   visitTypes;
    private final static int                      INSN           = 0;
    private final static int                      VAR            = 1;
    private final static int                      LDC            = 2;
    private final static int                      JUMP           = 3;
    private final static int                      TABLESWITCH    = 4;
    private final static int                      LOOKUPSWITCH   = 5;
    private final static int                      FIELD          = 6;
    private final static int                      METHOD         = 7;
    private final static int                      TYPE           = 8;
    private final static int                      MULTIANEWARRAY = 9;
    private final static int                      INT            = 10;
    private final static int                      IINC           = 11;

    static {
        for (int i = 0; i < opcodeStrs.length; i++) {
            opcodeMap.put(opcodeStrs[i], i);
        }
        opcodeMap.put("invokenonvirtual", opcodeMap.get("invokespecial"));
        
        // Generated the table from asm.Opcode
        visitTypes = new byte[] {
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INT, INT, LDC, LDC,
                LDC, VAR, VAR, VAR, VAR, VAR, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, VAR, VAR, VAR, VAR, VAR, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, IINC, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN, INSN,
                INSN, INSN, INSN, JUMP, JUMP, JUMP, JUMP, JUMP, JUMP, JUMP,
                JUMP, JUMP, JUMP, JUMP, JUMP, JUMP, JUMP, JUMP, JUMP, VAR,
                TABLESWITCH, LOOKUPSWITCH, INSN, INSN, INSN, INSN, INSN, INSN, FIELD, FIELD,
                FIELD, FIELD, METHOD, METHOD, METHOD, METHOD, INSN, TYPE, INT, TYPE,
                INSN, INSN, TYPE, TYPE, INSN, INSN, INSN, MULTIANEWARRAY, JUMP, JUMP,
                JUMP, JUMP
        };
    }

    static final Pattern insnPattern       = Pattern.compile("(\\w+)( +(.*))?");
    static final Pattern quotedPattern     = Pattern.compile("(.*)");
    static final Pattern casePattern       = Pattern.compile("(\\w+) *: *(\\w+)");
    static final Pattern methodInvokePattern = Pattern.compile("("+ classNamePatternStr + ")[/.](" + methodNamePatternStr + ") *([(].*?[)]" + descPatternStr + ") *(, *\\d+)?");
    static final Pattern fieldSpecPattern  = Pattern.compile("([\\w/.$]+)[/.]([\\w$]+) +([^\\s]+)");

    private void parseInstructions() {
        // line read in parseMethodBody()
        if (!lineMatch(insnPattern)) {
            err("Instruction is not well-formed");
        }
        String insn = group(1);
        String operand = null;
        if (groupCount() == 3) {
            operand = group(3);
            if (operand != null) {
                operand = operand.trim();
            }
        }
        if (!opcodeMap.containsKey(insn)) {
            err("Instruction " + insn + " not recognized");
        }
        int opcode = opcodeMap.get(insn);
        switch (visitTypes[opcode]) {
            case INSN:
                mv.visitInsn(opcode);
                break;
            case VAR:
                mv.visitVarInsn(opcode, parseInt(operand));
                break;
            case LDC:
                mv.visitLdcInsn(parseValue(operand, (opcode == LDC2_W)));
                break;
            case JUMP:
                Label l = getLabel(operand);
                mv.visitJumpInsn(opcode, l);
                break;

            case TABLESWITCH: {
                int min = parseInt(operand);
                ArrayList<Label> labelList = new ArrayList<Label>(10);
                Label defLabel = null;
                while (true) {
                    readLine();
                    if (line.startsWith("default")) {
                        lineMatch(casePattern);
                        defLabel = getLabel(group(2));
                        break;
                    } else {
                        labelList.add(getLabel(line.s));
                    }
                }
                Label[] labels = labelList.toArray(new Label[labelList.size()]);
                int max = labels.length - 1;
                mv.visitTableSwitchInsn(min, max, defLabel, labels);
                break;
            }
            case LOOKUPSWITCH: {
                ArrayList<Integer> keyList = new ArrayList<Integer>(10);
                ArrayList<Label> labelList = new ArrayList<Label>(10);
                Label defLabel = null;
                while (true) {
                    readLine();

                    if (lineMatch(casePattern)) {
                        Label lab = getLabel(group(2));
                        String keystr = group(1);
                        if (keystr.equals("default")) {
                            defLabel = lab;
                            break;
                        } else {
                            int key = parseInt(keystr);
                            keyList.add(key);
                            labelList.add(lab);
                        }
                    } else {
                        err("Ill-formed switch instruction");
                    }
                }
                Label[] labels = labelList.toArray(new Label[labelList.size()]);
                int[] keys = new int[keyList.size()];
                for (int i = 0; i < keys.length; i++) {
                    keys[i] = keyList.get(i);
                }
                mv.visitLookupSwitchInsn(defLabel, keys, labels);
                break;
            }
            case FIELD: {
                // getstatic foo/bar/Baz/fieldName I
                if (operand == null || !match(operand, fieldSpecPattern)) {
                    err("Expected field access of the form foo/Bar/fieldName I");
                }
                String owner = group(1);
                String name = group(2);
                String desc = group(3);
                mv.visitFieldInsn(opcode, owner, name, desc);
                break;
            }
            case METHOD: {
                // invokevirtual foo/bar/Baz/methodName(IJZ)V
                if (operand == null || !match(operand, methodInvokePattern)) {
                    err("Expected method invocation of the form /foo/Bar/methodName(IJ)V");
                }
                String owner = group(1);
                String name = group(2);
                String desc = group(3);
                mv.visitMethodInsn(opcode, owner, name, desc);
                break;
            }
            case TYPE:
                opcheck("expected type", operand);
                mv.visitTypeInsn(opcode, operand);
                break;
                
            case MULTIANEWARRAY: {
                opcheck("expected array type and dimensions", operand);
                String words[] = split(wsPattern, operand);
                mv.visitMultiANewArrayInsn(words[0], parseInt(words[1]));
                break;
            }
            
            case INT: {
                int op = -1;
                if (opcode == NEWARRAY) {
                    if (operand.equals("boolean")) {
                        op = T_BOOLEAN;
                    } else if (operand.equals("char")) {
                        op = T_CHAR;
                    } else if (operand.equals("float")) {
                        op = T_FLOAT;
                    } else if (operand.equals("double")) {
                        op = T_DOUBLE;
                    } else if (operand.equals("byte")) {
                        op = T_BYTE;
                    } else if (operand.equals("short")) {
                        op = T_SHORT;
                    } else if (operand.equals("int")) {
                        op = T_INT;
                    } else if (operand.equals("long")) {
                        op = T_LONG;
                    } else {
                        err("Unknown type for newarray: " + operand);
                    }
                } else {
                    op = parseInt(operand);
                }

                mv.visitIntInsn(opcode, op);
                break;
            }
            
            case IINC: {
                opcheck("Expected iinc <var> <inc amount>", operand);
                String words[] = split(wsPattern, operand);
                int var = parseInt(words[0]);
                int increment = parseInt(words[1]);
                mv.visitIincInsn(var, increment);
                break;
            }
            
            default:
                err("INTERNAL ERROR: UNKNOWN TYPE OF INSTRUCTION");
        }
    }
    
    private void opcheck(String errMessage, String operand) {
        if (operand == null) {
            err(errMessage);
        }
    }
    
    private Object parseValue(String s, boolean isDoubleWord) {
        Object ret = null;
        if (s == null) {
            err("Expected constant value ");
        }
        if (s.startsWith("\"")) {
            if (isDoubleWord) {
                err("long or double value expected instead of string");
            }
            if (s.charAt(s.length() - 1) != '"') {
                err("Ill-formed string");
            }
            ret = s.substring(1, s.length() - 1);
        } else if (s.startsWith("L")) {
            // class name
            ret = Type.getType(s);
        } else {
            if (s.indexOf('.') == -1) {
                if (isDoubleWord) {
                    ret = (Long)parseLong(s);
                } else {
                    ret = (Integer) parseInt(s);
                }
            } else {
                if (isDoubleWord) {
                    ret = (Double)parseDouble(s); 
                } else {
                    ret = (Float)parseFloat(s);
                }
            }
        }
        return ret;
    }
    
    int parseInt(String s) {
        if (s == null) {
            err("Expected integer");
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException nfe) {
            err("Expected integer value, got " + s);
        }
        return 0;
    }

    long parseLong(String s) {
        if (s == null) {
            err("Expected long");
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException nfe) {
            err("Expected long value, got " + s);
        }
        return 0L;
    }
    
    float parseFloat(String s) {
        if (s == null) {
            err("Expected float");
        }
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException nfe) {
            err("Expected float, got " + s);
        }
        return 0.0f;
    }

    double parseDouble(String s) {
        if (s == null) {
            err("Expected float");
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException nfe) {
            err("Expected double, got " + s);
        }
        return 0.0;
    }

    Label getLabel(String s) {
        if (s == null) {
            err("Expected label string");
        }
        Label ret = labels.get(s);
        if (ret == null) {
            ret = new Label();
            labels.put(s, ret);
        }
        return ret;
    }

    private void err(String s) {
        String msg = String.format("%s: %d: %s\n", fileName, line.n, s);
        msg += line.s; 
        throw new AsmException(msg);
    }

    // return the next non-empty line after stripping comments
    private Line readLine() {
        if (bufferedLine != null) {
            line = bufferedLine;
            bufferedLine = null;
            return line;
        }
        while (true) {
            Line l = getLine();
            String s = l.s.trim();
            s = commentPattern.matcher(s).replaceAll("");
            if (s.length() > 0) {
                l.s = s;
                line = l;
                return l;
            }
        }
    }
    

    private void putBackLine() {
        bufferedLine = line;
    }

    boolean eofSeen = false;

    private Line getLine() {
        if (eofSeen) {
            throw new EOF();
        }
        try {
            String s = reader.readLine();
            if (s == null) {
                eofSeen = true;
                s = "";
            }
            return new Line(reader.getLineNumber(), s);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new EOF();
        }
    }
    
    
    boolean match(String s, Pattern p) {
        lastMatch = p.matcher(s);
        lastPattern = p;
        return lastMatch.find();
    }

    boolean lineMatch(Pattern p) {
        lastMatch = p.matcher(line.s);
        lastPattern = p;
        return lastMatch.find();
    }
    
    String group(int i) {
        String ret = lastMatch.group(i);
        return ret; 
    }
    
    int groupCount() {
        return lastMatch.groupCount();
    }

    static String[] split(Pattern p, String s) {
        return p.split(s);
    }

    private void write() throws IOException {
        String dir = outputDir + "/" + getDirName(className);
        mkdir(dir);
        String fileName = outputDir + '/' + className + ".class";
        FileOutputStream fos = new FileOutputStream(fileName);
        fos.write(cv.toByteArray());
        fos.close();
        System.out.println("Wrote: " + fileName);
    }

    private static void mkdir(String dir) throws IOException {
        File f = new File(dir);
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new IOException("Unable to create directory: " + dir);
            }
        }
    }

    private static String getDirName(String className) {
        int end = className.lastIndexOf('/');
        return (end == -1) ? "" : className.substring(0, end);
    }

    private static List<String> parseArgs(String[] args) {
        ArrayList<String> ret = new ArrayList<String>(args.length);
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-d")) {
                outputDir = args[++i];
            } else if (arg.equals("-q")) {
                quiet = true;
            } else {
                ret.add(arg);
            }
        }
        return ret;
    }

}

@SuppressWarnings("serial")
class EOF extends RuntimeException {
}

class Line {
    int            n;
    String         s;

    Line(int num, String str) {
        n = num;
        s = str;
    }

    public String toString() {
        return String.format("%4d: %s\n", n, s);
    }

    public boolean startsWith(String str) {
        return s.startsWith(str);
    }
}

@SuppressWarnings("serial")
class StringList extends ArrayList<String> {
    public String[] toArray() {
        String[] ret = new String[size()];
        return this.toArray(ret);
    }
}

@SuppressWarnings("serial") 
class AsmException extends RuntimeException {
    public AsmException(String s) {super(s);}
}