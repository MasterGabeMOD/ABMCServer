package server.alanbecker.net;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(id = "abmcserver", name = "ABMCServer", version = "1.0.0",
        description = "Server switch commands", authors = {"MasterGabeMOD"})
public class ABMCServer {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private ConfigurationNode config;

    @Inject
    public ABMCServer(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        loadConfig();
        registerCommands();
    }

    private void loadConfig() {
        try {
            File configFile = new File(dataDirectory.toFile(), "config.yml");
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            }

            YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder().setPath(configFile.toPath()).build();
            config = loader.load();
        } catch (Exception e) {
            logger.error("Error loading configuration: ", e);
        }
    }

    private void registerCommands() {
        CommandManager commandManager = server.getCommandManager();
        ConfigurationNode serversNode = config.getNode("servers");
        if (serversNode.isVirtual()) {
            logger.warn("No server commands configured.");
            return;
        }

        serversNode.getChildrenMap().forEach((key, value) -> {
            String command = key.toString();
            String serverName = value.getString();
            commandManager.register(commandManager.metaBuilder(command).build(), new ServerCommand(serverName));
        });
    }

    private class ServerCommand implements SimpleCommand {
        private final String targetServer;

        public ServerCommand(String targetServer) {
            this.targetServer = targetServer;
        }

        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player)) {
                invocation.source().sendMessage(
                    Component.text("Only players can use this command!").color(TextColor.color(0xFF5555))
                );
                return;
            }

            Player player = (Player) invocation.source();
            UUID playerUuid = player.getUniqueId();
            long cooldown = config.getNode("cooldown").getLong(15);

            if (cooldowns.containsKey(playerUuid) &&
                System.currentTimeMillis() - cooldowns.get(playerUuid) < TimeUnit.SECONDS.toMillis(cooldown)) {
                player.sendMessage(
                    Component.text("You must wait before using this command again.").color(TextColor.color(0x55FF55))
                );
                return;
            }

            server.getServer(targetServer).ifPresent(s -> {
                player.createConnectionRequest(s).fireAndForget();
                player.sendMessage(
                    Component.text("Connecting to " + targetServer + "...").color(TextColor.color(0x55FFFF))
                );
            });

            if (!server.getServer(targetServer).isPresent()) {
                player.sendMessage(
                    Component.text("Server not found.").color(TextColor.color(0xFF5555))
                );
            }

            cooldowns.put(playerUuid, System.currentTimeMillis());
        }
    }
}
