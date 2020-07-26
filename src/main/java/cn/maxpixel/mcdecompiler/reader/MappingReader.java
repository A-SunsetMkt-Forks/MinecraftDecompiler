/*
 * MinecraftDecompiler. A tool/library to deobfuscate and decompile Minecraft.
 * Copyright (C) 2019-2020  MaxPixelStudios
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

import cn.maxpixel.mcdecompiler.mapping.ClassMapping;
import cn.maxpixel.mcdecompiler.mapping.FieldMapping;
import cn.maxpixel.mcdecompiler.mapping.MethodMapping;
import cn.maxpixel.mcdecompiler.mapping.PackageMapping;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class MappingReader implements AutoCloseable {
	protected BufferedReader reader;
	private final List<ClassMapping> mappings;
	private final List<PackageMapping> packages;

	protected MappingReader(BufferedReader reader) {
		this.reader = reader;
		Stream<String> lines = reader.lines().map(s -> {
			if(s.startsWith("#")) return null;

			int index = s.indexOf('#');
			if(index > 0) return s.substring(0, index - 1);

			if(s.replaceAll("\\s+", "").isEmpty()) return null;

			return s;
		}).filter(Objects::nonNull);
		NonPackageMappingProcessor processor = getProcessor();
		mappings = processor.process(lines);
		packages = processor instanceof MappingProcessor ? ((MappingProcessor) processor).processPackage(lines) : Collections.emptyList();
	}
	protected MappingReader(Reader rd) {
		this(new BufferedReader(rd));
	}
	protected MappingReader(InputStream is) {
		this(new InputStreamReader(is));
	}
	protected MappingReader(String path) throws FileNotFoundException, NullPointerException {
		this(new FileReader(Objects.requireNonNull(path)));
	}

	protected abstract NonPackageMappingProcessor getProcessor();
	public List<ClassMapping> getMappings() {
		return mappings;
	}
	public List<PackageMapping> getPackages() {
		return packages;
	}
	public Map<String, ClassMapping> getMappingsMapByObfuscatedName() {
		return getMappings().stream().collect(Collectors.toMap(ClassMapping::getObfuscatedName, Function.identity(),
				(classMapping, classMapping2) -> {throw new IllegalArgumentException("Key duplicated!");}, Object2ObjectOpenHashMap::new));
	}
	public Map<String, ClassMapping> getMappingsMapByOriginalName() {
		return getMappings().stream().collect(Collectors.toMap(ClassMapping::getOriginalName, Function.identity(),
				(classMapping, classMapping2) -> {throw new IllegalArgumentException("Key duplicated!");}, Object2ObjectOpenHashMap::new));
	}
	@Override
	public void close() {
		try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			reader = null;
		}
	}
	protected abstract static class MappingProcessor extends NonPackageMappingProcessor {
		protected abstract List<PackageMapping> processPackage(Stream<String> lines);
	}
	protected abstract static class NonPackageMappingProcessor {
		public abstract List<ClassMapping> process(Stream<String> lines);
		protected abstract ClassMapping processClass(String line);
		protected abstract MethodMapping processMethod(String line);
		protected abstract FieldMapping processField(String line);
	}
}