package de.geolykt.starloader.widener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipEntry;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;

public class WidenerGradlePlugin implements Plugin<Project> {

    public static File accessWidenerFile;
    public static File affectedFile;

    @Override
    public void apply(Project gradleProject) {
        WidenerGradleExtension extension = gradleProject.getExtensions().create("widener", WidenerGradleExtension.class);

        gradleProject.afterEvaluate(project -> {
            if (extension.accessWidener == null) {
                return;
            }
            accessWidenerFile = project.file(extension.accessWidener);
            if (!accessWidenerFile.exists()) {
                throw new RuntimeException("Could not find the specified access widener file which should be at " + accessWidenerFile.getAbsolutePath());
            }

            affectedFile = project.file(extension.affectedFile);
            if (!affectedFile.exists()) {
                throw new RuntimeException("Affected file was not found: " + affectedFile.getAbsolutePath());
            }

            AccessWidener accessWidener = new AccessWidener();
            AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);

            try (BufferedReader reader = new BufferedReader(new FileReader(accessWidenerFile))) {
                accessWidenerReader.read(reader);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read project access widener file");
            }
            if (accessWidener.getTargets().isEmpty()) {
                return;
            }
            process(accessWidener, affectedFile);
        });
    }

    public void process(AccessWidener accessWidener, File file) {
            ZipUtil.transformEntries(file, getTransformers(accessWidener, accessWidener.getTargets()));
//            ZipUtil.addEntry(file, "aw.sha256", inputHash);
    }

    private ZipEntryTransformerEntry[] getTransformers(AccessWidener accessWidener, Set<String> classes) {
            return classes.stream()
                            .map(string -> new ZipEntryTransformerEntry(string.replaceAll("\\.", "/") + ".class", getTransformer(accessWidener, string)))
                            .toArray(ZipEntryTransformerEntry[]::new);
    }

    private ZipEntryTransformer getTransformer(AccessWidener accessWidener, String className) {
            return new ByteArrayZipEntryTransformer() {
                    @Override
                    protected byte[] transform(ZipEntry zipEntry, byte[] input) {
                            ClassReader reader = new ClassReader(input);
                            ClassWriter writer = new ClassWriter(0);
                            ClassVisitor classVisitor = AccessWidenerVisitor.createClassVisitor(Opcodes.ASM9, writer, accessWidener);
                            reader.accept(classVisitor, 0);
                            return writer.toByteArray();
                    }
            };
    }
}
