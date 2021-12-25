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

package cn.maxpixel.mcdecompiler;

import java.nio.file.Path;
import java.util.Objects;

public class Properties {
    public static Path TEMP_DIR = Path.of("temp");
    public static Path DOWNLOAD_DIR = Path.of("downloads");

    public static Path getDownloadedMcJarPath(String version, Info.SideType type) {
        return DOWNLOAD_DIR.resolve(version).resolve(type + ".jar");
    }

    public static Path getDownloadedProguardMappingPath(String version, Info.SideType type) {
        return DOWNLOAD_DIR.resolve(version).resolve(type + "_mappings.txt");
    }

    public static Path getDownloadedDecompilerPath(Info.DecompilerType type) {
        if(Objects.requireNonNull(type) == Info.DecompilerType.USER_DEFINED) throw new UnsupportedOperationException();
        return DOWNLOAD_DIR.resolve("decompiler").resolve(Objects.requireNonNull(type) + ".jar");
    }
}