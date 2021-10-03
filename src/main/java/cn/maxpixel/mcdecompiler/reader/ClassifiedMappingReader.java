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

import cn.maxpixel.mcdecompiler.asm.ClassifiedMappingRemapper;
import cn.maxpixel.mcdecompiler.mapping1.ClassMapping;
import cn.maxpixel.mcdecompiler.mapping1.Mapping;
import cn.maxpixel.mcdecompiler.mapping1.NamespacedMapping;
import cn.maxpixel.mcdecompiler.mapping1.PairedMapping;
import cn.maxpixel.mcdecompiler.util.NamingUtil;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;

public class ClassifiedMappingReader<M extends Mapping> extends AbstractMappingReader<M, ObjectList<ClassMapping<M>>, MappingProcessor.Classified<M>> {
    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, BufferedReader reader) {
        super(processor, reader);
    }

    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, Reader rd) {
        super(processor, rd);
    }

    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, InputStream is) {
        super(processor, is);
    }

    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, String path) throws FileNotFoundException {
        super(processor, path);
    }

    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, BufferedReader... readers) {
        super(processor, readers);
    }

    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, Reader... rd) {
        super(processor, rd);
    }

    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, InputStream... is) {
        super(processor, is);
    }

    public ClassifiedMappingReader(MappingProcessor.Classified<M> processor, String... path) throws FileNotFoundException {
        super(processor, path);
    }

    public static ClassifiedMappingReader<PairedMapping> reverse(ClassifiedMappingReader<PairedMapping> reader) {
        ClassifiedMappingRemapper remapper = new ClassifiedMappingRemapper(reader);
        reader.mappings.forEach(cm -> ClassMapping.reverse(cm, remapper));
        reader.packages.forEach(PairedMapping::reverse);
        return reader;
    }

    public static ClassifiedMappingReader<NamespacedMapping> swap(ClassifiedMappingReader<NamespacedMapping> reader, String targetNamespace) {
        String sourceNamespace = NamingUtil.findSourceNamespace(reader);
        ClassifiedMappingRemapper remapper = new ClassifiedMappingRemapper(reader, sourceNamespace, targetNamespace);
        reader.mappings.forEach(cm -> ClassMapping.swap(cm, remapper, sourceNamespace, targetNamespace));
        reader.packages.forEach(m -> m.swap(sourceNamespace, targetNamespace));
        return reader;
    }
}