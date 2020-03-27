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

package cn.maxpixel.mcdecompiler.deobfuscator;

import cn.maxpixel.mcdecompiler.*;
import cn.maxpixel.mcdecompiler.mapping.ClassMapping;
import cn.maxpixel.mcdecompiler.reader.ProguardMappingReader;
import cn.maxpixel.mcdecompiler.remapper.ProguardMappingRemapper;
import cn.maxpixel.mcdecompiler.asm.SuperClassMapping;
import cn.maxpixel.mcdecompiler.util.FileUtil;
import cn.maxpixel.mcdecompiler.util.JarUtil;
import cn.maxpixel.mcdecompiler.util.NamingUtil;
import cn.xiaopangxie732.easynetwork.coder.ByteDecoder;
import cn.xiaopangxie732.easynetwork.http.HttpConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class ProguardDeobfuscator extends AbstractDeobfuscator {
	private String version;
	private Info.SideType type;
	private static JsonArray versions;
	private JsonObject version_json;
	static {
		versions = JsonParser.parseString(ByteDecoder.decodeToString(HttpConnection
				.newGetConnection("https://launchermeta.mojang.com/mc/game/version_manifest.json", DeobfuscatorCommandLine.PROXY)))
				.getAsJsonObject().get("versions").getAsJsonArray();
	}
	public ProguardDeobfuscator(String version, Info.SideType type) {
		this.version = Objects.requireNonNull(version);
		this.type = Objects.requireNonNull(type);
		downloadMapping();
		downloadJar();
	}
	private ProguardDeobfuscator downloadMapping() {
		checkVersion();
		File f = new File(InfoProviders.get().getMappingPath(version, type));
		f.getParentFile().mkdirs();
		if(!f.exists()) {
			LOGGER.info("downloading mapping...");
			try(FileOutputStream fout = new FileOutputStream(f)) {
				f.createNewFile();
				fout.write(HttpConnection.newGetConnection(
						version_json.get("downloads").getAsJsonObject().get(type.toString() + "_mappings").getAsJsonObject().get("url").getAsString(),
						DeobfuscatorCommandLine.PROXY));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return this;
	}
	private void checkVersion() {
		LOGGER.info("checking version...");
		for (JsonElement element : versions) {
			JsonObject object = element.getAsJsonObject();
			if(object.get("id").getAsString().equalsIgnoreCase(version)) {
				version_json = JsonParser.parseString(ByteDecoder.decodeToString(HttpConnection
						.newGetConnection(object.get("url").getAsString(), DeobfuscatorCommandLine.PROXY))).getAsJsonObject();
				if (version_json.get("downloads").getAsJsonObject().has(type.toString() + "_mappings")) break;
				else throw new RuntimeException("This version doesn't have mappings");
			}
		}
		if(version_json == null) throw new RuntimeException("INVALID VERSION DETECTED: " + version);
	}
	private ProguardDeobfuscator downloadJar() {
		File f = new File(InfoProviders.get().getMcJarPath(version, type));
		f.getParentFile().mkdirs();
		if(!f.exists()) {
			LOGGER.info("downloading jar...");
			try(FileOutputStream fout = new FileOutputStream(f)) {
				f.createNewFile();
				fout.write(HttpConnection.newGetConnection(
						version_json.get("downloads").getAsJsonObject().get(type.toString()).getAsJsonObject().get("url").getAsString(),
						DeobfuscatorCommandLine.PROXY));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return this;
	}

	@Override
	public ProguardDeobfuscator deobfuscate() {
		try {
			LOGGER.info("deobfuscating...");
			File deobfuscateJar = new File(InfoProviders.get().getDeobfuscateJarPath(version, type));
			deobfuscateJar.getParentFile().mkdirs();
			deobfuscateJar.createNewFile();
			new File(InfoProviders.get().getTempPath()).mkdirs();
			File originalClasses = new File(InfoProviders.get().getTempOriginalClassesPath(version, type));
			originalClasses.mkdirs();
			JarUtil.decompressJar(InfoProviders.get().getMcJarPath(version, type), originalClasses);
			LOGGER.info("remapping...");
			try(ProguardMappingReader mappingReader = new ProguardMappingReader(InfoProviders.get().getMappingPath(version, type))) {
				SuperClassMapping superClassMapping = new SuperClassMapping();
				listMcClassFiles(originalClasses, path -> {
					try(InputStream inputStream = Files.newInputStream(path.toPath())) {
						ClassReader reader = new ClassReader(inputStream);
						reader.accept(superClassMapping, ClassReader.SKIP_DEBUG);
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
				ProguardMappingRemapper remapper = new ProguardMappingRemapper(mappingReader.getMappingsMapByObfuscatedName(), mappingReader.getMappingsMapByOriginalName(), superClassMapping);
				Map<String, ClassMapping> mappings = mappingReader.getMappingsMapByObfuscatedName();
				Files.createDirectories(Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type)));
				listMcClassFiles(originalClasses, path -> {
					try(InputStream inputStream = Files.newInputStream(path.toPath())) {
						ClassReader reader = new ClassReader(inputStream);
						ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
						reader.accept(new ClassRemapper(writer, remapper), ClassReader.SKIP_DEBUG);
						String mappingKey;
						if(path.getPath().contains("minecraft" + Info.FILE_SEPARATOR)) {
							mappingKey = NamingUtil.asJavaName("net/minecraft" + path.getPath().substring(path.getPath().
									lastIndexOf(Info.FILE_SEPARATOR, 48)));
						} else if(path.getPath().contains("mojang" + Info.FILE_SEPARATOR)) {
							mappingKey = NamingUtil.asJavaName("com/mojang" + path.getPath().substring(path.getPath().
									lastIndexOf(Info.FILE_SEPARATOR, 45)));
						} else {
							mappingKey = NamingUtil.asJavaName(path.getName());
						}
						ClassMapping mapping = mappings.get(mappingKey);
						if(mapping != null) {
							String s = NamingUtil.asNativeName(mapping.getOriginalName());
							Files.createDirectories(Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type), s.substring(0, s.lastIndexOf('/'))));
							Files.write(Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type), s + ".class"), writer.toByteArray(),
									StandardOpenOption.CREATE, StandardOpenOption.WRITE);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
			}
			copyOthers(originalClasses);
			String mainClass = type == Info.SideType.CLIENT ? "net.minecraft.client.main.Main" : "net.minecraft.server.MinecraftServer";
			JarUtil.compressJar(mainClass, deobfuscateJar, new File(InfoProviders.get().getTempRemappedClassesPath(version, type)));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}
	private void copyOthers(File baseDir) {
		for(File childFile : Objects.requireNonNull(baseDir.listFiles())) {
			if(childFile.isFile() && !childFile.getPath().endsWith(".class")) {
				FileUtil.copyFile(childFile.toPath(), Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type), childFile.getName()));
			} else if(childFile.isDirectory() && !childFile.getAbsolutePath().contains("net")
					&& !childFile.getAbsolutePath().contains("blaze3d") && !childFile.getAbsolutePath().contains("realmsclient")) {
				FileUtil.copyDirectory(childFile.toPath(), Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type)));
			}
		}
		File manifest = Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type), "META-INF", "MANIFEST.MF").toFile();
		if(manifest.exists()) manifest.delete();
		File mojangRSA = Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type), "META-INF", "MOJANGCS.RSA").toFile();
		if(mojangRSA.exists()) mojangRSA.delete();
		File mojangSF = Paths.get(InfoProviders.get().getTempRemappedClassesPath(version, type), "META-INF", "MOJANGCS.SF").toFile();
		if(mojangSF.exists()) mojangSF.delete();
	}
	private void listMcClassFiles(File baseDir, Consumer<File> fileConsumer) {
		for(File childFile : Objects.requireNonNull(baseDir.listFiles())) {
			if(childFile.isFile() && childFile.getPath().endsWith(".class")) fileConsumer.accept(childFile);
		}
		for(File minecraft : Objects.requireNonNull(new File(baseDir, "net/minecraft").listFiles())) {
			if (minecraft.isDirectory()) processNetDotMinecraftPackage(minecraft, fileConsumer);
		}
		if(type == Info.SideType.CLIENT) {
			for(File mojang : Objects.requireNonNull(new File(baseDir, "com/mojang").listFiles())) {
				if (mojang.isDirectory()) processNetDotMinecraftPackage(mojang, fileConsumer);
			}
		}
	}
	private void processNetDotMinecraftPackage(File dir, Consumer<File> fileConsumer) {
		for(File f : Objects.requireNonNull(dir.listFiles())) {
			if(f.isFile()) {
				fileConsumer.accept(f);
			} else if(f.isDirectory()) {
				processNetDotMinecraftPackage(f, fileConsumer);
			}
		}
	}
}