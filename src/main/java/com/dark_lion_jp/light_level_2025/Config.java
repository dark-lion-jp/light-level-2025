package com.dark_lion_jp.light_level_2025;

import com.esotericsoftware.yamlbeans.YamlConfig.WriteClassName;
import com.esotericsoftware.yamlbeans.scalar.ScalarSerializer;
import com.esotericsoftware.yamlbeans.YamlConfig;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class Config {

  public int version = BuildConfig.CONFIG_VERSION;

  public render_distance render_distance = new render_distance();

  public block block = new block();

  public text text = new text();

  public cache cache = new cache();

  public static class render_distance {

    public int horizontal = 16;
    public int vertical = 4;
  }

  public static class block {

    public Set<String> blacklist = new LinkedHashSet<>(Arrays.asList(
        "minecraft:air",
        "minecraft:barrier",
        "minecraft:bedrock",
        "minecraft:chain_command_block",
        "minecraft:command_block",
        "minecraft:end_portal",
        "minecraft:nether_portal",
        "minecraft:repeating_command_block"
    ));

    public Set<String> whitelist = new LinkedHashSet<>(Arrays.asList(
        "minecraft:mud",
        "minecraft:slime_block",
        "minecraft:soul_sand"
    ));
  }

  public static class text {

    public color color = new color();
    public scale scale = new scale();
    public float offset_y_base = 0.1f;

    public static class color {

      public Hex safe = new Hex(0xFF40FF40);
      public Hex warning = new Hex(0xFFFFFF40);
      public Hex danger = new Hex(0xFFFF4040);
      public Hex neutral = new Hex(0xFFFFFFFF);
    }

    public static class scale {

      public float normal = 1f / 32f;
      public float debug = 1f / 48f;
    }
  }

  public static class cache {

    public int update_interval_frames = 20;
  }

  public static class Hex {

    public int value;

    public Hex(int value) {
      this.value = value;
    }
  }

  private static class HexColorSerializer implements ScalarSerializer<Hex> {

    @Override
    public String write(Hex hex) {
      return String.format("%06X", hex.value & 0x00FFFFFF);
    }

    @Override
    public Hex read(String value) {
      return new Hex(
          0xFF000000 | Integer.parseInt(value, 16)
      );
    }
  }

  private static final Path path = FabricLoader
      .getInstance()
      .getConfigDir()
      .resolve(BuildConfig.CONFIG_PATH);

  public Set<Block> getBlockBlacklist() {
    return block
        .blacklist
        .stream()
        .map(id -> Registries.BLOCK.get(Identifier.of(id)))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public Set<Block> getBlockWhitelist() {
    return block
        .whitelist
        .stream()
        .map(id -> Registries.BLOCK.get(Identifier.of(id)))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public Config load() {
    File configFile = new File(String.valueOf(path));

    if (!configFile.exists()) {
      Config config = new Config();
      config.save();
      return config;
    }
    try (FileReader reader = new FileReader(configFile)) {
      YamlConfig yamlConfig = new YamlConfig();
      yamlConfig.setScalarSerializer(Hex.class, new HexColorSerializer());

      YamlReader yamlReader = new YamlReader(reader, yamlConfig);
      Config config = yamlReader.read(Config.class);
      yamlReader.close();

      if (config.version != BuildConfig.CONFIG_VERSION) {
        config.upgrade();
        config.save();
      }

      return config;
    } catch (IOException e) {
      return new Config();
    }
  }

  public void save() {
    File configFile = new File(String.valueOf(path));

    try (FileWriter writer = new FileWriter(configFile)) {
      YamlConfig yamlConfig = new YamlConfig();
      yamlConfig.writeConfig.setVersion(null);
      yamlConfig.writeConfig.setExplicitFirstDocument(false);
      yamlConfig.writeConfig.setWriteRootTags(false);
      yamlConfig.writeConfig.setWriteDefaultValues(true);
      yamlConfig.writeConfig.setWriteClassname(WriteClassName.NEVER);
      yamlConfig.writeConfig.setKeepBeanPropertyOrder(true);
      yamlConfig.setScalarSerializer(Hex.class, new HexColorSerializer());

      YamlWriter yamlWriter = new YamlWriter(writer, yamlConfig);
      yamlWriter.write(this);
      yamlWriter.close();
    } catch (IOException e) {
      //
    }
  }

  public void upgrade() {
    if (this.version < 2) {
      this.block.blacklist.addAll(Arrays.asList(
          "minecraft:end_portal",
          "minecraft:nether_portal"
      ));
    }

    this.version = BuildConfig.CONFIG_VERSION;
  }
}
