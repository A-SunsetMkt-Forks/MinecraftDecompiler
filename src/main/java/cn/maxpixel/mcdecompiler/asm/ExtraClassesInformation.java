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
import cn.maxpixel.mcdecompiler.util.IOUtil;
import cn.maxpixel.mcdecompiler.util.Logging;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.*;
import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ExtraClassesInformation implements Consumer<Path> {
    private static final Logger LOGGER = Logging.getLogger("Class Info Collector");
    private final Object2ObjectOpenHashMap<String, ObjectArrayList<String>> superClassMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, Object2IntOpenHashMap<String>> accessMap = new Object2ObjectOpenHashMap<>();
    private final Optional<JsonObject> refMap;

    public ExtraClassesInformation(Optional<JsonObject> refMap) {
        this.refMap = refMap;
    }

    public ExtraClassesInformation(Optional<JsonObject> refMap, Stream<Path> classes) {
        this(refMap, classes, false);
    }

    public ExtraClassesInformation(Optional<JsonObject> refMap, Stream<Path> classes, boolean close) {
        this.refMap = refMap;
        if(close) try(classes) {
            classes.forEach(this);
        } else classes.forEach(this);
    }

    @Override
    public void accept(Path classFilePath) {
        try {
            ClassReader reader = new ClassReader(IOUtil.readAllBytes(classFilePath));
            String className = reader.getClassName();
            boolean needToRecord = (reader.getAccess() & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_RECORD)) == 0;
            String superName = reader.getSuperName();
            String[] interfaces = reader.getInterfaces();
            int itfLen = interfaces.length;
            if(needToRecord && !superName.startsWith("java/")) {
                ObjectArrayList<String> list = new ObjectArrayList<>(itfLen + 1);
                list.add(superName);
                if(itfLen > 0) for(String itf : interfaces) {
                    if(itf.startsWith("java/")) continue;
                    list.add(itf);
                }
                synchronized(superClassMap) {
                    superClassMap.put(className, list);
                }
            } else if(itfLen > 0) {
                ObjectArrayList<String> list = new ObjectArrayList<>(itfLen);
                for(String itf : interfaces) {
                    if(itf.startsWith("java/")) continue;
                    list.add(itf);
                }
                synchronized(superClassMap) {
                    superClassMap.put(className, list);
                }
            }
            reader.accept(new ClassVisitor(Info.ASM_VERSION) {
                private final Object2IntOpenHashMap<String> map = needToRecord ? new Object2IntOpenHashMap<>() : null;

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor)) {
                        ObjectArrayList<String> list = superClassMap.computeIfAbsent(className, s -> new ObjectArrayList<>());
                        return new AnnotationVisitor(api) {
                            @Override
                            public AnnotationVisitor visitArray(String name) {
                                if ("value".equals(name)) {
                                    return new AnnotationVisitor(api) {
                                        @Override
                                        public void visit(String name, Object value) {
                                            if (value instanceof Type t && t.getSort() == Type.OBJECT) {
                                                list.add(t.getInternalName());
                                            }
                                        }
                                    };
                                } else if ("targets".equals(name)) {
                                    return new AnnotationVisitor(api) {
                                        @Override
                                        public void visit(String name, Object value) {
                                            if (value instanceof String s) {
                                                list.add(refMap.map(obj -> obj.getAsJsonObject(className)
                                                        .get(s).getAsString()).orElse(name));
                                            }
                                        }
                                    };
                                }
                                return null;
                            }
                        };
                    }
                    return null;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if(needToRecord && (access & Opcodes.ACC_PUBLIC) == 0) map.put(name, access);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if(needToRecord && (access & Opcodes.ACC_PUBLIC) == 0) map.put(name.concat(descriptor), access);
                    return null;
                }

                @Override
                public void visitEnd() {
                    if(needToRecord && !map.isEmpty()) {
                        map.defaultReturnValue(Opcodes.ACC_PUBLIC);
                        synchronized(accessMap) {
                            accessMap.put(className, map);
                        }
                    }
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch(IOException e) {
            LOGGER.log(Level.WARNING, "Error when creating super class mapping", e);
        }
    }

    public ObjectList<String> getSuperNames(String name) {
        return superClassMap.get(name);
    }

    public int getAccessFlags(String className, String combinedMemberName) {
        Object2IntMap<String> map = accessMap.get(className);
        if(map == null) return Opcodes.ACC_PUBLIC;
        return map.getInt(combinedMemberName);
    }
}