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

package cn.maxpixel.mcdecompiler.remapper.processing;

import cn.maxpixel.mcdecompiler.remapper.Deobfuscator;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class IndyRemapper extends ClassVisitor {
    private final ClassFileRemapper mappingRemapper;

    public IndyRemapper(ClassVisitor classVisitor, ClassFileRemapper mappingRemapper) {
        super(Deobfuscator.ASM_VERSION, classVisitor);
        this.mappingRemapper = mappingRemapper;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new Remapper(super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    public class Remapper extends MethodVisitor {
        public Remapper(MethodVisitor methodVisitor) {
            super(Deobfuscator.ASM_VERSION, methodVisitor);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            // Only remap name here. Other things are remapped by ASM's ClassRemapper
            if (bootstrapMethodHandle.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
                Type interfaceMethodType = (Type) bootstrapMethodArguments[0];
                name = mappingRemapper.mapMethodName(Type.getReturnType(descriptor).getInternalName(), name, interfaceMethodType.getDescriptor());
            }
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }
    }
}