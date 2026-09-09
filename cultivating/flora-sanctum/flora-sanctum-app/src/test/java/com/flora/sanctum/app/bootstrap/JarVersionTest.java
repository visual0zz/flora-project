package com.flora.sanctum.app.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class JarVersionTest {

    @TempDir
    Path dir;

    @Test
    void compareEqual() {
        assertEquals(0, JarVersion.compare("0.1.0", "0.1.0"));
        assertEquals(0, JarVersion.compare("0.1", "0.1.0"));
        assertEquals(0, JarVersion.compare("1.0.0", "1"));
    }

    @Test
    void compareNewer() {
        assertTrue(JarVersion.compare("0.2.0", "0.1.0") > 0);
        assertTrue(JarVersion.compare("1.0.0", "0.2.0") > 0);
        // 10.0 按数值比较应大于 2.0
        assertTrue(JarVersion.compare("10.0", "2.0") > 0);
    }

    @Test
    void compareOlder() {
        assertTrue(JarVersion.compare("0.1.0", "0.2.0") < 0);
        assertTrue(JarVersion.compare("1.0.0", "1.0.1") < 0);
    }

    @Test
    void compareReleaseNewerThanSnapshot() {
        // 发布版应高于同基线的预发布（限定符）版本
        assertTrue(JarVersion.compare("0.1.0", "0.1.0-SNAPSHOT") > 0);
        assertTrue(JarVersion.compare("0.1.0-SNAPSHOT", "0.1.0") < 0);
    }

    @Test
    void ofJarReadsManifest() throws IOException {
        Path jar = writeJar("flora-sanctum-app-9.9.9.jar", "2.0.0");
        assertEquals(Optional.of("2.0.0"), JarVersion.ofJar(jar));
    }

    @Test
    void ofJarFallsBackToFileName() throws IOException {
        // 无 manifest 版本时回退到文件名
        Path jar = writeJarNoVersion("flora-sanctum-app-1.2.3.jar");
        assertEquals(Optional.of("1.2.3"), JarVersion.ofJar(jar));
    }

    @Test
    void ofBundlePrefersPrimaryArtifact() throws IOException {
        Path primary = writeJar("flora-sanctum-app-1.0.0.jar", "1.0.0");
        Path other = writeJar("flora-sanctum-core-0.5.0.jar", "0.5.0");
        Path third = writeJar("flatlaf-3.6.jar", "3.6");
        List<Path> jars = List.of(third, other, primary);
        // 主构件优先
        assertEquals(Optional.of("1.0.0"),
                JarVersion.ofBundle(jars, "flora-sanctum-app"));
    }

    @Test
    void ofBundleFallsBackToMaxSanctum() throws IOException {
        Path a = writeJar("flora-sanctum-core-0.5.0.jar", "0.5.0");
        Path b = writeJar("flora-sanctum-kdbx-0.8.0.jar", "0.8.0");
        // 无主构件时取 flora-sanctum-* 中的最大版本
        assertEquals(Optional.of("0.8.0"),
                JarVersion.ofBundle(List.of(a, b), "flora-sanctum-app"));
    }

    private Path writeJar(String fileName, String implementationVersion) throws IOException {
        Path jar = dir.resolve(fileName);
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, implementationVersion);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            out.putNextEntry(new JarEntry("dummy.txt"));
            out.write("x".getBytes());
            out.closeEntry();
        }
        return jar;
    }

    private Path writeJarNoVersion(String fileName) throws IOException {
        Path jar = dir.resolve(fileName);
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            out.putNextEntry(new JarEntry("dummy.txt"));
            out.write("x".getBytes());
            out.closeEntry();
        }
        return jar;
    }
}
