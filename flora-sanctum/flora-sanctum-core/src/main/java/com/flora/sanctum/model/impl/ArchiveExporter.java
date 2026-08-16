package com.flora.sanctum.model.impl;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.vault.*;

import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.ObjectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导出加密归档（见设计 03"备份"）：把库根全部密文块文件打包为 zip。
 * 块已 AES-GCM-SIV 加密，归档保持密文；恢复即解压回库根。
 */
public final class ArchiveExporter {

    private final ObjectStore store;

    public ArchiveExporter(ObjectStore store) {
        this.store = store;
    }

    public void export(Path outZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outZip))) {
            for (Block b : store.scan()) {
                zos.putNextEntry(new ZipEntry(b.file().getFileName().toString()));
                zos.write(Files.readAllBytes(b.file()));
                zos.closeEntry();
            }
        }
    }
}
