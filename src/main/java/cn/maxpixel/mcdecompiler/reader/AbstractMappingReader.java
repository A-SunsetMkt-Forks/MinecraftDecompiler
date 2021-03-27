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

package cn.maxpixel.mcdecompiler.reader;

import cn.maxpixel.mcdecompiler.asm.MappingRemapper;
import cn.maxpixel.mcdecompiler.mapping.ClassMapping;
import cn.maxpixel.mcdecompiler.mapping.PackageMapping;
import cn.maxpixel.mcdecompiler.mapping.base.BaseFieldMapping;
import cn.maxpixel.mcdecompiler.mapping.base.BaseMethodMapping;
import cn.maxpixel.mcdecompiler.util.Utils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractMappingReader implements AutoCloseable {
    protected BufferedReader reader;
    private List<? extends ClassMapping> mappings;
    private List<PackageMapping> packages;

    protected AbstractMappingReader(BufferedReader reader) {
        this.reader = reader;
    }
    protected AbstractMappingReader(Reader rd) {
        this(new BufferedReader(Objects.requireNonNull(rd)));
    }
    protected AbstractMappingReader(InputStream is) {
        this(new InputStreamReader(Objects.requireNonNull(is), StandardCharsets.UTF_8));
    }
    protected AbstractMappingReader(String path) throws FileNotFoundException, NullPointerException {
        this(new FileReader(Objects.requireNonNull(path)));
    }

    protected abstract AbstractNonPackageMappingProcessor getProcessor();

    private void read() {
        AbstractNonPackageMappingProcessor processor = getProcessor();
        mappings = processor.process(reader.lines().map(s -> {
            if(s.startsWith("#") || s.isEmpty() || s.replaceAll("\\s+", "").isEmpty()) return null;

            int index = s.indexOf('#');
            if(index > 0) return s.substring(0, index);
            else if(index == 0) return null;

            return s;
        }).filter(Objects::nonNull));
        packages = processor instanceof AbstractMappingProcessor ? ((AbstractMappingProcessor) processor).getPackages() : ObjectLists.emptyList();
    }

    public final List<? extends ClassMapping> getMappings() {
        if(mappings == null) read();
        return mappings;
    }

    public final List<PackageMapping> getPackages() {
        if(packages == null) read();
        return packages;
    }

    public final AbstractMappingReader reverse() {
        if(mappings == null || packages == null) read();
        if(this instanceof TinyMappingReader) throw new UnsupportedOperationException();
        MappingRemapper remapper = new MappingRemapper(this);
        mappings.forEach(cm -> cm.reverse(remapper));
        packages.forEach(PackageMapping::reverse);
        return this;
    }

    public final Object2ObjectOpenHashMap<String, ? extends ClassMapping> getMappingsByUnmappedNameMap() {
        return getMappings().stream().collect(Collectors.toMap(ClassMapping::getUnmappedName, Function.identity(), (cm1, cm2) ->
        {throw new IllegalArgumentException("Key \"" + cm1 + "\" and \"" + cm2 + "\" duplicated!");}, Object2ObjectOpenHashMap::new));
    }

    public final Object2ObjectOpenHashMap<String, ? extends ClassMapping> getMappingsByMappedNameMap() {
        return getMappings().stream().collect(Collectors.toMap(ClassMapping::getMappedName, Function.identity(), (cm1, cm2) ->
        {throw new IllegalArgumentException("Key \"" + cm1 + "\" and \"" + cm2 + "\" duplicated!");}, Object2ObjectOpenHashMap::new));
    }

    @Override
    public final void close() {
        try {
            reader.close();
        } catch (IOException e) {
            throw Utils.wrapInRuntime(e);
        } finally {
            reader = null;
        }
    }

    protected abstract static class AbstractNonPackageMappingProcessor {
        abstract ObjectList<? extends ClassMapping> process(Stream<String> lines);
        abstract ClassMapping processClass(String line);
        abstract BaseMethodMapping processMethod(String line);
        abstract BaseFieldMapping processField(String line);
    }

    protected abstract static class AbstractMappingProcessor extends AbstractNonPackageMappingProcessor {
        abstract ObjectList<PackageMapping> getPackages();
        abstract PackageMapping processPackage(String line);
    }
}