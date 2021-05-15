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

package cn.maxpixel.mcdecompiler.deobfuscator;

import cn.maxpixel.mcdecompiler.asm.LVTRenamer;
import cn.maxpixel.mcdecompiler.asm.MappingRemapper;
import cn.maxpixel.mcdecompiler.reader.TinyMappingReader;
import cn.maxpixel.mcdecompiler.reader.TsrgMappingReader;

import java.nio.file.Path;

public class TsrgDeobfuscator extends AbstractDeobfuscator {
    public TsrgDeobfuscator(String mappingPath) {
        super(mappingPath);
    }
    @Override
    public TsrgDeobfuscator deobfuscate(Path source, Path target, boolean includeOthers, boolean reverse) {
        try {
            TsrgMappingReader mappingReader = new TsrgMappingReader(mappingPath);
            if(mappingReader.version == 2) {
                String[] namespaces = mappingReader.getProcessor().asNamespaced().getNamespaces();
                boolean flag = namespaces[namespaces.length - 1].equals("id");
                sharedDeobfuscate(source, target, mappingReader, includeOthers, false, parent -> new LVTRenamer(parent,
                        mappingReader.getMappingsByNamespaceMap(namespaces[0]), namespaces[0], flag ? namespaces[namespaces.length - 2] :
                        namespaces[namespaces.length - 1]), (reader, superClassMapping) -> new MappingRemapper(reader, superClassMapping,
                        namespaces[0], flag ? namespaces[namespaces.length - 2] : namespaces[namespaces.length - 1]));
            } else sharedDeobfuscate(source, target, mappingReader, includeOthers, reverse);
        } catch (Exception e) {
            LOGGER.error("Error when deobfuscating", e);
        }
        return this;
    }

    public TsrgDeobfuscator deobfuscate(Path source, Path target, boolean includeOthers, String fromNamespace, String toNamespace) {
        try {
            TinyMappingReader mappingReader = new TinyMappingReader(mappingPath);
            if(mappingReader.version != 2) throw new UnsupportedOperationException();
            sharedDeobfuscate(source, target, mappingReader, includeOthers, false, parent -> new LVTRenamer(parent,
                            mappingReader.getMappingsByNamespaceMap(fromNamespace), fromNamespace, toNamespace),
                    (reader, superClassMapping) -> new MappingRemapper(reader, superClassMapping, fromNamespace, toNamespace));
        } catch (Exception e) {
            LOGGER.error("Error when deobfuscating", e);
        }
        return this;
    }
}