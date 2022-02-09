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

package cn.maxpixel.mcdecompiler;

import cn.maxpixel.mcdecompiler.decompiler.Decompilers;
import cn.maxpixel.mcdecompiler.decompiler.IDecompiler;
import cn.maxpixel.mcdecompiler.decompiler.IExternalResourcesDecompiler;
import cn.maxpixel.mcdecompiler.decompiler.ILibRecommendedDecompiler;
import cn.maxpixel.mcdecompiler.mapping.Mapping;
import cn.maxpixel.mcdecompiler.mapping.NamespacedMapping;
import cn.maxpixel.mcdecompiler.mapping.PairedMapping;
import cn.maxpixel.mcdecompiler.mapping.type.MappingType;
import cn.maxpixel.mcdecompiler.reader.ClassifiedMappingReader;
import cn.maxpixel.mcdecompiler.util.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

public class MinecraftDecompiler {
    private static final Logger LOGGER = Logging.getLogger();
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .proxy(ProxySelector.of((InetSocketAddress) MinecraftDecompilerCommandLine.INTERNAL_PROXY.address()))
            .executor(ForkJoinPool.commonPool())
            .connectTimeout(Duration.ofSeconds(10L))
            .build();

    private final Options options;
    private final ClassifiedDeobfuscator deobfuscator;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtil.deleteIfExists(Properties.TEMP_DIR)));
    }

    public MinecraftDecompiler(Options options) {
        this.options = options;
        this.deobfuscator = options.buildDeobfuscator();
    }

    public void deobfuscate() {
        try {
            deobfuscator.deobfuscate(options.inputJar(), options.outputJar(), options);
        } catch(IOException e) {
            LOGGER.log(Level.SEVERE, "Error deobfuscating", e);
        }
    }

    public void decompile(Info.DecompilerType decompilerType) {
        if(Files.notExists(options.outputJar())) deobfuscate();
        LOGGER.log(Level.INFO, "Decompiling using \"{0}\"", decompilerType);
        decompile0(Decompilers.get(decompilerType), options.outputJar(), options.outputDecompDir());
    }

    public void decompileCustomized(String customizedDecompilerName) {
        if(Files.notExists(options.outputJar())) deobfuscate();
        LOGGER.log(Level.INFO, "Decompiling using customized decompiler \"{0}\"", customizedDecompilerName);
        decompile0(Decompilers.getCustom(customizedDecompilerName), options.outputJar(), options.outputDecompDir());
    }

    private void decompile0(IDecompiler decompiler, Path inputJar, Path outputDir) {
        try(FileSystem jarFs = JarUtil.getJarFileSystemProvider().newFileSystem(inputJar, Object2ObjectMaps.emptyMap())) {
            FileUtil.deleteIfExists(outputDir);
            Files.createDirectories(outputDir);
            Path libDownloadPath = Properties.DOWNLOAD_DIR.resolve("libs").toAbsolutePath().normalize();
            FileUtil.ensureDirectoryExist(libDownloadPath);
            if(decompiler instanceof IExternalResourcesDecompiler erd)
                erd.extractTo(Properties.TEMP_DIR.toAbsolutePath().normalize());
            if(decompiler instanceof ILibRecommendedDecompiler lrd) {
                if(options.bundledLibs().isPresent()) lrd.receiveLibs(options.bundledLibs().get());
                else if(options.version() != null) lrd.downloadLib(libDownloadPath, options.version());
            }
            switch (decompiler.getSourceType()) {
                case DIRECTORY -> {
                    Path decompileClasses = Properties.TEMP_DIR.resolve("decompileClasses").toAbsolutePath().normalize();
                    FileUtil.copyDirectory(jarFs.getPath("/net"), decompileClasses);
                    if(options.bundledLibs().isEmpty()) {
                        try(Stream<Path> mjDirs = Files.list(jarFs.getPath("/com", "mojang")).filter(p ->
                                !(p.endsWith("authlib") || p.endsWith("bridge") || p.endsWith("brigadier") || p.endsWith("datafixers") ||
                                        p.endsWith("serialization") || p.endsWith("util")))) {
                            mjDirs.forEach(p -> FileUtil.copyDirectory(p, decompileClasses));
                        }
                    }
                    decompiler.decompile(decompileClasses, outputDir);
                }
                case FILE -> decompiler.decompile(inputJar, outputDir);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error when decompiling", e);
        }
    }

    public static class OptionBuilder {
        private static final Logger LOGGER = Logging.getLogger("Option Builder");
        private String version;
        private Info.SideType type;
        private boolean includeOthers = true;
        private boolean rvn;
        private BufferedReader inputMappings;
        private Path outputJar;
        private Path outputDecompDir;
        private final ObjectArrayList<Path> extraJars = new ObjectArrayList<>();

        private Path inputJar;
        private boolean reverse;

        private String targetNamespace;

        private Optional<ObjectList<Path>> bundledLibs = Optional.empty();

        public OptionBuilder(String version, Info.SideType type) {
            this.version = Objects.requireNonNull(version, "version cannot be null!");
            this.type = Objects.requireNonNull(type, "type cannot be null!");
            preprocess(downloadJar(version, type));
            this.outputJar = Path.of("output", version + "_" + type + "_deobfuscated.jar").toAbsolutePath().normalize();
            this.outputDecompDir = Path.of("output", version + "_" + type + "_decompiled").toAbsolutePath().normalize();
        }

        public OptionBuilder(Path inputJar) {
            this(inputJar, false);
        }

        public OptionBuilder(Path inputJar, boolean reverse) {
            preprocess(inputJar);
            this.reverse = reverse;
            this.outputJar = Path.of("output", "deobfuscated.jar").toAbsolutePath().normalize();
            this.outputDecompDir = Path.of("output", "decompiled").toAbsolutePath().normalize();
        }

        private void preprocess(Path inputJar) {
            FileUtil.deleteIfExists(Properties.TEMP_DIR);
            try {
                Files.createDirectories(Properties.TEMP_DIR);
            } catch(IOException e) {
                LOGGER.severe("Error creating temp directory");
                throw Utils.wrapInRuntime(e);
            }
            try(FileSystem jarFs = JarUtil.createZipFs(FileUtil.requireExist(inputJar))) {
                if(Files.exists(jarFs.getPath("/net/minecraft/bundler/Main.class"))) {
                    Path metaInf = jarFs.getPath("META-INF");
                    Path extractDir = FileUtil.ensureDirectoryExist(Properties.TEMP_DIR.resolve("bundleExtract"));
                    List<String> jar = Files.readAllLines(metaInf.resolve("versions.list"));
                    if(jar.size() == 1) {
                        Path versionPath = metaInf.resolve("versions").resolve(jar.get(0).split("\t")[2]);
                        FileUtil.copyFile(versionPath, extractDir);
                        this.inputJar = extractDir.resolve(versionPath.getFileName().toString());
                    } else throw new IllegalArgumentException("Why multiple versions in a bundle?");
                    ObjectArrayList<Path> libs = new ObjectArrayList<>();
                    Files.lines(metaInf.resolve("libraries.list")).forEach(line -> {
                        Path lib = metaInf.resolve("libraries").resolve(line.split("\t")[2]);
                        FileUtil.copyFile(lib, extractDir);
                        libs.add(extractDir.resolve(lib.getFileName().toString()));
                    });
                    this.bundledLibs = Optional.of(libs);
                } else this.inputJar = inputJar;
                Path versionJson = jarFs.getPath("/version.json");
                if(version == null && Files.exists(versionJson)) {
                    JsonObject object = JsonParser.parseString(Files.readString(versionJson)).getAsJsonObject();
                    this.version = object.get("id").getAsString();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error opening jar file {0}", new Object[] {inputJar, e});
                throw Utils.wrapInRuntime(e);
            }
        }

        private static Path downloadJar(String version, Info.SideType type) {
            Path p = Properties.DOWNLOAD_DIR.resolve(version).resolve(type + ".jar");
            if(Files.notExists(p)) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(
                            URI.create(VersionManifest.get(version)
                                    .get("downloads")
                                    .getAsJsonObject()
                                    .get(type.toString())
                                    .getAsJsonObject()
                                    .get("url")
                                    .getAsString()
                            )).build();
                    LOGGER.info("Downloading jar...");
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(FileUtil.ensureFileExist(p), WRITE, TRUNCATE_EXISTING));
                } catch(IOException | InterruptedException e) {
                    LOGGER.severe("Error downloading Minecraft jar");
                    throw Utils.wrapInRuntime(e);
                }
            }
            return p;
        }

        public OptionBuilder libsUsing(String version) {
            if(this.version != null) throw new IllegalArgumentException("version already defined, do not define it twice");
            this.version = Objects.requireNonNull(version, "version cannot be null!");
            return this;
        }

        public OptionBuilder withMapping(String inputMappings) {
            try {
                return withMapping(Files.newInputStream(Path.of(inputMappings)));
            } catch (IOException e) {
                LOGGER.severe("Error opening mapping file");
                throw Utils.wrapInRuntime(e);
            }
        }

        public OptionBuilder withMapping(InputStream inputMappings) {
            return withMapping(new InputStreamReader(inputMappings));
        }

        public OptionBuilder withMapping(Reader inputMappings) {
            return withMapping(IOUtil.asBufferedReader(inputMappings, "inputMappings"));
        }

        public OptionBuilder withMapping(BufferedReader inputMappings) {
            this.inputMappings = Objects.requireNonNull(inputMappings, "inputMappings cannot be null");
            return this;
        }

        public OptionBuilder output(Path outputJar) {
            this.outputJar = Objects.requireNonNull(outputJar, "outputJar cannot be null").toAbsolutePath().normalize();
            return this;
        }

        public OptionBuilder outputDecomp(Path outputDecompDir) {
            this.outputDecompDir = Objects.requireNonNull(outputDecompDir, "outputDecompDir cannot be null").toAbsolutePath().normalize();
            return this;
        }

        public OptionBuilder targetNamespace(String targetNamespace) {
            this.targetNamespace = Objects.requireNonNull(targetNamespace, "targetNamespace cannot be null");
            return this;
        }

        public OptionBuilder doNotIncludeOthers() {
            this.includeOthers = false;
            return this;
        }

        public OptionBuilder regenerateVariableNames() {
            this.rvn = true;
            return this;
        }

        public OptionBuilder addExtraJar(Path jar) {
            this.extraJars.add(jar);
            return this;
        }

        public OptionBuilder addExtraJars(Collection<Path> jars) {
            this.extraJars.addAll(jars);
            return this;
        }

        public OptionBuilder addExtraJars(ObjectList<Path> jars) {
            this.extraJars.addAll(jars);
            return this;
        }

        public Options build() {
            if(this.outputJar.getParent().equals(this.outputDecompDir))
                throw new IllegalArgumentException("The parent directory of outputJar cannot be the same as outputDecomp");
            return new Options() {
                @Override
                public String version() {
                    return version;
                }

                @Override
                public Info.SideType type() {
                    return type;
                }

                @Override
                public boolean includeOthers() {
                    return includeOthers;
                }

                @Override
                public boolean rvn() {
                    return rvn;
                }

                @Override
                public BufferedReader inputMappings() {
                    return inputMappings;
                }

                @Override
                public Path inputJar() {
                    return inputJar;
                }

                @Override
                public Path outputJar() {
                    return outputJar;
                }

                @Override
                public Path outputDecompDir() {
                    return outputDecompDir;
                }

                @Override
                public boolean reverse() {
                    return reverse;
                }

                @Override
                public String targetNamespace() {
                    return targetNamespace;
                }

                @Override
                public ObjectList<Path> extraJars() {
                    return extraJars;
                }

                @Override
                public Optional<ObjectList<Path>> bundledLibs() {
                    return bundledLibs;
                }
            };
        }
    }

    private interface Options extends ClassifiedDeobfuscator.DeobfuscateOptions {
        String version();

        Info.SideType type();

        private ClassifiedDeobfuscator buildDeobfuscator() {
            if(inputMappings() != null) {
                MappingType<? extends Mapping, ?> type = Utils.tryIdentifyingMappingType(inputMappings());
                if(type instanceof MappingType.Classified mtc) {
                    if(type.isNamespaced()) {
                        return new ClassifiedDeobfuscator(new ClassifiedMappingReader<NamespacedMapping>(mtc, inputMappings()),
                                Objects.requireNonNull(targetNamespace(), "You are using a namespaced mapping but no target namespace is specified"));
                    } else return new ClassifiedDeobfuscator(new ClassifiedMappingReader<PairedMapping>(mtc, inputMappings()));
                } else throw new UnsupportedOperationException("Unsupported yet"); // TODO
            }
            return new ClassifiedDeobfuscator(version(), type());
        }

        @Override
        boolean includeOthers();

        @Override
        boolean rvn();

        BufferedReader inputMappings();

        Path inputJar();

        Path outputJar();

        Path outputDecompDir();

        @Override
        boolean reverse();

        String targetNamespace();

        @Override
        ObjectList<Path> extraJars();

        Optional<ObjectList<Path>> bundledLibs();
    }
}