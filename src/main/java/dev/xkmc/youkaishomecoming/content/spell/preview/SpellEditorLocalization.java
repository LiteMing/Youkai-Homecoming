package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Display-only localization for the in-game spell editor.
 * English mode intentionally keeps the code-facing labels unchanged.
 */
public final class SpellEditorLocalization {

	private static final Pattern COUNT_SECTION = Pattern.compile("^(onEnter|onTick|onExit|onDamage) \\((\\d+)\\)$");
	private static final Pattern COUNT_BRANCH = Pattern.compile("^(if_true|if_false|body|actions|onExpiry|onTrail|onHitEntity|onHitBlock) \\((\\d+)\\)$");
	private static final Pattern ACTION_ROW = Pattern.compile("^([TFBEH]?)(\\d+: )(.*)$");
	private static final Pattern NUMBER_PREFIX = Pattern.compile("^(\\d+:)(.+)$");
	private static final String[] POLICY_MARKERS = {"[EXP] ", "[OP] ", "[Q] ", "[X] "};

	private static Boolean chineseOverride;

	private SpellEditorLocalization() {
	}

	public static boolean isChinese() {
		if (chineseOverride != null) {
			return chineseOverride;
		}
		try {
			String code = Minecraft.getInstance().getLanguageManager().getSelected();
			return code != null && code.toLowerCase(java.util.Locale.ROOT).startsWith("zh");
		} catch (Exception ignored) {
			return false;
		}
	}

	public static void toggle() {
		chineseOverride = !isChinese();
	}

	public static String modeButtonLabel() {
		return isChinese() ? "中文" : "EN";
	}

	public static String t(String text) {
		if (text == null || text.isEmpty() || !isChinese()) {
			return text;
		}
		String italic = "";
		if (text.startsWith("\u00A7o")) {
			italic = "\u00A7o";
			text = text.substring(2);
		}
		String prefix = "";
		if (text.startsWith("\u25B6 ") || text.startsWith("\u25BC ")) {
			prefix = text.substring(0, 2);
			text = text.substring(2);
		}
		for (String marker : POLICY_MARKERS) {
			if (text.startsWith(marker)) {
				prefix += marker;
				text = text.substring(marker.length());
				break;
			}
		}
		if (text.startsWith("* ")) {
			return italic + prefix + "* " + t(text.substring(2));
		}
		if (text.endsWith("*")) {
			return italic + prefix + t(text.substring(0, text.length() - 1)) + "*";
		}

		Matcher count = COUNT_SECTION.matcher(text);
		if (count.matches()) {
			return italic + prefix + exact(count.group(1)) + " (" + count.group(2) + ")";
		}
		Matcher branch = COUNT_BRANCH.matcher(text);
		if (branch.matches()) {
			return italic + prefix + exact(branch.group(1)) + " (" + branch.group(2) + ")";
		}
		Matcher action = ACTION_ROW.matcher(text);
		if (action.matches()) {
			return italic + prefix + action.group(1) + action.group(2) + actionRest(action.group(3));
		}
		Matcher numbered = NUMBER_PREFIX.matcher(text);
		if (numbered.matches()) {
			return italic + prefix + numbered.group(1) + t(numbered.group(2));
		}

		String mapped = exact(text);
		if (!mapped.equals(text)) {
			return italic + prefix + mapped;
		}
		if (text.startsWith("+ ")) {
			return italic + prefix + "+ " + t(text.substring(2));
		}
		if (text.startsWith("Delete ")) {
			return italic + prefix + "删除 " + text.substring("Delete ".length());
		}
		if (text.startsWith("Limit:")) {
			return italic + prefix + "上限:" + text.substring("Limit:".length());
		}
		if (text.startsWith("Dist:")) {
			return italic + prefix + "距离:" + text.substring("Dist:".length());
		}
		if (text.startsWith("Height:")) {
			return italic + prefix + "高度:" + text.substring("Height:".length());
		}
		if (text.startsWith("THP:")) {
			return italic + prefix + "目标HP:" + text.substring("THP:".length());
		}
		if (text.startsWith("HP:")) {
			return italic + prefix + "HP:" + text.substring("HP:".length());
		}
		if (text.startsWith("Power:")) {
			return italic + prefix + "P点:" + text.substring("Power:".length());
		}
		if (text.startsWith("Caster Marker: ")) {
			return italic + prefix + "施法者标记: " + t(text.substring("Caster Marker: ".length()));
		}
		if (text.startsWith("Target Marker: ")) {
			return italic + prefix + "目标标记: " + t(text.substring("Target Marker: ".length()));
		}
		if (text.startsWith("Ground: ")) {
			return italic + prefix + "在地面: " + t(text.substring("Ground: ".length()));
		}
		if (text.startsWith("Flying: ")) {
			return italic + prefix + "飞行: " + t(text.substring("Flying: ".length()));
		}
		if (text.startsWith("Elytra: ")) {
			return italic + prefix + "鞘翅飞行: " + t(text.substring("Elytra: ".length()));
		}
		if (text.startsWith("Overridden by ") && text.endsWith(" mover")) {
			return italic + prefix + "由 " + t(text.substring(14, text.length() - 6)) + " 运动器覆盖";
		}
		if (text.startsWith("Rotating ")) {
			return italic + prefix + "正在旋转 " + text.substring("Rotating ".length());
		}
		return italic + prefix + text;
	}

	public static String danmakuBulletShapeName(String bulletId) {
		if (bulletId == null || bulletId.isBlank()) {
			return bulletId;
		}
		String id = bulletId.toLowerCase(Locale.ROOT);
		if (!isChinese()) {
			return id;
		}
		if (id.equals("scale")) return "鳞弹";
		if (id.equals("giant_yinyang")) return "巨大阴阳玉";
		if (id.equals("moon")) return "月球弹";
		String itemKey = switch (id) {
			case "moon" -> "item.youkaishomecoming.moon_danmaku";
			case "giant_yinyang" -> "item.youkaishomecoming.giant_yinyang_danmaku";
			case "scale" -> "item.youkaishomecoming.scale_danmaku";
			default -> "item.youkaishomecoming.white_" + id + "_danmaku";
		};
		if (I18n.exists(itemKey)) {
			return stripDanmakuItemName(I18n.get(itemKey));
		}
		return t(id.replace('_', ' '));
	}

	private static String stripDanmakuItemName(String name) {
		String ans = name;
		for (String prefix : new String[]{
				"淡蓝色", "淡灰色", "黄绿色", "品红色", "粉红色",
				"黑色", "蓝色", "棕色", "青色", "灰色", "绿色",
				"橙色", "紫色", "红色", "白色", "黄色"
		}) {
			if (ans.startsWith(prefix)) {
				ans = ans.substring(prefix.length());
				break;
			}
		}
		if (ans.endsWith("弹幕")) {
			ans = ans.substring(0, ans.length() - "弹幕".length());
		}
		return ans;
	}

	private static String actionRest(String text) {
		if (text.startsWith("fire spell ")) {
			return "发射符卡 " + text.substring("fire spell ".length());
		}
		if (text.startsWith("fire ")) {
			String[] parts = text.split(" ");
			if (parts.length >= 3) {
				return "发射 " + danmakuBulletShapeName(parts[1]) + " " + t(parts[2]);
			}
			return "发射 " + text.substring(5);
		}
		if (text.startsWith("laser ")) {
			String[] parts = text.split(" ");
			if (parts.length >= 3) {
				return "激光 " + t(parts[1]) + " " + t(parts[2]);
			}
			return "激光 " + text.substring(6);
		}
		if (text.startsWith("if ")) return "如果 " + text.substring(3);
		if (text.startsWith("sequence")) return text.replace("sequence", "序列");
		if (text.equals("clear_screen")) return "清屏";
		if (text.startsWith("erase enemy r=")) return "擦除敌弹 r=" + text.substring("erase enemy r=".length());
		if (text.equals("play_sound")) return "播放声音";
		if (text.startsWith("show title ")) return text.replace("show title", "显示符卡标题");
		if (text.startsWith("spell circle ")) return text.replace("spell circle", "魔法阵")
				.replace(" size=", " 大小=");
		if (text.startsWith("set ")) return "设置 " + text.substring(4);
		if (text.startsWith("add ")) return "增加 " + text.substring(4);
		if (text.startsWith("force ")) return "切换阶段 " + text.substring(6).replace("[clear]", "[清屏]").replace("[keep]", "[保留]");
		if (text.startsWith("spell ")) return "切换符卡 " + text.substring(6).replace("[clear]", "[清屏]").replace("[keep]", "[保留]");
		if (text.startsWith("repeat")) return text.replace("repeat", "重复");
		if (text.startsWith("delay")) return text.replace("delay", "延迟");
		if (text.startsWith("burst")) return text.replace("burst", "爆发");
		if (text.startsWith("shooter")) return text.replace("shooter", "发射器");
		if (text.startsWith("ysm clear")) return text.replace("ysm clear", "YSM 清除");
		if (text.startsWith("ysm set")) return text.replace("ysm set", "YSM 设置")
				.replace(" model=", " 模型=").replace(" tex=", " 贴图=").replace(" anim=", " 动画=")
				.replace(" expire=", " 到期清除=");
		if (text.equals("teleport")) return "传送";
		if (text.equals("noop")) return "空操作";
		return t(text);
	}

	private static String exact(String text) {
		return ZH.getOrDefault(text, text);
	}

	private static final Map<String, String> ZH = Map.ofEntries(
			Map.entry("Spell Editor", "符卡编辑器"),
			Map.entry("Spell Preview", "符卡预览"),
			Map.entry("New Spell", "新符卡"),
			Map.entry("Ortho", "正交"),
			Map.entry("Persp", "透视"),
			Map.entry("BindTgt", "绑定目标"),
			Map.entry("Unbind", "解绑"),
			Map.entry("Editor <<", "编辑器 <<"),
			Map.entry("Editor >>", "编辑器 >>"),
			Map.entry("Save & Refresh", "保存并刷新"),
			Map.entry("Prev", "上一个"),
			Map.entry("Next", "下一个"),
			Map.entry("New", "新建"),
			Map.entry("Save", "保存"),
			Map.entry("Delete", "删除"),
			Map.entry("Built-in magic circles cannot be deleted", "内置魔法阵不可删除"),
			Map.entry("Magic Circle id already exists", "魔法阵 ID 已存在"),
			Map.entry("Magic Circle reset", "魔法阵已重置"),
			Map.entry("No snapshot to reset to", "没有可还原的快照"),
			Map.entry("Mode: Spell", "模式: 符卡"),
			Map.entry("Mode: Circle", "模式: 魔法阵"),
			Map.entry("Reset", "重置"),
			Map.entry("Certify & Export", "认证并导出"),
			Map.entry("Auto:ON", "自动:开"),
			Map.entry("Auto:OFF", "自动:关"),
			Map.entry("Help", "帮助"),
			Map.entry("\u25B6All", "\u25B6全部"),
			Map.entry("\u25BCAll", "\u25BC全部"),
			Map.entry("[+]:All", "[+]:全部"),
			Map.entry("[+]:Sel", "[+]:选中"),
			Map.entry("RstLayout", "重置布局"),
			Map.entry("Front (XY)", "正面 (XY)"),
			Map.entry("Side (ZY)", "侧面 (ZY)"),
			Map.entry("Top (XZ)", "顶面 (XZ)"),
			Map.entry("Actions", "动作"),
			Map.entry("Properties", "属性"),
			Map.entry("Controls", "控制"),
			Map.entry("Status", "状态"),
			Map.entry("Perf", "性能"),
			Map.entry("Viewport", "视口"),
			Map.entry("Magic Circle", "魔法阵"),
			Map.entry("Circle", "魔法阵"),
			Map.entry("No phase", "无阶段"),
			Map.entry("Add Action", "添加动作"),
			Map.entry("Select an action", "选择一个动作"),
			Map.entry("Click an action in", "在动作列表中"),
			Map.entry("the list below to", "点击一个动作"),
			Map.entry("edit its properties", "以编辑属性"),
			Map.entry("[Enable]", "[启用]"),
			Map.entry("[Disable]", "[禁用]"),
			Map.entry("[Delete]", "[删除]"),
			Map.entry("Read-only action", "只读动作"),
			Map.entry("Fire Danmaku", "发射弹幕"),
			Map.entry("Fire Laser", "发射激光"),
			Map.entry("Fire Text Danmaku", "发射文字弹幕"),
			Map.entry("Conditional", "条件"),
			Map.entry("Repeat", "重复"),
			Map.entry("Delay", "延迟"),
			Map.entry("Teleport", "传送"),
			Map.entry("Spawn Shooter", "生成发射器"),
			Map.entry("Burst", "爆发"),
			Map.entry("Set Variable", "设置变量"),
			Map.entry("Add Variable", "增加变量"),
			Map.entry("Sequence", "序列"),
			Map.entry("Clear Screen", "清屏"),
			Map.entry("Erase Enemy Danmaku", "擦除敌弹"),
			Map.entry("Play Sound", "播放声音"),
			Map.entry("Run Command", "运行命令"),
			Map.entry("Show Spell Title", "显示符卡标题"),
			Map.entry("Custom Magic Circle", "自定义魔法阵"),
			Map.entry("Force Phase", "强制阶段"),
			Map.entry("Force Spell", "强制符卡"),
			Map.entry("Fire Spell", "发射符卡"),
			Map.entry("Confine Target", "限制目标"),
			Map.entry("Set Entity Flag", "设置实体标志"),
			Map.entry("YSM Render", "YSM 渲染"),
			Map.entry("Teleport Random", "随机传送"),
			Map.entry("Caster Moves", "施法者移动"),
			Map.entry("Spell Health", "符卡血量"),
			Map.entry("Phase ID", "阶段 ID"),
			Map.entry("Spell ID", "符卡 ID"),
			Map.entry("Raw ID", "原始 ID"),
			Map.entry("Timeout Target", "超时切换"),
			Map.entry("Timeout Phase", "超时阶段"),
			Map.entry("Timeout Spell", "超时符卡"),
			Map.entry("Timeout Clear Screen", "超时清屏"),
			Map.entry("Break Target", "击破切换"),
			Map.entry("Break Phase", "击破阶段"),
			Map.entry("Break Spell", "击破符卡"),
			Map.entry("Break Clear Screen", "击破清屏"),
			Map.entry("Noop", "空操作"),
			Map.entry("Legacy Ticker", "旧版计时器"),
			Map.entry("\u26A0 DISABLED (press D to enable)", "\u26A0 已禁用 (按 D 启用)"),
			Map.entry("[Remove Tilt]", "[移除倾斜]"),
			Map.entry("[+ Tilt Angle]", "[+ 倾斜角]"),
			Map.entry("[Remove Group Rotation]", "[移除整体旋转]"),
			Map.entry("[+ Group Rotation]", "[+ 整体旋转]"),
			Map.entry("[- Remove Dmg Type]", "[- 移除伤害类型]"),
			Map.entry("[+ Damage Type]", "[+ 伤害类型]"),
			Map.entry("[+ Delayed Mover]", "[+ 延迟运动器]"),
			Map.entry("[- Remove Delayed]", "[- 移除延迟]"),
			Map.entry("[+ Add Condition]", "[+ 添加条件]"),
			Map.entry("[+] Add Segment", "[+] 添加段"),
			Map.entry("[-] Remove Last Segment", "[-] 移除最后段"),
			Map.entry("[+] Add Layer", "[+] 添加层"),
			Map.entry("[-] Remove Last Layer", "[-] 移除最后层"),
			Map.entry("[+] Add Bezier Segment", "[+] 添加贝塞尔段"),
			Map.entry("[+] Add Waypoint", "[+] 添加路径点"),
			Map.entry("[-] Remove Last Waypoint", "[-] 移除最后路径点"),
			Map.entry("Bullet", "弹幕"),
			Map.entry("Bullet Mode", "弹幕模式"),
			Map.entry("Bullet Index", "弹幕索引"),
			Map.entry("Bullet List", "弹幕列表"),
			Map.entry("Laser", "激光"),
			Map.entry("Color", "颜色"),
			Map.entry("Color Mode", "颜色模式"),
			Map.entry("Color Index", "颜色索引"),
			Map.entry("Color Var", "颜色变量"),
			Map.entry("Color Interval", "颜色间隔"),
			Map.entry("Color List", "颜色列表"),
			Map.entry("Color Anim", "颜色动画"),
			Map.entry("Hue Cycle", "色相循环"),
			Map.entry("Hue Period", "色相周期"),
			Map.entry("Hue Offset", "色相偏移"),
			Map.entry("Index Step", "索引步进"),
			Map.entry("Saturation", "饱和度"),
			Map.entry("Brightness", "亮度"),
			Map.entry("Count", "数量"),
			Map.entry("Speed", "速度"),
			Map.entry("Lifetime", "生命周期"),
			Map.entry("Health", "生命"),
			Map.entry("Damage", "伤害"),
			Map.entry("Size", "大小"),
			Map.entry("Pattern", "模式"),
			Map.entry("Angle", "角度"),
			Map.entry("Spread", "扩散"),
			Map.entry("Elevation", "仰角"),
			Map.entry("Cols", "列数"),
			Map.entry("Outer Cnt", "外层数量"),
			Map.entry("Aim Mode", "瞄准模式"),
			Map.entry("Axis Tilt", "轴倾斜"),
			Map.entry("Tilt Angle", "倾斜角"),
			Map.entry("Group Rotation (post-origin/tilt)", "整体旋转 (原点/倾斜后)"),
			Map.entry("Rot X", "X 旋转"),
			Map.entry("Rot Y", "Y 旋转"),
			Map.entry("Rot Z", "Z 旋转"),
			Map.entry("Origin", "原点"),
			Map.entry("Mover", "运动器"),
			Map.entry("Advanced", "高级"),
			Map.entry("Trail Intv", "拖尾间隔"),
			Map.entry("Hit Entity", "命中实体"),
			Map.entry("Hit Block", "命中方块"),
			Map.entry("Dmg Type", "伤害类型"),
			Map.entry("Length", "长度"),
			Map.entry("Thickness", "粗细"),
			Map.entry("Prepare", "准备"),
			Map.entry("Start", "开始"),
			Map.entry("End", "结束"),
			Map.entry("Delayed V0", "延迟 V0"),
			Map.entry("Delayed V1", "延迟 V1"),
			Map.entry("Model ID", "模型 ID"),
			Map.entry("Anim Hint", "动画提示"),
			Map.entry("Text", "文本"),
			Map.entry("Text Color", "文本颜色"),
			Map.entry("Per Char", "逐字"),
			Map.entry("Roll", "滚转"),
			Map.entry("Condition", "条件"),
			Map.entry("Cond 1", "条件 1"),
			Map.entry("Cond 2", "条件 2"),
			Map.entry("Interval", "间隔"),
			Map.entry("Offset", "偏移"),
			Map.entry("Threshold", "阈值"),
			Map.entry("Ticks", "刻数"),
			Map.entry("Distance", "距离"),
			Map.entry("Probability", "概率"),
			Map.entry("Value", "值"),
			Map.entry("Period", "周期"),
			Map.entry("Trait", "特性"),
			Map.entry("Flag", "标志"),
			Map.entry("Left", "左值"),
			Map.entry("Op", "运算符"),
			Map.entry("Right", "右值"),
			Map.entry("Key", "键"),
			Map.entry("Difficulty", "难度"),
			Map.entry("Min Diff", "最低难度"),
			Map.entry("Inner", "内部"),
			Map.entry("Mode", "模式"),
			Map.entry("Hit Context", "命中上下文"),
			Map.entry("Command", "命令"),
			Map.entry("Set / switch", "设置/切换"),
			Map.entry("Clear overrides", "清除覆盖"),
			Map.entry("Clear Fields", "清除字段"),
			Map.entry("Expire Fields", "到期清除字段"),
			Map.entry("Model", "模型"),
			Map.entry("Texture", "贴图"),
			Map.entry("Animation", "动画"),
			Map.entry("Duration", "持续时间"),
			Map.entry("Clear", "清除"),
			Map.entry("Radius", "半径"),
			Map.entry("Stroke", "笔画"),
			Map.entry("Vertex", "顶点"),
			Map.entry("Rune", "符文"),
			Map.entry("Item", "物品"),
			Map.entry("Item ID", "物品 ID"),
			Map.entry("Layer", "层"),
			Map.entry("Child", "子组件"),
			Map.entry("Children", "子组件"),
			Map.entry("Scale", "缩放"),
			Map.entry("Rot", "旋转"),
			Map.entry("Rot Speed", "旋转速度"),
			Map.entry("Z", "Z"),
			Map.entry("Alpha", "透明度"),
			Map.entry("Preview", "预览"),
			Map.entry("Entity Target", "实体目标"),
			Map.entry("Block Target", "方块目标"),
			Map.entry("State", "状态"),
			Map.entry("+Stroke", "+笔画"),
			Map.entry("-Stroke", "-笔画"),
			Map.entry("+Item", "+物品"),
			Map.entry("-Item", "-物品"),
			Map.entry("+Layer", "+层"),
			Map.entry("-Layer", "-层"),
			Map.entry("+Child", "+子组件"),
			Map.entry("Open Child", "打开子组件"),
			Map.entry("+Text", "+文字"),
			Map.entry("-Text", "-文字"),
			Map.entry("Fire", "发射"),
			Map.entry("Flow", "流程控制"),
			Map.entry("Variables", "变量"),
			Map.entry("Field", "清场"),
			Map.entry("Presentation", "表现"),
			Map.entry("Spell Flow", "符卡流程"),
			Map.entry("Movement", "移动"),
			Map.entry("Privileged", "特权"),
			Map.entry("Salvaged broken nodes", "已抢救损坏节点"),
			Map.entry("Fix broken nodes first", "请先修复损坏节点"),
			Map.entry("⚠ BROKEN NODE (not executed, blocks certify/export)",
					"⚠ 损坏节点（不会执行，且阻止认证/导出）"),
			Map.entry("[Replace with another type]", "[替换为其他类型]"),
			Map.entry("Error", "错误"),
			Map.entry("Raw", "原文"),
			Map.entry("Strokes", "笔画"),
			Map.entry("Items", "物品"),
			Map.entry("Texts", "文字"),
			Map.entry("Layers", "层"),
			Map.entry("Width", "宽度"),
			Map.entry("ID", "ID"),
			Map.entry("Spacing", "字距"),
			Map.entry("Arc Radius", "环绕半径"),
			Map.entry("Arc Span", "环绕跨度"),
			Map.entry("Read: Inward", "朝向: 向内"),
			Map.entry("Read: Outward", "朝向: 向外"),
			Map.entry("No strokes", "无笔画"),
			Map.entry("No items", "无物品"),
			Map.entry("No texts", "无文字"),
			Map.entry("No layers", "无层"),
			Map.entry("Text node added", "已添加文字"),
			Map.entry("Text node removed", "已移除文字"),
			Map.entry("Text changed", "文字已变更"),
			Map.entry("Text color changed", "文字颜色已变更"),
			Map.entry("Magic Circle ready", "魔法阵就绪"),
			Map.entry("Magic Circle loaded", "魔法阵已载入"),
			Map.entry("Magic Circle created", "魔法阵已创建"),
			Map.entry("Magic Circle JSON ready", "魔法阵 JSON 就绪"),
			Map.entry("Magic Circle JSON applied", "魔法阵 JSON 已应用"),
			Map.entry("Magic Circle save sent", "魔法阵保存已发送"),
			Map.entry("Magic Circle export sent", "魔法阵导出已发送"),
			Map.entry("Invalid magic circle JSON", "魔法阵 JSON 无效"),
			Map.entry("Invalid magic circle id", "魔法阵 ID 无效"),
			Map.entry("Invalid color", "颜色无效"),
			Map.entry("Stroke added", "已添加笔画"),
			Map.entry("Stroke removed", "已移除笔画"),
			Map.entry("Stroke color changed", "笔画颜色已变更"),
			Map.entry("Stroke changed", "笔画已变更"),
			Map.entry("Item node added", "已添加物品节点"),
			Map.entry("Item node removed", "已移除物品节点"),
			Map.entry("Item changed", "物品节点已变更"),
			Map.entry("Item selected", "已选择物品层"),
			Map.entry("Item moved", "物品层已移动"),
			Map.entry("Item rotated", "物品层已旋转"),
			Map.entry("Layer added", "已添加层"),
			Map.entry("Layer removed", "已移除层"),
			Map.entry("Layer changed", "层已变更"),
			Map.entry("Layer children changed", "子组件已变更"),
			Map.entry("Child component added", "已添加子组件"),
			Map.entry("Child component loaded", "已打开子组件"),
			Map.entry("No child component", "没有子组件"),
			Map.entry("Invalid child id", "子组件 ID 无效"),
			Map.entry("Preview size changed", "预览大小已变更"),
			Map.entry("ON", "开"),
			Map.entry("OFF", "关"),
			Map.entry("Y", "Y"),
			Map.entry("N", "否"),
			Map.entry("N/A", "不可用"),
			Map.entry("true", "真"),
			Map.entry("false", "假"),
			Map.entry("Constant", "固定"),
			Map.entry("Indexed", "索引"),
			Map.entry("Variable", "变量"),
			Map.entry("Cycle", "循环"),
			Map.entry("Random", "随机"),
			Map.entry("Raw JSON", "原始 JSON"),
			Map.entry("No action selected", "未选择动作"),
			Map.entry("Raw JSON ready", "原始 JSON 就绪"),
			Map.entry("Raw JSON applied", "原始 JSON 已应用"),
			Map.entry("Invalid JSON", "JSON 无效"),
			Map.entry("Invalid action JSON", "动作 JSON 无效"),
			Map.entry("Invalid spell JSON", "符卡 JSON 无效"),
			Map.entry("Raw JSON has unsupported field", "原始 JSON 存在不支持字段"),
			Map.entry("Raw JSON draft restored", "原始 JSON 草稿已恢复"),
			Map.entry("Unable to load Raw JSON draft", "无法读取原始 JSON 草稿"),
			Map.entry("Unable to save Raw JSON draft", "无法保存原始 JSON 草稿"),
			Map.entry("Unable to encode action JSON", "无法编码动作 JSON"),
			Map.entry("Unable to encode spell JSON", "无法编码符卡 JSON"),
			Map.entry("onEnter", "进入时"),
			Map.entry("onTick", "每刻"),
			Map.entry("onExit", "退出时"),
			Map.entry("onDamage", "受伤时"),
			Map.entry("if_true", "为真"),
			Map.entry("if_false", "为假"),
			Map.entry("body", "主体"),
			Map.entry("onExpiry", "到期时"),
			Map.entry("onTrail", "拖尾时"),
			Map.entry("onHitEntity", "命中实体时"),
			Map.entry("onHitBlock", "命中方块时"),
			Map.entry("actions", "动作"),
			Map.entry("target", "目标"),
			Map.entry("direction_to_target", "朝向目标"),
			Map.entry("fixed", "固定方向"),
			Map.entry("caster_facing", "施法者朝向"),
			Map.entry("angle_offset", "角度偏移"),
			Map.entry("variable_angle", "变量角度"),
			Map.entry("random_angle", "随机角度"),
			Map.entry("tick_interval", "tick 间隔"),
			Map.entry("health_below", "生命低于"),
			Map.entry("health_above", "生命高于"),
			Map.entry("tick_elapsed", "tick 已过"),
			Map.entry("distance_above", "距离高于"),
			Map.entry("distance_below", "距离低于"),
			Map.entry("hit_count", "命中次数"),
			Map.entry("target_on_ground", "目标在地面"),
			Map.entry("target_speed", "目标速度"),
			Map.entry("random_chance", "随机概率"),
			Map.entry("target_health_below", "目标生命低于"),
			Map.entry("target_health_above", "目标生命高于"),
			Map.entry("target_is_flying", "目标飞行"),
			Map.entry("target_is_fallflying", "目标鞘翅飞行"),
			Map.entry("dynamic_tick_interval", "动态 tick 间隔"),
			Map.entry("entity_trait", "实体特性"),
			Map.entry("entity_flag", "实体标志"),
			Map.entry("compare", "数值比较"),
			Map.entry("variable_check", "变量检查(旧)"),
			Map.entry("difficulty_equals", "难度等于"),
			Map.entry("difficulty_above", "难度高于"),
			Map.entry("always", "始终"),
			Map.entry("not", "非"),
			Map.entry("and", "与"),
			Map.entry("or", "或"),
			Map.entry("PEACEFUL", "和平"),
			Map.entry("EASY", "简单"),
			Map.entry("NORMAL", "普通"),
			Map.entry("HARD", "困难"),
			Map.entry("none", "无"),
			Map.entry("phase", "阶段"),
			Map.entry("spell", "符卡"),
			Map.entry("set", "设置"),
			Map.entry("clear", "清除"),
			Map.entry("acceleration", "加速度"),
			Map.entry("rotate", "旋转"),
			Map.entry("polar", "极坐标"),
			Map.entry("zero", "静止"),
			Map.entry("bezier", "贝塞尔"),
			Map.entry("multi_bezier", "多段贝塞尔"),
			Map.entry("spline", "样条"),
			Map.entry("formula", "公式"),
			Map.entry("orbital", "轨道"),
			Map.entry("translate", "平移"),
			Map.entry("homing", "追踪"),
			Map.entry("attached", "附着"),
			Map.entry("attached_free_rot", "附着自由旋转"),
			Map.entry("caster_to_target", "施法者到目标"),
			Map.entry("forward", "发射方向"),
			Map.entry("velocity", "当前速度"),
			Map.entry("as caster", "以施法者"),
			Map.entry("console", "控制台"),
			Map.entry("non cheat", "非作弊权限"),
			Map.entry("default", "默认"),
			Map.entry("as hit entity", "以命中实体"),
			Map.entry("at entity pos", "位于实体坐标"),
			Map.entry("at block pos", "位于方块命中坐标"),
			Map.entry("white", "白色"),
			Map.entry("orange", "橙色"),
			Map.entry("magenta", "品红"),
			Map.entry("light blue", "淡蓝"),
			Map.entry("yellow", "黄色"),
			Map.entry("lime", "黄绿"),
			Map.entry("pink", "粉色"),
			Map.entry("gray", "灰色"),
			Map.entry("light gray", "淡灰"),
			Map.entry("cyan", "青色"),
			Map.entry("purple", "紫色"),
			Map.entry("blue", "蓝色"),
			Map.entry("brown", "棕色"),
			Map.entry("green", "绿色"),
			Map.entry("red", "红色"),
			Map.entry("black", "黑色"),
			Map.entry("dynamic", "动态"),
			Map.entry("ring", "环形"),
			Map.entry("aimed", "瞄准"),
			Map.entry("random", "随机"),
			Map.entry("grid", "网格"),
			Map.entry("nested ring", "嵌套环"),
			Map.entry("caster", "施法者"),
			Map.entry("target_pos", "目标位置"),
			Map.entry("absolute", "绝对位置"),
			Map.entry("relative", "相对位移"),
			Map.entry("self", "自身"),
			Map.entry("pass", "穿透"),
			Map.entry("bounce", "反弹"),
			Map.entry("vanish", "消失"),
			Map.entry("standard", "标准"),
			Map.entry("abyssal", "深渊"),
			Map.entry("Hit Control", "命中控制"),
			Map.entry("Space", "坐标系"),
			Map.entry("World", "世界"),
			Map.entry("Local", "局部"),
			Map.entry("Limit Vx", "限制 Vx"),
			Map.entry("Limit Vy", "限制 Vy"),
			Map.entry("Limit Vz", "限制 Vz"),
			Map.entry("Limit Forward", "限制前进速度"),
			Map.entry("Limit Right", "限制横向速度"),
			Map.entry("Limit Up", "限制垂直速度"),
			Map.entry("Term Vx", "终端 Vx"),
			Map.entry("Term Vy", "终端 Vy"),
			Map.entry("Term Vz", "终端 Vz"),
			Map.entry("Term Forward", "终端前进速度"),
			Map.entry("Term Right", "终端横向速度"),
			Map.entry("Term Up", "终端垂直速度"),
			Map.entry("Acc Forward", "前进加速度"),
			Map.entry("Acc Right", "横向加速度"),
			Map.entry("Acc Up", "垂直加速度"),
			Map.entry("Bounce Source", "反弹 (Bounce)"),
			Map.entry("Continue Source", "穿透 (Continue)"),
			Map.entry("Expire Source", "结束弹幕寿命 (触发 onExpiry)"),
			Map.entry("Discard Source", "抹除弹幕 (不触发后续)"),
			Map.entry("Hold Source", "命中后冻结 (Hold Source)"),
			Map.entry("bounce_source", "反弹 (Bounce)"),
			Map.entry("continue_source", "穿透 (Continue)"),
			Map.entry("expire_source", "结束弹幕寿命 (触发 onExpiry)"),
			Map.entry("discard_source", "抹除弹幕 (不触发后续)"),
			Map.entry("hold_source", "命中后冻结 (Hold Source)"),
			Map.entry("onRelease", "释放时动作"),
			Map.entry("Delay Ticks", "延迟刻数"),
			Map.entry("Preset", "预设模式"),
			Map.entry("Specular Reflect", "镜面反射"),
			Map.entry("Bouncy Dampened", "弹性衰减"),
			Map.entry("Surface Slide", "沿面偏转"),
			Map.entry("Custom", "自定义"),
			Map.entry("Normal Factor", "法向反弹系数 [-5, 0]"),
			Map.entry("Tangent Factor", "切向保留系数 [-5, 5]"),
			Map.entry("Tangent Offset X (World)", "切向偏置 X (世界坐标)"),
			Map.entry("Tangent Offset Y (World)", "切向偏置 Y (世界坐标)"),
			Map.entry("Tangent Offset Z (World)", "切向偏置 Z (世界坐标)"),
			Map.entry("Reset Speed", "重设速度大小"),
			Map.entry("New Speed", "重设速度值"),
			Map.entry("Max Bounces", "最大反弹次数"),
			Map.entry("Retarget", "反弹重瞄"),
			Map.entry("Safety Limit", "安全上限"),
			Map.entry("Caster HP", "施法者 HP"),
			Map.entry("Viewport Range", "视口范围"),
			Map.entry("Markers", "标记"),
			Map.entry("Target State", "目标状态"),
			Map.entry("Target HP", "目标 HP"),
			Map.entry("Target Height", "目标高度"),
			Map.entry("Focus", "聚焦"),
			Map.entry("Target", "目标"),
			Map.entry("Caster", "施法者"),
			Map.entry("Reset Position", "重置位置"),
			Map.entry("Target Position", "目标位置"),
			Map.entry("Block Target Position", "方块目标位置"),
			Map.entry("Caster Position", "施法者位置"),
			Map.entry("Range", "范围"),
			Map.entry("Limit", "上限"),
			Map.entry("Dist", "距离"),
			Map.entry("HP", "HP"),
			Map.entry("THP", "目标HP"),
			Map.entry("Height", "高度"),
			Map.entry("Label:", "标签:"),
			Map.entry("Spell:", "符卡:"),
			Map.entry("Display Name", "显示名"),
			Map.entry("New Spell ID", "新符卡 ID"),
			Map.entry("Cancel", "取消"),
			Map.entry("Select an existing spell or enter a new spell id and press Enter.", "选择已有符卡，或输入新的符卡 ID 后按 Enter。"),
			Map.entry("SELECTED — switch to orthographic to edit", "已选中 - 切换到正交模式编辑"),
			Map.entry("Perspective  LMB look · RMB orbit · MMB pan · wheel speed", "透视  左键视角 · 右键环绕 · 中键平移 · 滚轮调速"),
			Map.entry("ROTATE X  LMB drag: rotate", "旋转 X  左键拖拽: 旋转"),
			Map.entry("ROTATE Y  LMB drag: rotate", "旋转 Y  左键拖拽: 旋转"),
			Map.entry("ROTATE Z  LMB drag: rotate", "旋转 Z  左键拖拽: 旋转"),
			Map.entry("X/Y/Z axis · Esc/R exit · RMB orbit", "X/Y/Z 轴 · Esc/R 退出 · 右键环绕"),
			Map.entry("SELECTED  LMB drag bullets: move origin · LMB empty: deselect", "已选中  左键拖弹幕: 移动原点 · 左键空白: 取消选择"),
			Map.entry("R rotate · RMB orbit · MMB pan · wheel zoom", "R 旋转 · 右键环绕 · 中键平移 · 滚轮缩放"),
			Map.entry("LMB select bullet · drag caster/entity/block target", "左键选择弹幕 · 拖拽施法者/实体目标/方块目标"),
			Map.entry("RMB orbit · MMB pan · wheel zoom", "右键环绕 · 中键平移 · 滚轮缩放"),
			Map.entry("Magic Circle  LMB drag item: move / RMB drag item: rotate", "魔法阵  左键拖物品: 移动 / 右键拖物品: 旋转"),
			Map.entry("MMB pan / RMB empty orbit / wheel zoom", "中键平移 / 右键空白环绕 / 滚轮缩放"),
			Map.entry("Item layer - LMB move / RMB rotate", "物品层 - 左键移动 / 右键旋转"),
			Map.entry("Item layer - click to select", "物品层 - 点击选择"),
			Map.entry("Caster — drag to move", "施法者 - 拖拽移动"),
			Map.entry("Entity Target — drag to move", "实体目标 - 拖拽移动"),
			Map.entry("Block Target — drag to move", "方块目标 - 拖拽移动"),
			Map.entry("Danmaku — drag to move origin", "弹幕 - 拖拽移动原点"),
			Map.entry("Danmaku — click to select", "弹幕 - 点击选择"),
			Map.entry("Moving item layer", "正在移动物品层"),
			Map.entry("Rotating item layer", "正在旋转物品层"),
			Map.entry("Moving Caster", "正在移动施法者"),
			Map.entry("Moving Target", "正在移动目标"),
			Map.entry("Moving Block Target", "正在移动方块目标"),
			Map.entry("Moving origin", "正在移动原点"),
			Map.entry("Orbit view", "环绕视图"),
			Map.entry("Pan view", "平移视图"),
			Map.entry("\u2588 tick", "\u2588 tick"),
			Map.entry("\u2588 render interval", "\u2588 渲染间隔")
	);
}
