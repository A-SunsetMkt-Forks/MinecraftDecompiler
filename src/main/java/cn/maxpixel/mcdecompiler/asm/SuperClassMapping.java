/*
 * MinecraftDecompiler. A tool/library to deobfuscate and decompile Minecraft.
 * Copyright (C) 2019-2021  MaxPixelStudios
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

import cn.maxpixel.mcdecompiler.util.NamingUtil;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class SuperClassMapping extends ClassVisitor {
    private final Object2ObjectMap<String, ObjectArrayList<String>> superClassMap = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
    public SuperClassMapping() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        ObjectArrayList<String> list = new ObjectArrayList<>();
        if(!superName.equals("java/lang/Object")) {
            list.add(NamingUtil.asJavaName0(superName));
        }
        if(interfaces != null) for(String interface_ : interfaces) list.add(NamingUtil.asJavaName0(interface_));
        if(!list.isEmpty()) {
            superClassMap.put(NamingUtil.asJavaName0(name), list);
        }
    }

    public Object2ObjectMap<String, ObjectArrayList<String>> getMap() {
        return Object2ObjectMaps.unmodifiable(superClassMap);
    }
}