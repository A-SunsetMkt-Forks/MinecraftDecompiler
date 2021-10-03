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

package cn.maxpixel.mcdecompiler.util;

import cn.maxpixel.mcdecompiler.mapping1.NamespacedMapping;
import cn.maxpixel.mcdecompiler.mapping1.component.Descriptor;
import cn.maxpixel.mcdecompiler.reader.ClassifiedMappingReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NamingUtil {
    private static final Pattern ARR_DIM = Pattern.compile("\\[]");

    public static int getDimension(String javaName) {
        Matcher m = ARR_DIM.matcher(javaName);
        int arrDimension = 0;
        while(m.find()) arrDimension++;
        return arrDimension;
    }

    public static String findSourceNamespace(ClassifiedMappingReader<NamespacedMapping> mappingReader) {
        return mappingReader.mappings.parallelStream()
                .flatMap(cm -> cm.getMethods().parallelStream())
                .map(Descriptor.Namespaced.class::cast)
                .map(Descriptor.Namespaced::getDescriptorNamespace)
                .findAny().orElseThrow(IllegalArgumentException::new);
    }

    public static String asJavaName(String pureNativeName) {
        return pureNativeName.replace('/', '.');
    }

    public static String asNativeName(String javaName) {
        return javaName.replace('.', '/');
    }

    public static String asNativeName0(String fileName) {
        return fileName.replace('\\', '/').replace(".class", "");
    }

    public static String asDescriptor(String javaName) {
        if(javaName == null || javaName.isEmpty()) return "";
        if(!javaName.contains("[]")) {
            return switch(javaName) {
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "double" -> "D";
                case "float" -> "F";
                case "int" -> "I";
                case "long" -> "J";
                case "short" -> "S";
                case "void" -> "V";
                default -> 'L' + asNativeName(javaName) + ';';
            };
        } else {
            String dim = "[".repeat(getDimension(javaName));
            javaName = javaName.replace("[]", "");
            return switch(javaName) {
                case "boolean" -> dim + 'Z';
                case "byte" -> dim + 'B';
                case "char" -> dim + 'C';
                case "double" -> dim + 'D';
                case "float" -> dim + 'F';
                case "int" -> dim + 'I';
                case "long" -> dim + 'J';
                case "short" -> dim + 'S';
                case "void" -> dim + 'V';
                default -> dim + 'L' + asNativeName(javaName) + ';';
            };
        }
    }
}