package kir.taixiu;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public final class KirTaiXiuPlugin extends JavaPlugin implements Listener {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private static final String GUI_HISTORY_TITLE_RAW = "&0Lịch Sử Tài Xỉu";
    private static final String GUI_TOP_TITLE_RAW = "&0Bảng Xếp Hạng";
    private final Random random = new Random();
    private final List<Bet> bets = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private Economy economy;
    private BukkitTask roundTask;
    private int secondsLeft;
    private boolean bettingOpen;
    private boolean resultPhase;
    private double jackpot;
    private File dataFile;
    private FileConfiguration data;
    private BossBar bossBar;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found. KirTaiXiu disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        loadData();
        createBossBar();
        startLoop();
        updateChecker = new UpdateChecker(this);
        updateChecker.checkOnStartup();
        getLogger().info("KirTaiXiu enabled.");
    }

    @Override
    public void onDisable() {
        if (roundTask != null) {
            roundTask.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        saveData();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) return false;
        economy = provider.getProvider();
        return economy != null;
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        jackpot = data.getDouble("jackpot", getConfig().getDouble("economy.jackpot-seed", 1_000_000));
        history.clear();
        history.addAll(data.getStringList("history"));
        stats.clear();
        if (data.isConfigurationSection("stats")) {
            for (String key : Objects.requireNonNull(data.getConfigurationSection("stats")).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    PlayerStats s = new PlayerStats();
                    s.name = data.getString("stats." + key + ".name", "Unknown");
                    s.wagered = data.getDouble("stats." + key + ".wagered");
                    s.profit = data.getDouble("stats." + key + ".profit");
                    s.wins = data.getInt("stats." + key + ".wins");
                    s.losses = data.getInt("stats." + key + ".losses");
                    stats.put(uuid, s);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        data.set("jackpot", jackpot);
        data.set("history", history);
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            String p = "stats." + e.getKey();
            PlayerStats s = e.getValue();
            data.set(p + ".name", s.name);
            data.set(p + ".wagered", s.wagered);
            data.set(p + ".profit", s.profit);
            data.set(p + ".wins", s.wins);
            data.set(p + ".losses", s.losses);
        }
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    private void startLoop() {
        secondsLeft = getConfig().getInt("general.round-seconds", 45);
        bettingOpen = true;
        resultPhase = false;
        updateBossBar();
        broadcast(msg("messages.round-open"));
        roundTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            updateBossBarPlayers();
            secondsLeft--;
            if (resultPhase) {
                updateBossBar();
                if (secondsLeft <= 0) {
                    secondsLeft = getConfig().getInt("general.round-seconds", 45);
                    bettingOpen = true;
                    resultPhase = false;
                    bets.clear();
                    broadcast(msg("messages.round-open"));
                    updateBossBar();
                }
                return;
            }
            if (secondsLeft <= 0) {
                settleRound();
            } else {
                updateBossBar();
            }
        }, 20L, 20L);
    }

    private void settleRound() {
        bettingOpen = false;
        int d1 = random.nextInt(6) + 1;
        int d2 = random.nextInt(6) + 1;
        int d3 = random.nextInt(6) + 1;
        int sum = TaiXiuRules.sum(d1, d2, d3);
        boolean triple = TaiXiuRules.isTriple(d1, d2, d3);
        String result = TaiXiuRules.resultLabel(sum);
        int winners = 0;
        List<Bet> jackpotWinners = new ArrayList<>();

        for (Bet bet : bets) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(bet.playerId);
            PlayerStats s = stats.computeIfAbsent(bet.playerId, k -> new PlayerStats());
            s.name = bet.playerName;
            s.wagered += bet.amount;

            boolean win = bet.wins(d1, d2, d3, sum, triple);
            if (win) {
                double payout = bet.amount * multiplier(bet);
                economy.depositPlayer(player, payout);
                s.profit += payout - bet.amount;
                s.wins++;
                winners++;
                Player online = Bukkit.getPlayer(bet.playerId);
                if (online != null) {
                    online.sendMessage(msg("messages.win")
                        .replace("{payout}", fmt(payout))
                        .replace("{profit}", fmt(payout - bet.amount)));
                }
                if (isJackpotTrigger(d1, d2, d3) && bet.type == TxBetType.TRIPLE_EXACT && bet.number == d1) {
                    jackpotWinners.add(bet);
                }
            } else {
                s.profit -= bet.amount;
                s.losses++;
                Player online = Bukkit.getPlayer(bet.playerId);
                if (online != null) {
                    online.sendMessage(msg("messages.lose").replace("{amount}", fmt(bet.amount)));
                }
            }
        }

        payJackpot(jackpotWinners);
        String time = TaiXiuRules.formatHistoryTimestamp(Instant.now(), ZoneId.systemDefault());
        String line = time + " | " + d1 + "-" + d2 + "-" + d3 + " | " + result + " | thắng=" + winners + " | cược=" + bets.size();
        history.add(0, line);
        int max = getConfig().getInt("history.keep-last", 30);
        while (history.size() > max) history.remove(history.size() - 1);
        saveData();

        if (getConfig().getBoolean("general.broadcast-result", true)) {
            broadcast(msg("messages.result")
                .replace("{d1}", String.valueOf(d1))
                .replace("{d2}", String.valueOf(d2))
                .replace("{d3}", String.valueOf(d3))
                .replace("{sum}", String.valueOf(sum))
                .replace("{result}", result + (triple ? " / Tam hoa" : ""))
                .replace("{winners}", String.valueOf(winners))
                .replace("{jackpot}", fmt(jackpot)));
        }
        showResultBossBar(d1, d2, d3, sum, result + (triple ? " / Tam hoa" : ""));
        bettingOpen = false;
        resultPhase = true;
        secondsLeft = getConfig().getInt("general.result-display-seconds", 8);
    }

    private void createBossBar() {
        if (!getConfig().getBoolean("bossbar.enabled", true)) return;
        BarColor color = parseBarColor(getConfig().getString("bossbar.betting-color", "YELLOW"));
        BarStyle style = parseBarStyle(getConfig().getString("bossbar.style", "SEGMENTED_20"));
        bossBar = Bukkit.createBossBar(color("&6Tài Xỉu"), color, style);
        bossBar.setVisible(true);
        updateBossBarPlayers();
    }

    private void updateBossBarPlayers() {
        if (bossBar == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        bossBar.getPlayers().stream()
            .filter(p -> !p.isOnline())
            .toList()
            .forEach(bossBar::removePlayer);
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        int total = resultPhase
            ? getConfig().getInt("general.result-display-seconds", 8)
            : getConfig().getInt("general.round-seconds", 45);
        bossBar.setColor(resultPhase
            ? parseBarColor(getConfig().getString("bossbar.result-color", "PURPLE"))
            : parseBarColor(getConfig().getString("bossbar.betting-color", "YELLOW")));
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, secondsLeft / Math.max(1.0, total))));
        if (!resultPhase) {
            bossBar.setTitle(color(getConfig().getString("bossbar.betting-title", "&6Tài Xỉu &8| &eCòn &f{seconds}s")
                .replace("{seconds}", String.valueOf(secondsLeft))
                .replace("{bets}", String.valueOf(bets.size()))
                .replace("{jackpot}", fmt(jackpot))));
        }
    }

    private void showResultBossBar(int d1, int d2, int d3, int sum, String result) {
        if (bossBar == null) return;
        bossBar.setColor(parseBarColor(getConfig().getString("bossbar.result-color", "PURPLE")));
        bossBar.setProgress(1.0);
        bossBar.setTitle(color(getConfig().getString("bossbar.result-title", "&6Tài Xỉu &8| &e{d1}-{d2}-{d3} &8=> &b{result}")
            .replace("{d1}", String.valueOf(d1))
            .replace("{d2}", String.valueOf(d2))
            .replace("{d3}", String.valueOf(d3))
            .replace("{sum}", String.valueOf(sum))
            .replace("{result}", result)
            .replace("{jackpot}", fmt(jackpot))));
    }

    private BarColor parseBarColor(String raw) {
        try {
            return BarColor.valueOf(String.valueOf(raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle parseBarStyle(String raw) {
        try {
            return BarStyle.valueOf(String.valueOf(raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BarStyle.SEGMENTED_20;
        }
    }

    private boolean isJackpotTrigger(int d1, int d2, int d3) {
        if (!getConfig().getBoolean("jackpot.enabled", true)) return false;
        if (!(d1 == d2 && d2 == d3)) return false;
        return getConfig().getIntegerList("jackpot.trigger-triples").contains(d1);
    }

    private void payJackpot(List<Bet> winners) {
        if (winners.isEmpty() || jackpot <= 0) return;
        double each = jackpot / winners.size();
        for (Bet bet : winners) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(bet.playerId), each);
            PlayerStats s = stats.computeIfAbsent(bet.playerId, k -> new PlayerStats());
            s.profit += each;
            Player online = Bukkit.getPlayer(bet.playerId);
            if (online != null) {
                online.sendMessage(msg("messages.jackpot-win").replace("{amount}", fmt(each)));
            }
        }
        jackpot = getConfig().getBoolean("jackpot.reset-to-seed", true)
            ? getConfig().getDouble("economy.jackpot-seed", 1_000_000)
            : 0;
    }

    private double multiplier(Bet bet) {
        return switch (bet.type) {
            case TAI_XIU -> getConfig().getDouble("payouts.tai-xiu", 1.9);
            case EVEN_ODD -> getConfig().getDouble("payouts.chan-le", 1.9);
            case TOTAL -> getConfig().getDouble("payouts.total", 10.0);
            case TRIPLE_ANY -> getConfig().getDouble("payouts.triple-any", 28.0);
            case TRIPLE_EXACT -> getConfig().getDouble("payouts.triple-exact", 120.0);
        };
    }

    private boolean placeBet(Player player, TxBetType type, int number, double amount, String choiceLabel) {
        double min = getConfig().getDouble("general.min-bet", 1000);
        double max = getConfig().getDouble("general.max-bet", 10_000_000);
        if (!bettingOpen) return fail(player, "ván hiện tại đã chốt");
        Bet activeBet = findBet(player.getUniqueId());
        if (activeBet != null) {
            player.sendMessage(msg("messages.already-bet")
                .replace("{choice}", activeBet.label)
                .replace("{amount}", fmt(activeBet.amount)));
            return false;
        }
        if (amount < min) return fail(player, "tối thiểu " + fmt(min) + "$");
        if (amount > max) return fail(player, "tối đa " + fmt(max) + "$");
        if (!economy.has(player, amount)) return fail(player, "không đủ tiền");

        double feePercent = getConfig().getDouble("economy.house-fee-percent", 3.0);
        double fee = amount * feePercent / 100.0;
        double jackpotPart = fee * getConfig().getDouble("economy.jackpot-share-of-fee-percent", 50.0) / 100.0;
        jackpot += jackpotPart;

        economy.withdrawPlayer(player, amount);
        bets.add(new Bet(player.getUniqueId(), player.getName(), type, number, amount, choiceLabel));
        player.sendMessage(msg("messages.bet-placed")
            .replace("{amount}", fmt(amount))
            .replace("{choice}", choiceLabel)
            .replace("{fee}", fmt(fee)));
        return true;
    }

    private Bet findBet(UUID playerId) {
        for (Bet bet : bets) {
            if (bet.playerId.equals(playerId)) {
                return bet;
            }
        }
        return null;
    }

    private boolean fail(Player player, String reason) {
        player.sendMessage(msg("messages.bet-failed").replace("{reason}", reason));
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) openGui(p);
            else sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("kirtaixiu.admin")) return deny(sender);
            reloadConfig();
            sender.sendMessage(color("&aĐã reload KirTaiXiu."));
            return true;
        }
        if (sub.equals("history")) {
            if (sender instanceof Player p) openHistoryGui(p);
            else {
                sender.sendMessage(color("&6--- Lịch sử Tài Xỉu ---"));
                history.stream().limit(10).forEach(h -> sender.sendMessage(color("&7" + h)));
            }
            return true;
        }
        if (sub.equals("top")) {
            if (sender instanceof Player p) openTopGui(p);
            else sendTop(sender);
            return true;
        }
        if (sub.equals("jackpot")) {
            sender.sendMessage(color("&dJackpot hiện tại: &6" + fmt(jackpot) + "$"));
            return true;
        }
        if (sub.equals("update")) {
            boolean force = args.length >= 2 && args[1].equalsIgnoreCase("force");
            updateChecker.report(sender, force);
            return true;
        }
        if (sub.equals("debug")) {
            if (!sender.hasPermission("kirtaixiu.admin")) return deny(sender);
            sender.sendMessage(color("&6--- KirTaiXiu Debug ---"));
            sender.sendMessage(color("&7round-seconds: &e" + getConfig().getInt("general.round-seconds", -1)));
            sender.sendMessage(color("&7payout tai-xiu: &e" + payoutText("payouts.tai-xiu")));
            sender.sendMessage(color("&7payout triple-any: &e" + payoutText("payouts.triple-any")));
            sender.sendMessage(color("&7payout triple-exact: &e" + payoutText("payouts.triple-exact")));
            sender.sendMessage(color("&7message round-open exists: &e" + getConfig().isString("messages.round-open")));
            sender.sendMessage(color("&7message result exists: &e" + getConfig().isString("messages.result")));
            return true;
        }
        if (sub.equals("reset")) {
            if (!sender.hasPermission("kirtaixiu.admin")) return deny(sender);
            handleReset(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can bet.");
            return true;
        }
        if (!player.hasPermission("kirtaixiu.use")) return deny(player);
        if (args.length < 2) {
            sendHelp(sender, label);
            return true;
        }
        try {
            switch (sub) {
                case "tai", "tài" -> placeBet(player, TxBetType.TAI_XIU, 1, parseMoney(args[1]), "Tài");
                case "xiu", "xỉu" -> placeBet(player, TxBetType.TAI_XIU, 0, parseMoney(args[1]), "Xỉu");
                case "chan", "chẵn" -> placeBet(player, TxBetType.EVEN_ODD, 0, parseMoney(args[1]), "Chẵn");
                case "le", "lẻ" -> placeBet(player, TxBetType.EVEN_ODD, 1, parseMoney(args[1]), "Lẻ");
                case "tong", "tổng" -> {
                    if (args.length < 3) {
                        sendHelp(sender, label);
                        return true;
                    }
                    int total = Integer.parseInt(args[1]);
                    if (total < 3 || total > 18) return fail(player, "tổng phải từ 3 tới 18");
                    placeBet(player, TxBetType.TOTAL, total, parseMoney(args[2]), "Tổng " + total);
                }
                case "tamhoa" -> {
                    if (args.length >= 3 && args[1].equalsIgnoreCase("any")) {
                        placeBet(player, TxBetType.TRIPLE_ANY, 0, parseMoney(args[2]), "Tam hoa bất kỳ");
                    } else {
                        if (args.length < 3) {
                            sendHelp(sender, label);
                            return true;
                        }
                        int n = Integer.parseInt(args[1]);
                        if (n < 1 || n > 6) return fail(player, "tam hoa chính xác phải từ 1 tới 6");
                        placeBet(player, TxBetType.TRIPLE_EXACT, n, parseMoney(args[2]), "Tam hoa " + n);
                    }
                }
                default -> sendHelp(sender, label);
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(color("&cSố tiền không hợp lệ."));
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        String authors = String.join(", ", getDescription().getAuthors());
        if (authors.isBlank()) {
            authors = "Kir";
        }
        sender.sendMessage(color("&6--- Tài Xỉu ---"));
        sender.sendMessage(color("&7Phiên bản: &e" + getDescription().getVersion() + " &8| &7Tác giả: &b" + authors));
        sender.sendMessage(color("&7Lệnh chính: &e/taixiu &8| &7Lệnh tắt: &e/tx"));
        sender.sendMessage(color("&e/" + label + " &7- mở GUI"));
        sender.sendMessage(color("&e/" + label + " tai <tiền> &7| &e/" + label + " xiu <tiền>"));
        sender.sendMessage(color("&e/" + label + " chan <tiền> &7| &e/" + label + " le <tiền>"));
        sender.sendMessage(color("&e/" + label + " tong <3-18> <tiền>"));
        sender.sendMessage(color("&e/" + label + " tamhoa <1-6> <tiền>"));
        sender.sendMessage(color("&e/" + label + " jackpot &7| &e/" + label + " history &7| &e/" + label + " top"));
        sender.sendMessage(color("&e/" + label + " update &7[force] &8- kiểm tra bản cập nhật"));
        if (sender.hasPermission("kirtaixiu.admin")) {
            sender.sendMessage(color("&c[Admin] &e/" + label + " reset <stats|history|jackpot|all>"));
        }
    }

    private void sendTop(CommandSender sender) {
        sender.sendMessage(color("&6--- Top lợi nhuận " + getConfig().getString("season.name", "Mùa 1") + " ---"));
        stats.values().stream()
            .sorted(Comparator.comparingDouble((PlayerStats s) -> s.profit).reversed())
            .limit(getConfig().getInt("season.leaderboard-size", 10))
            .forEach(s -> sender.sendMessage(color("&e" + s.name + " &7- &a" + fmt(s.profit) + "$ &8(" + s.wins + "W/" + s.losses + "L)")));
    }

    private boolean deny(CommandSender sender) {
        sender.sendMessage(msg("messages.no-permission"));
        return true;
    }

    private double parseMoney(String raw) {
        return TaiXiuRules.parseMoney(raw);
    }

    private String rawGuiTitle() {
        return getConfig().getString("general.gui-title", "&0Tài Xỉu");
    }

    private String strippedGuiTitle() {
        return ChatColor.stripColor(color(rawGuiTitle()));
    }

    private void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, color(rawGuiTitle()));
        inv.setItem(10, item(Material.RED_DYE, "&cCược Tài",
            "&7Lệnh: &e/tx tai <tiền>",
            "&fĐiều kiện: tổng &e11-18",
            "&fTrả thưởng: &a" + payoutText("payouts.tai-xiu") + "x",
            "&8Ví dụ: /tx tai 100k"));
        inv.setItem(12, item(Material.LAPIS_LAZULI, "&bCược Xỉu",
            "&7Lệnh: &e/tx xiu <tiền>",
            "&fĐiều kiện: tổng &e3-10",
            "&fTrả thưởng: &a" + payoutText("payouts.tai-xiu") + "x",
            "&8Ví dụ: /tx xiu 100k"));
        inv.setItem(14, item(Material.QUARTZ, "&fCược Chẵn/Lẻ",
            "&7Lệnh: &e/tx chan <tiền>",
            "&7Lệnh: &e/tx le <tiền>",
            "&fĐiều kiện: tổng điểm chẵn hoặc lẻ",
            "&fTrả thưởng: &a" + payoutText("payouts.chan-le") + "x"));
        inv.setItem(16, item(Material.AMETHYST_SHARD, "&dCược Tổng Điểm",
            "&7Lệnh: &e/tx tong <3-18> <tiền>",
            "&fĐoán chính xác tổng 3 viên xúc xắc",
            "&fTrả thưởng: &a" + payoutText("payouts.total") + "x",
            "&8Ví dụ: /tx tong 12 100k"));
        inv.setItem(20, item(Material.NETHER_STAR, "&5Tam Hoa",
            "&7Bất kỳ: &e/tx tamhoa any <tiền>",
            "&7Chính xác: &e/tx tamhoa <1-6> <tiền>",
            "&fTam hoa bất kỳ: &a" + payoutText("payouts.triple-any") + "x",
            "&fTam hoa chính xác: &a" + payoutText("payouts.triple-exact") + "x"));
        inv.setItem(22, item(Material.GOLD_NUGGET, "&eJackpot / Nổ Hũ",
            "&fHũ hiện tại: &6" + fmt(jackpot) + "$",
            "&fNổ khi ra tam hoa: &d" + getConfig().getIntegerList("jackpot.trigger-triples"),
            "&fCần cược đúng tam hoa chính xác",
            "&7Nếu nhiều người trúng sẽ chia đều."));
        inv.setItem(24, item(Material.BOOK, "&aThông tin ván",
            "&7Còn: &e" + secondsLeft + "s",
            "&7Cược hiện tại: &f" + bets.size(),
            "&7Giới hạn: &c1 cược / người / ván",
            "&7Min: &e" + fmt(getConfig().getDouble("general.min-bet", 1000)) + "$",
            "&7Max: &e" + fmt(getConfig().getDouble("general.max-bet", 10_000_000)) + "$"));
        inv.setItem(29, item(Material.PAPER, "&fLịch Sử",
            "&7Click để mở lịch sử ván đấu",
            "&7Hoặc dùng &e/tx history"));
        inv.setItem(33, item(Material.GOLD_INGOT, "&6Bảng Xếp Hạng",
            "&7Click để mở bảng xếp hạng",
            "&7Hoặc dùng &e/tx top"));
        player.openInventory(inv);
    }

    private void openHistoryGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color(GUI_HISTORY_TITLE_RAW));
        ItemStack glass = item(Material.GRAY_STAINED_GLASS_PANE, "&8 ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);
        int slot = 0;
        for (String entry : history) {
            if (slot >= 45) break;
            String[] parts = entry.split(" \\| ");
            List<String> lore = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) lore.add(color("&7" + parts[i]));
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.setDisplayName(color("&e#" + (slot + 1) + " &f" + (parts.length > 0 ? parts[0] : entry)));
            meta.setLore(lore);
            paper.setItemMeta(meta);
            inv.setItem(slot++, paper);
        }
        if (history.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(color("&cChưa có lịch sử nào."));
            empty.setItemMeta(meta);
            inv.setItem(22, empty);
        }
        inv.setItem(49, item(Material.BARRIER, "&cĐóng"));
        player.openInventory(inv);
    }

    private void openTopGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, color(GUI_TOP_TITLE_RAW));
        ItemStack glass = item(Material.GRAY_STAINED_GLASS_PANE, "&8 ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);
        // Layout hình kim cương: rank 1 ở trung tâm, 2-3 flanking, 4-7, 8-10 xuống dần
        int[] topSlots = {13, 20, 24, 28, 30, 32, 34, 38, 40, 42};
        String[] medals = {"&6#1", "&7#2", "&e#3", "&f#4", "&f#5", "&f#6", "&f#7", "&f#8", "&f#9", "&f#10"};
        int limit = Math.min(getConfig().getInt("season.leaderboard-size", 10), topSlots.length);
        List<Map.Entry<UUID, PlayerStats>> ranked = stats.entrySet().stream()
            .sorted(Comparator.comparingDouble((Map.Entry<UUID, PlayerStats> e) -> e.getValue().profit).reversed())
            .limit(limit)
            .toList();
        if (ranked.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName(color("&cChưa có dữ liệu người chơi."));
            empty.setItemMeta(meta);
            inv.setItem(22, empty);
        }
        for (int i = 0; i < ranked.size(); i++) {
            UUID uuid = ranked.get(i).getKey();
            PlayerStats s = ranked.get(i).getValue();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skull = (SkullMeta) head.getItemMeta();
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            skull.setDisplayName(color(medals[i] + " &f" + s.name));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7Lợi nhuận: " + (s.profit >= 0 ? "&a+" : "&c") + fmt(s.profit) + "$"));
            lore.add(color("&7Tổng cược: &e" + fmt(s.wagered) + "$"));
            lore.add(color("&7Thắng/Thua: &a" + s.wins + "&7/&c" + s.losses));
            skull.setLore(lore);
            head.setItemMeta(skull);
            inv.setItem(topSlots[i], head);
        }
        inv.setItem(49, item(Material.BARRIER, "&cĐóng"));
        player.openInventory(inv);
    }

    private void handleReset(CommandSender sender, String[] args) {
        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (target) {
            case "stats" -> {
                stats.clear();
                saveData();
                sender.sendMessage(color("&aĐã reset toàn bộ thống kê người chơi."));
            }
            case "history" -> {
                history.clear();
                saveData();
                sender.sendMessage(color("&aĐã xóa toàn bộ lịch sử ván đấu."));
            }
            case "jackpot" -> {
                jackpot = getConfig().getDouble("economy.jackpot-seed", 1_000_000);
                saveData();
                sender.sendMessage(color("&aĐã reset jackpot về &6" + fmt(jackpot) + "$."));
            }
            case "all" -> {
                stats.clear();
                history.clear();
                jackpot = getConfig().getDouble("economy.jackpot-seed", 1_000_000);
                saveData();
                sender.sendMessage(color("&aĐã reset toàn bộ dữ liệu (stats, lịch sử, jackpot)."));
            }
            default -> sender.sendMessage(color("&eUsage: /tx reset <stats|history|jackpot|all>"));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String stripped = ChatColor.stripColor(event.getView().getTitle());
        if (stripped.equals(strippedGuiTitle())) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 29) {
                player.closeInventory();
                openHistoryGui(player);
            } else if (slot == 33) {
                player.closeInventory();
                openTopGui(player);
            } else if (slot == 22) {
                player.sendMessage(color("&dJackpot hiện tại: &6" + fmt(jackpot) + "$"));
            } else if (slot == 10 || slot == 12 || slot == 14 || slot == 16 || slot == 20) {
                String cmd = switch (slot) {
                    case 10 -> "tai";
                    case 12 -> "xiu";
                    case 14 -> "chan";
                    case 16 -> "tong <3-18>";
                    case 20 -> "tamhoa <1-6>";
                    default -> "tai";
                };
                player.sendMessage(color("&7Dùng lệnh để nhập số tiền, ví dụ: &e/tx " + cmd + " 100k"));
            }
        } else if (stripped.equals(ChatColor.stripColor(color(GUI_HISTORY_TITLE_RAW)))) {
            event.setCancelled(true);
            if (event.getRawSlot() == 49) player.closeInventory();
        } else if (stripped.equals(ChatColor.stripColor(color(GUI_TOP_TITLE_RAW)))) {
            event.setCancelled(true);
            if (event.getRawSlot() == 49) player.closeInventory();
        }
    }

    private String payoutText(String path) {
        return fmt(getConfig().getDouble(path, payoutDefault(path)));
    }

    private double payoutDefault(String path) {
        return switch (path) {
            case "payouts.tai-xiu", "payouts.chan-le" -> 1.90;
            case "payouts.total" -> 10.0;
            case "payouts.triple-any" -> 28.0;
            case "payouts.triple-exact" -> 120.0;
            default -> 1.0;
        };
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(color(line));
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private void broadcast(String text) {
        Bukkit.broadcastMessage(text);
    }

    private String msg(String path) {
        return color(getConfig().getString("general.prefix", "&6[TX]&r ") + getConfig().getString(path, defaultMessage(path)));
    }

    private String defaultMessage(String path) {
        return switch (path) {
            case "messages.no-permission" -> "&cBạn không có quyền.";
            case "messages.economy-missing" -> "&cKhông tìm thấy Vault economy.";
            case "messages.round-open" -> "&aVán mới đã mở! Dùng &e/tx tài <tiền>&a hoặc &e/tx xỉu <tiền>&a.";
            case "messages.already-bet" -> "&cMỗi ván chỉ được cược 1 lần. Bạn đã cược &e{choice} &cvới &e{amount}$&c.";
            case "messages.bet-placed" -> "&aĐã cược &e{amount}$ &avào &e{choice}&a. Phí: &c{fee}$&a.";
            case "messages.bet-failed" -> "&cKhông thể đặt cược: {reason}";
            case "messages.result" -> "&6Kết quả: &e{d1}-{d2}-{d3} &7(tổng {sum}) &f=> &b{result}&f. Người thắng: &a{winners}&f. Jackpot: &6{jackpot}$";
            case "messages.win" -> "&aBạn thắng &e{payout}$ &7(lãi ròng {profit}$)&a.";
            case "messages.lose" -> "&cBạn thua &e{amount}$&c.";
            case "messages.jackpot-win" -> "&dBạn đã nổ hũ và nhận &6{amount}$&d!";
            default -> "&cThiếu message config: " + path;
        };
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String fmt(double value) {
        return MONEY.format(value);
    }

    private record Bet(UUID playerId, String playerName, TxBetType type, int number, double amount, String label) {
        boolean wins(int d1, int d2, int d3, int sum, boolean triple) {
            return TaiXiuRules.wins(type, number, d1, d2, d3);
        }
    }

    private static final class PlayerStats {
        String name = "Unknown";
        double wagered;
        double profit;
        int wins;
        int losses;
    }
}
