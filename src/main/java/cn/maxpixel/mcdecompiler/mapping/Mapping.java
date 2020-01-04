/*
 * MinecraftDecompiler. A tool/library to deobfuscate and decompile Minecraft.
 * Copyright (C) 2020  XiaoPangxie732
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

package cn.maxpixel.mcdecompiler.mapping;

public class Mapping {
	private String obfuscatedName;
	private String originalName;
	protected Mapping(String obfuscatedName, String originalName) {
		this.obfuscatedName = obfuscatedName;
		this.originalName = originalName;
	}
	protected Mapping() {}
	public String getObfuscatedName() {
		return obfuscatedName;
	}
	public void setObfuscatedName(String obfuscatedName) {
		this.obfuscatedName = obfuscatedName;
	}
	public String getOriginalName() {
		return originalName;
	}
	public void setOriginalName(String originalName) {
		this.originalName = originalName;
	}
}