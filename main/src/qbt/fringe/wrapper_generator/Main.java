package qbt.fringe.wrapper_generator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Main {
    public static void main(String[] args) throws IOException {
        if(args.length < 2 || args.length % 2 != 0) {
            System.out.println("Usage: wrapper-generator <input directory> <output directory> (<wrapper> <main class>)*");
            System.exit(1);
        }
        File inputDirectory = new File(args[0]);
        File outputDirectory = new File(args[1]);
        File libDirectory = new File(outputDirectory, "lib");
        File binDirectory = new File(outputDirectory, "bin");
        File mainsDirectory = new File(outputDirectory, "mains");
        libDirectory.mkdirs();
        binDirectory.mkdirs();
        mainsDirectory.mkdirs();
        Map<String, Map<Long, List<String>>> classToCrcToJars = new HashMap<>();
        for(File packageDir : inputDirectory.listFiles()) {
            if(packageDir.getName().startsWith(".")) {
                continue;
            }
            File jarsDir = new File(packageDir, "jars");
            if(!jarsDir.isDirectory()) {
                continue;
            }
            for(File jar : jarsDir.listFiles()) {
                String jarName = jar.getName();
                if(jar.isFile() && jarName.endsWith(".jar")) {
                    try(ZipFile zf = new ZipFile(jar)) {
                        Enumeration<? extends ZipEntry> en = zf.entries();
                        while(en.hasMoreElements()) {
                            ZipEntry ze = en.nextElement();
                            if(ze.getName().endsWith(".class")) {
                                long crc = ze.getCrc();
                                Map<Long, List<String>> crcToJars = classToCrcToJars.get(ze.getName());
                                if(crcToJars == null) {
                                    classToCrcToJars.put(ze.getName(), crcToJars = new HashMap<>());
                                }
                                List<String> jars = crcToJars.get(crc);
                                if(jars == null) {
                                    crcToJars.put(crc, jars = new LinkedList<>());
                                }
                                jars.add(jarName);
                            }
                        }
                    }
                    File destination = new File(libDirectory, jar.getName());
                    Files.copy(jar.toPath(), destination.toPath());
                }
            }
        }
        // It's deeply insane to have multiple jars provide the same class but,
        // if for some dumb-ass reason they provide the same file (or at least
        // CRC) we look the other way.  However, if they are different class
        // contents (or at least CRC) we're putting our foot down.
        for(Map.Entry<String, Map<Long, List<String>>> e : classToCrcToJars.entrySet()) {
            if(e.getValue().size() > 1) {
                throw new IllegalArgumentException("Classfile collision at " + e.getKey() + ": " + e.getValue());
            }
        }
        List<String> wrapper = new LinkedList<>();
        try(InputStream is = Main.class.getResourceAsStream("wrapper.py")) {
            try(InputStreamReader isr = new InputStreamReader(is)) {
                try(BufferedReader br = new BufferedReader(isr)) {
                    while(true) {
                        String line = br.readLine();
                        if(line == null) {
                            break;
                        }
                        wrapper.add(line);
                    }
                }
            }
        }
        for(int i = 2; i < args.length; i += 2) {
            String name = args[i];
            String clazz = args[i + 1];

            File mainFile = new File(mainsDirectory, name);
            writeLines(mainFile, Collections.singleton(clazz));

            File wrapperFile = new File(binDirectory, name);
            writeLines(wrapperFile, wrapper);
            wrapperFile.setExecutable(true);
        }
        List<String> env = new LinkedList<String>();
        class Helper {
            private void add(String wgName, String name) {
                String val = System.getenv(name);
                if(val != null) {
                    env.add(wgName + "=" + val);
                }
            }
        }
        Helper h = new Helper();
        h.add("WG_PACKAGE_NAME", "PACKAGE_NAME");
        h.add("WG_PACKAGE_CUMULATIVE_VERSION", "PACKAGE_CUMULATIVE_VERSION");
        h.add("WG_VANITY_NAME", "QBT_ENV_VANITY_NAME");
        writeLines(new File(outputDirectory, "env"), env);
    }

    private static void writeLines(File file, Iterable<String> lines) throws IOException {
        try(FileOutputStream fos = new FileOutputStream(file)) {
            try(PrintWriter pw = new PrintWriter(fos)) {
                for(String line : lines) {
                    pw.println(line);
                }
            }
        }
    }
}
