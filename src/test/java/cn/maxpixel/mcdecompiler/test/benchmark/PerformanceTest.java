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

package cn.maxpixel.mcdecompiler.test.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@Fork(1)
@Threads(1)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
@Measurement(iterations = 100)
@Warmup(iterations = 50)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PerformanceTest {
    public void test() throws RunnerException {
        Options options = new OptionsBuilder()
                .include(".*" + PerformanceTest.class.getSimpleName() + ".*")
                .build();
//        new Runner(options).run();
    }

    public String s = "fsasuifgbafsbdfjhsavd/asdfsafsda/fasdfsadf\\asdfsfasdf\\afssafasd\\asfdsadfs\\wefwef/wfwewew\\fwew.class";

    @Benchmark
    public void replaceAll(Blackhole bh) {
        bh.consume(s.replaceAll("/|\\\\|.class", ""));
    }

    @Benchmark
    public void replace(Blackhole bh) {
        bh.consume(s.replace('/', '.').replace('\\', '.').replace(".class", ""));
    }
}