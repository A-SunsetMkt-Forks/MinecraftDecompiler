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

package cn.maxpixel.mcdecompiler.asm;

import cn.maxpixel.mcdecompiler.Info;
import cn.maxpixel.mcdecompiler.util.Logging;
import org.objectweb.asm.*;

import java.util.logging.Level;
import java.util.logging.Logger;

// Visitor version of https://github.com/MinecraftForge/ForgeAutoRenamingTool/blob/master/src/main/java/net/minecraftforge/fart/internal/ParameterAnnotationFixer.java
public class RuntimeParameterAnnotationFixer extends ClassVisitor {
    private static final Logger LOGGER = Logging.getLogger("Runtime(In)visibleParameterAnnotations Attribute Fixer");
    private int removeCount;// = isEnum ? 2 : 1
    private String className;
    private String toProcess;

    public RuntimeParameterAnnotationFixer(ClassVisitor classVisitor) {
        super(Info.ASM_VERSION, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        if((access & Opcodes.ACC_ENUM) != 0) {
            this.removeCount = 2;
            this.toProcess = "(Ljava/lang/String;I";
            LOGGER.log(Level.FINER, "Fixing class {0} because it is an enum", name);
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if(toProcess == null && name.equals(className) && (access & (Opcodes.ACC_STATIC | Opcodes.ACC_INTERFACE)) == 0 &&
                innerName != null) {
            if(outerName == null) {
                int i = className.lastIndexOf('$');
                if(i != -1) {
                    this.removeCount = 1;
                    String s = className.substring(0, i);
                    this.toProcess = '(' + Type.getObjectType(s).getDescriptor();
                    LOGGER.log(Level.FINER, "Fixing class {0} as its name appears to be an inner class of {1}", new Object[] {name, s});
                }
            } else {
                this.removeCount = 1;
                this.toProcess = '(' + Type.getObjectType(outerName).getDescriptor();
                LOGGER.log(Level.FINER, "Fixing class {0} as its an inner class of {1}", new Object[] {name, outerName});
            }
        }
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if(toProcess != null && name.equals("<init>") && descriptor.startsWith(toProcess)) {
            int params = Type.getArgumentTypes(descriptor).length;
            return new MethodVisitor(Info.ASM_VERSION, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                private boolean processVisible;
                private boolean processInvisible;
                @Override
                public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
                    if(params == parameterCount) {
                        LOGGER.log(Level.FINEST, "Found {0} extra {1}, try removing...", new Object[] {removeCount, visible ?
                                "RuntimeVisibleParameterAnnotations" : "RuntimeInvisibleParameterAnnotations"});
                        if(visible) processVisible = true;
                        else processInvisible = true;
                        super.visitAnnotableParameterCount(parameterCount - removeCount, visible);
                    } else super.visitAnnotableParameterCount(parameterCount, visible);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                    if(processVisible && visible) {
                        if(parameter >= removeCount) {
                            return super.visitParameterAnnotation(parameter - removeCount, descriptor, true);
                        } else {
                            LOGGER.log(Level.FINEST, "Dropped an annotation(descriptor={0}) on synthetic param {1}",
                                    new Object[] {descriptor, parameter});
                            return null;
                        }
                    }
                    if(processInvisible && !visible) {
                        if(parameter >= removeCount) {
                            return super.visitParameterAnnotation(parameter - removeCount, descriptor, false);
                        } else {
                            LOGGER.log(Level.FINEST, "Dropped an annotation(descriptor={}) on synthetic param {}",
                                    new Object[] {descriptor, parameter});
                            return null;
                        }
                    }
                    return super.visitParameterAnnotation(parameter, descriptor, visible);
                }
            };
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}