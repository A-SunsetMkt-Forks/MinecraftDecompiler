/*
 * MinecraftDecompiler. A tool/library to deobfuscate and decompile jars.
 * Copyright (C) 2019-2024 MaxPixelStudios(XiaoPangxie732)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.maxpixel.mcdecompiler.remapper.variable;

import cn.maxpixel.mcdecompiler.remapper.Deobfuscator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.RecordComponentVisitor;

/**
 * @apiNote Use this when {@code (access & Opcodes.ACC_RECORD) != 0}
 */
public class RecordNameRemapper extends ClassVisitor implements VariableNameProvider {
    private final ObjectArrayList<String> recordNames = new ObjectArrayList<>();
    private final StringBuilder recordDesc = new StringBuilder("(");
    private boolean done;

    public RecordNameRemapper(ClassVisitor classVisitor) {
        super(Deobfuscator.ASM_VERSION, classVisitor);
    }

    @Override
    public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        recordNames.add(name);
        recordDesc.append(descriptor);
        return super.visitRecordComponent(name, descriptor, signature);
    }

    @Override
    public @NotNull RenameFunction forMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("<init>")) {
            if (!done) {
                recordDesc.append(")V");
                done = true;
            }
            if (descriptor.contentEquals(recordDesc)) {
                return new RenameFunction() {
                    private int i;

                    @Override
                    public @Nullable String getName(String originalName, String descriptor, String signature, Label start, Label end, int index) {
                        if (index > 0 && i < recordNames.size()) {
                            return recordNames.get(i++);
                        }
                        return null;
                    }
                };
            }
        }
        return RenameFunction.NOP;
    }
}