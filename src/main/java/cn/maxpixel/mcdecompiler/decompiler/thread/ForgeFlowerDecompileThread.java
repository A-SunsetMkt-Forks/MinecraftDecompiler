/*
 * MinecraftDecompiler. A tool/library to deobfuscate and decompile Minecraft.
 * Copyright (C) 2019-2022  MaxPixelStudios
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.maxpixel.mcdecompiler.decompiler.thread;

import cn.maxpixel.mcdecompiler.util.Logging;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.ThreadedPrintStreamLogger;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.logging.Logger;

public class ForgeFlowerDecompileThread extends Thread {
    private final File[] sources;
    private final File[] libraries;
    private final File target;

    public ForgeFlowerDecompileThread(File[] sources, File[] libraries, File target) {
        super("ForgeFlower-Decompile");
        this.sources = sources;
        this.libraries = libraries;
        this.target = target;
    }

    @Override
    public void run() {
        Map<String, Object> options = Map.of(
                "log", "TRACE",
                "rsy", "1",
                "dgs", "1",
                "asc", "1",
                "bsm", "1",
                "iec", "1"
        );
        ConsoleDecompiler decompiler = new AccessibleConsoleDecompiler(target, options,
                new ThreadedPrintStreamLogger(new PrintStream(new OutputStream() {
                    private static final Logger LOGGER = Logging.getLogger("ForgeFlower");

                    @Override
                    public void write(int b) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        LOGGER.fine(new String(b, off, len).stripTrailing());
                    }
                })));
        for(File source : sources) decompiler.addSource(source);
        for(File library : libraries) decompiler.addLibrary(library);
        decompiler.decompileContext();
    }
}