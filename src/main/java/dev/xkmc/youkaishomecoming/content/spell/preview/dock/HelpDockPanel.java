package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 可停靠的帮助面板。从 SpellPreviewScreen 提取帮助内容渲染和滚动逻辑。
 * 默认不显示在布局中，通过 Help 按钮打开并添加到 Tab。
 */
@OnlyIn(Dist.CLIENT)
public class HelpDockPanel implements DockPanel {

	private static final String[] HELP_LINES = {
			"\u00A7e\u00A7l--- 快捷键 ---",
			"",
			"\u00A7fSpace       \u00A77播放/暂停",
			"\u00A7fR           \u00A77重置到 tick 0",
			"\u00A7fRight       \u00A77单步推进 1 tick",
			"\u00A7fDel/Bksp    \u00A77删除选中节点",
			"",
			"\u00A7fCtrl+Z      \u00A77撤销",
			"\u00A7fCtrl+Y      \u00A77重做",
			"\u00A7fCtrl+C      \u00A77复制节点",
			"\u00A7fCtrl+X      \u00A77剪切节点",
			"\u00A7fCtrl+V      \u00A77粘贴节点",
			"\u00A7fCtrl+Up     \u00A77上移节点",
			"\u00A7fCtrl+Down   \u00A77下移节点",
			"\u00A7fCtrl+D      \u00A77启用/禁用节点",
			"\u00A7fCtrl+E      \u00A77折叠/展开选中子树",
			"\u00A7fCtrl+Sh+E   \u00A77全部折叠/全部展开",
			"\u00A7fCtrl+N      \u00A77切换自定义节点名显示",
			"\u00A7fCtrl+B      \u00A77切换 +按钮 全部显示/仅选中显示",
			"",
			"\u00A7e\u00A7l--- 鼠标操作 ---",
			"",
			"\u00A76节点树:",
			"\u00A7f  单击节点    \u00A77选中并在右侧编辑（显示该节点的 + 按钮）",
			"\u00A7f  双击节点    \u00A77重命名 (Enter 确认, Esc 取消)",
			"\u00A7f  点击 \u25BC/\u25B6   \u00A77折叠/展开子树",
			"\u00A7f  拖拽节点    \u00A77拖放重排序或移入分支",
			"\u00A7f  点击 [+]   \u00A77添加新节点到段落/分支",
			"",
			"\u00A76属性面板:",
			"\u00A7f  [Disable]  \u00A77禁用节点 (运行时跳过)",
			"\u00A7f  [Delete]   \u00A77删除节点",
			"\u00A7f  Ctrl+点击 \u00A7b$var\u00A7f  \u00A77跳转到变量定义节点",
			"\u00A7f  Tab        \u00A77表达式自动补全",
			"\u00A7f  滚轮       \u00A77滚动属性列表（右侧有滚动条）",
			"",
			"\u00A763D 视口 (正交模式):",
			"\u00A7f  左键拖拽    \u00A77移动目标位置",
			"\u00A7f  中键拖拽    \u00A77平移摄像机",
			"\u00A7f  右键拖拽    \u00A77旋转摄像机",
			"\u00A7f  滚轮        \u00A77缩放",
			"",
			"\u00A763D 视口 (透视模式):",
			"\u00A7f  左键单击    \u00A77进入自由视角（隐藏鼠标）",
			"\u00A7f  WASD/空格/Shift  \u00A77移动摄像机（自由视角中）",
			"\u00A7f  鼠标移动    \u00A77旋转视角（自由视角中）",
			"\u00A7f  滚轮        \u00A77调节飞行速度",
			"\u00A7f  右键拖拽    \u00A77轴心旋转（环绕前方中心）",
			"\u00A7f  中键拖拽    \u00A77视角平面平移",
			"\u00A7f  Esc         \u00A77退出自由视角 / 退出透视模式",
			"",
			"\u00A7e\u00A7l--- 工具栏按钮 ---",
			"",
			"\u00A7fTop/Front/Side  \u00A77切换正交预设角度",
			"\u00A7fPersp/Ortho     \u00A77切换透视/正交模式",
			"\u00A7fBindTgt/Unbind  \u00A77绑定/解绑目标跟随摄像机（透视）",
			"\u00A7f\u25B6All / \u25BCAll     \u00A77全部折叠 / 全部展开节点树",
			"\u00A7f[+]:Sel/All     \u00A77+ 按钮 仅选中显示 / 全部显示",
			"\u00A7fApply           \u00A77应用并保存符卡到所有使用它的实体",
			"\u00A7fExport          \u00A77导出 JSON 到 youkaishomecoming_exports/",
			"\u00A7fReset           \u00A77恢复到内建默认值",
			"\u00A7fAuto:ON/OFF     \u00A77编辑后自动回放预览",
			"",
			"\u00A7e\u00A7l--- Mover 类型 ---",
			"",
			"\u00A7fnone          \u00A77默认直线飞行",
			"\u00A7facceleration  \u00A77恒定加速度",
			"\u00A7frotate        \u00A77旋转",
			"\u00A7fpolar         \u00A77极坐标运动",
			"\u00A7fzero          \u00A77静止不动",
			"\u00A7fbezier        \u00A77三次贝塞尔曲线路径",
			"",
			"\u00A7e\u00A7l--- 表达式语法 ---",
			"",
			"\u00A77运算符: \u00A7f+ - * / %  \u00A77括号: \u00A7f( )",
			"\u00A77变量: \u00A7b$wave  $i  $ver",
			"\u00A77函数: \u00A7erand\u00A7f(min,max)  \u00A7esqrt\u00A7f(x)",
			"\u00A77       \u00A7esin\u00A7f(x,amp?,phase?)  \u00A7ecos\u00A7f(...)",
			"\u00A77       \u00A7elerp\u00A7f(start,end,dur)",
			"\u00A77       \u00A7ehp\u00A7f(full,empty)  \u00A7etick_mod\u00A7f(n)",
			"\u00A77关键字: \u00A7etick  total_tick  distance",
			"",
			"\u00A7e\u00A7l--- 语法高亮 ---",
			"",
			"\u00A7b$variable      \u00A77浅蓝色",
			"\u00A7erand() sqrt()  \u00A77函数 = 黄色",
			"\u00A7etick distance  \u00A77关键字 = 黄色",
			"\u00A7e(  \u00A7c(  \u00A7a(  \u00A79(  \u00A77括号 = 彩虹(仅合法时)",
	};

	private int x, y, w, h;
	private int scrollOffset = 0;

	@Override
	public String dockTitle() {
		return "Help";
	}

	@Override
	public String dockId() {
		return "help";
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	@Override
	public int getX() { return x; }

	@Override
	public int getY() { return y; }

	@Override
	public int getWidth() { return w; }

	@Override
	public int getHeight() { return h; }

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;

		// 背景
		graphics.fill(x, y, x + w, y + h, 0xEE111122);

		// 边框
		graphics.fill(x, y, x + w, y + 1, 0xFF444488);
		graphics.fill(x, y + h - 1, x + w, y + h, 0xFF444488);
		graphics.fill(x, y, x + 1, y + h, 0xFF444488);
		graphics.fill(x + w - 1, y, x + w, y + h, 0xFF444488);

		// 标题
		String title = "Spell Editor Help";
		graphics.drawString(font, title, x + (w - font.width(title)) / 2, y + 4, 0xFFFFFF88, false);

		// 可滚动内容
		int contentY = y + 18;
		int contentH = h - 22;
		graphics.enableScissor(x + 4, contentY, x + w - 8, contentY + contentH);

		int lineH = 10;
		int maxScroll = Math.max(0, HELP_LINES.length * lineH - contentH);
		scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

		for (int i = 0; i < HELP_LINES.length; i++) {
			int ly = contentY + i * lineH - scrollOffset;
			if (ly + lineH < contentY || ly > contentY + contentH) continue;
			graphics.drawString(font, HELP_LINES[i], x + 8, ly, 0xFFCCCCCC, false);
		}
		graphics.disableScissor();

		// 滚动条
		if (maxScroll > 0) {
			int sbX = x + w - 6;
			int trackH = contentH - 2;
			int thumbH = Math.max(10, trackH * contentH / (HELP_LINES.length * lineH));
			int thumbY = contentY + 1 + (trackH - thumbH) * scrollOffset / maxScroll;
			graphics.fill(sbX, contentY, sbX + 4, contentY + contentH, 0x33FFFFFF);
			graphics.fill(sbX + 1, thumbY, sbX + 3, thumbY + thumbH, 0x88AAAACC);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!isMouseOver(mouseX, mouseY)) return false;
		scrollOffset -= (int) (delta * 30);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Escape 关闭帮助 — 由 Screen 层面处理（从 Tab 中移除）
		return false;
	}
}
