package cn.maxpixel.mcdecompiler.mapping.processor;

import cn.maxpixel.mcdecompiler.mapping.NamespacedMapping;
import cn.maxpixel.mcdecompiler.mapping.collection.ClassMapping;
import cn.maxpixel.mcdecompiler.mapping.collection.ClassifiedMapping;
import cn.maxpixel.mcdecompiler.mapping.component.LocalVariableTable;
import cn.maxpixel.mcdecompiler.mapping.component.StaticIdentifiable;
import cn.maxpixel.mcdecompiler.mapping.format.MappingFormat;
import cn.maxpixel.mcdecompiler.mapping.format.MappingFormats;
import cn.maxpixel.mcdecompiler.mapping.trait.NamespacedTrait;
import cn.maxpixel.mcdecompiler.mapping.util.MappingUtil;
import it.unimi.dsi.fastutil.objects.ObjectList;

public enum TsrgV2MappingProcessor implements MappingProcessor.Classified<NamespacedMapping> {
    INSTANCE;

    @Override
    public MappingFormat<NamespacedMapping, ClassifiedMapping<NamespacedMapping>> getFormat() {
        return MappingFormats.TSRG_V2;
    }

    @Override
    public ClassifiedMapping<NamespacedMapping> process(ObjectList<String> content) {
        if(!content.get(0).startsWith("tsrg2")) error();
        String[] namespaces = MappingUtil.split(content.get(0), ' ', 6);
        ClassifiedMapping<NamespacedMapping> mappings = new ClassifiedMapping<>(new NamespacedTrait(namespaces));
        for (int i = 1, len = content.size(); i < len; ) {
            String[] sa = MappingUtil.split(content.get(i), ' ');
            if (sa[0].charAt(0) != '\t') {
                if (sa[0].charAt(sa[0].length() - 1) == '/') {
                    for (int j = 0; j < sa.length; j++) sa[j] = sa[j].substring(0, sa[j].length() - 1);
                    mappings.packages.add(new NamespacedMapping(namespaces, sa).setUnmappedNamespace(namespaces[0]));
                    i++;
                } else {
                    ClassMapping<NamespacedMapping> classMapping = new ClassMapping<>(new NamespacedMapping(namespaces, sa)
                            .setUnmappedNamespace(namespaces[0]));
                    i = processTree(i, len, namespaces, content, classMapping);
                    mappings.classes.add(classMapping);
                }
            } else error();
        }
        return mappings;
    }

    private static int processTree(int index, int size, String[] namespaces, ObjectList<String> content,
                                   ClassMapping<NamespacedMapping> classMapping) {
        for (index = index + 1; index < size; index++) {
            String s = content.get(index);
            if (s.charAt(0) == '\t') {
                String[] sa = MappingUtil.split(s, ' ', 1);
                switch (sa.length - namespaces.length) {
                    case 0 -> classMapping.addField(MappingUtil.Namespaced.o(namespaces, sa));
                    case 1 -> {
                        String desc = sa[1];
                        sa[1] = sa[0];
                        if (desc.charAt(0) == '(') {
                            NamespacedMapping methodMapping = MappingUtil.Namespaced.slduo(
                                    namespaces, sa, 1, namespaces[0], desc);
                            index = processTree1(index, size, namespaces, content, methodMapping);
                            classMapping.addMethod(methodMapping);
                        } else {
                            classMapping.addField(MappingUtil.Namespaced.duo(namespaces,
                                    sa, 1, namespaces[0], desc));
                        }
                    }
                    default -> error();
                }
            } else break;
        }
        return index;
    }

    private static int processTree1(int index, int size, String[] namespaces, ObjectList<String> content, NamespacedMapping methodMapping) {
        for (index = index + 1; index < size; index++) {
            String s = content.get(index);
            if (s.charAt(1) == '\t') {
                if (s.equals("\t\tstatic")) methodMapping.getComponent(StaticIdentifiable.class).setStatic(true);
                else {
                    String[] sa = MappingUtil.split(s, ' ', 2);
                    methodMapping.getComponent(LocalVariableTable.Namespaced.class)
                            .setLocalVariable(Integer.parseInt(sa[0]), new NamespacedMapping(namespaces, sa, 1));
                }
            } else break;
        }
        return index - 1;
    }

    private static void error() {
        throw new IllegalArgumentException("Is this a TSRG v2 mapping file?");
    }
}